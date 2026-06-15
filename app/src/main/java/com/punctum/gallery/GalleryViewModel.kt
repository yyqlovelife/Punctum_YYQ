package com.punctum.gallery

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.punctum.gallery.data.GalleryStore
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.InvitationCardStyle
import com.punctum.gallery.model.Photo
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GalleryStore(app)
    private val photoCache = mutableMapOf<String, List<Photo>>()
    private val thumbnailWarmJobs = mutableMapOf<String, Job>()
    private var overviewRefreshJob: Job? = null
    private var dataRefreshJob: Job? = null
    private var foregroundSyncJob: Job? = null
    private var pendingDeletePhoto: Photo? = null
    private val deleteTombstones = mutableMapOf<String, Long>()

    var galleries by mutableStateOf<List<Gallery>>(emptyList())
        private set
    var currentUri by mutableStateOf<String?>(null)
        private set
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var loadingPhotos by mutableStateOf(false)
        private set
    var overviews by mutableStateOf<Map<String, GalleryOverview>>(emptyMap())
        private set
    var invitationStyle by mutableStateOf(InvitationCardStyle.POSTCARD)
        private set
    var homeSubtitle by mutableStateOf(HOME_SUBTITLES.first())
        private set
    var pendingDeleteConfirmation by mutableStateOf<PendingIntent?>(null)
        private set

    // 覆层状态
    var showSwitcher by mutableStateOf(false)
        private set
    var selectedIndex by mutableStateOf<Int?>(null)
        private set

    val currentGallery: Gallery?
        get() = galleries.firstOrNull { it.uri.toString() == currentUri }

    /** 冷启动：恢复仍有效授权的画廊，并默认停留在邀请卡首页。 */
    fun start() {
        if (galleries.isNotEmpty()) return
        invitationStyle = InvitationCardStyle.from(store.invitationStyleId)
        homeSubtitle = HOME_SUBTITLES.random(Random(System.currentTimeMillis()))
        val permitted = getApplication<Application>().contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        val stored = store.loadGalleries().filter { permitted.contains(it.uri.toString()) }
        galleries = stored
        if (stored.isNotEmpty()) {
            store.saveGalleries(stored)
            overviews = store.loadOverviewCache(stored)
            refreshOverviews(force = true)
        }
    }

    fun addGallery(uri: Uri) {
        val ctx = getApplication<Application>()
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val key = uri.toString()
        if (galleries.none { it.uri.toString() == key }) {
            val name = PhotoRepository.displayName(ctx, uri) ?: "未命名画廊"
            galleries = galleries + Gallery(uri, name)
            store.saveGalleries(galleries)
        }
        showSwitcher = false
        selectGallery(key)
    }

    fun selectGallery(uriKey: String) {
        currentUri = uriKey
        store.lastGalleryUri = uriKey
        showSwitcher = false
        selectedIndex = null
        loadPhotos(uriKey)
    }

    fun refreshCurrentData() {
        dataRefreshJob?.cancel()
        dataRefreshJob = viewModelScope.launch {
            delay(250)
            refreshOverviews(force = true)
            currentUri?.let { uriKey ->
                refreshPhotos(uriKey, replaceVisible = false, cached = photoCache[uriKey].orEmpty())
            }
        }
    }

    fun startForegroundSync() {
        foregroundSyncJob?.cancel()
        refreshCurrentData()
        foregroundSyncJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                refreshCurrentData()
            }
        }
    }

    fun stopForegroundSync() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
    }

    fun renameGallery(uriKey: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        galleries = galleries.map {
            if (it.uri.toString() == uriKey) it.copy(displayName = trimmed) else it
        }
        store.saveGalleries(galleries)
        overviews[uriKey]?.let { ov ->
            val updated = ov.copy(gallery = ov.gallery.copy(displayName = trimmed))
            overviews = overviews + (uriKey to updated)
            store.saveOverview(updated)
        }
    }

    fun moveGallery(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in galleries.indices || toIndex !in galleries.indices || fromIndex == toIndex) return
        val mutable = galleries.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        galleries = mutable
        store.saveGalleries(galleries)
    }

    fun removeGallery(index: Int) {
        val target = galleries.getOrNull(index) ?: return
        val key = target.uri.toString()
        galleries = galleries.filterIndexed { i, _ -> i != index }
        overviews = overviews - key
        photoCache.remove(key)
        store.removePhotoCache(key)
        if (currentUri == key) {
            currentUri = null
            selectedIndex = null
            photos = emptyList()
            loadingPhotos = false
        }
        store.saveGalleries(galleries)
        store.removeOverview(key)
    }

    fun toggleInvitationStyle() {
        invitationStyle = invitationStyle.next()
        store.invitationStyleId = invitationStyle.id
    }

    private fun loadPhotos(uriKey: String) {
        photoCache[uriKey]?.let { cached ->
            photos = cached
            cacheOverview(uriKey, cached)
            loadingPhotos = false
            return
        }
        val persisted = store.loadPhotoCache(uriKey)
        if (persisted.isNotEmpty()) {
            photoCache[uriKey] = persisted
            photos = persisted
            cacheOverview(uriKey, persisted)
            loadingPhotos = false
            refreshPhotos(uriKey, replaceVisible = false, cached = persisted)
            return
        }
        loadingPhotos = true
        photos = emptyList()
        refreshPhotos(uriKey, replaceVisible = true, cached = emptyList())
    }

    private fun refreshPhotos(uriKey: String, replaceVisible: Boolean, cached: List<Photo> = photoCache[uriKey].orEmpty()) {
        viewModelScope.launch {
            val gallery = galleries.firstOrNull { it.uri.toString() == uriKey } ?: return@launch
            val list = applyDeleteTombstones(
                PhotoRepository.loadPhotos(getApplication(), gallery.uri, cached)
            )
            photoCache[uriKey] = list
            store.savePhotoCache(uriKey, list)
            cacheOverview(uriKey, list)
            if (currentUri == uriKey) {
                if (replaceVisible || photos != list) photos = list
                loadingPhotos = false
            }
        }
    }

    private fun cacheOverview(uriKey: String, list: List<Photo>) {
        val gallery = galleries.firstOrNull { it.uri.toString() == uriKey } ?: return
        val immediate = PhotoRepository.overviewFromPhotos(gallery, list).let { base ->
            overviews[uriKey]?.let { cached ->
                base.copy(
                    postcardCoverPath = cached.postcardCoverPath,
                    ticketCoverPath = cached.ticketCoverPath,
                )
            } ?: base
        }
        updateOverviewIfChanged(uriKey, immediate)
        val existing = overviews[uriKey]
        if (existing?.coverUris == immediate.coverUris &&
            !existing.postcardCoverPath.isNullOrBlank() &&
            !existing.ticketCoverPath.isNullOrBlank()
        ) {
            return
        }
        viewModelScope.launch {
            val overview = PhotoRepository.buildOverviewFromPhotos(getApplication(), gallery, list)
            updateOverviewIfChanged(uriKey, overview)
        }
    }

    fun openSwitcher() {
        goHome()
    }

    fun goHome() {
        currentUri = null
        selectedIndex = null
        showSwitcher = false
        refreshOverviews()
    }

    private fun refreshOverviews(force: Boolean = false) {
        // 先用占位卡（含名称）立即展示，再异步补全概览。
        val seeded = galleries.associate { g ->
            val key = g.uri.toString()
            key to (overviews[key] ?: GalleryOverview(g, loading = true))
        }
        overviews = seeded
        overviewRefreshJob?.cancel()
        overviewRefreshJob = viewModelScope.launch {
            galleries.forEach { g ->
                val key = g.uri.toString()
                if (!force && overviews[key]?.loading == false) return@forEach
                val cached = photoCache[key]
                val ov = if (cached != null) PhotoRepository.buildOverviewFromPhotos(getApplication(), g, cached)
                else PhotoRepository.loadOverview(getApplication(), g)
                updateOverviewIfChanged(key, ov)
            }
        }
    }

    private fun updateOverviewIfChanged(uriKey: String, overview: GalleryOverview) {
        if (overviews[uriKey] == overview) return
        overviews = overviews + (uriKey to overview)
        store.saveOverview(overview)
    }

    fun warmGalleryThumbnails(firstPhotoIndex: Int, lastPhotoIndex: Int) {
        val uriKey = currentUri ?: return
        val list = photoCache[uriKey] ?: photos
        if (list.isEmpty()) return
        val from = (firstPhotoIndex - 100).coerceAtLeast(0)
        val to = (lastPhotoIndex + 100).coerceAtMost(list.lastIndex)
        if (from > to) return
        val window = list.subList(from, to + 1)
        thumbnailWarmJobs[uriKey]?.cancel()
        thumbnailWarmJobs[uriKey] = viewModelScope.launch {
            val warmed = PhotoRepository.ensureThumbnails(getApplication(), uriKey, window)
            val warmedByUri = warmed.associateBy { it.uri }
            val current = photoCache[uriKey].orEmpty()
            val updated = current.map { warmedByUri[it.uri] ?: it }
            photoCache[uriKey] = updated
            store.savePhotoCache(uriKey, updated)
            if (currentUri == uriKey) photos = updated
            withContext(Dispatchers.IO) {
                com.punctum.gallery.data.GalleryImageCache.trimGalleryThumbnails(getApplication(), uriKey, updated)
            }
        }
    }

    fun warmDetailImages(centerIndex: Int) {
        val context = getApplication<Application>()
        val list = photos
        if (list.isEmpty()) return
        val from = (centerIndex - 2).coerceAtLeast(0)
        val to = (centerIndex + 3).coerceAtMost(list.lastIndex)
        viewModelScope.launch(Dispatchers.IO) {
            val loader = coil.Coil.imageLoader(context)
            for (index in from..to) {
                val photo = list[index]
                loader.enqueue(
                    coil.request.ImageRequest.Builder(context)
                        .data(photo.uri)
                        .memoryCacheKey("detail:${photo.uri}")
                        .diskCacheKey("detail:${photo.uri}")
                        .size(1800)
                        .crossfade(false)
                        .build()
                )
            }
        }
    }

    fun deletePhoto(photo: Photo) {
        markDeleting(photo)
        removePhotoFromState(photo)
        viewModelScope.launch {
            val gallery = currentGallery ?: return@launch
            when (val result = PhotoRepository.deletePhoto(getApplication(), gallery.uri, photo)) {
                PhotoRepository.DeleteResult.Deleted -> {
                    delay(10_000)
                    clearDeleting(photo)
                }
                PhotoRepository.DeleteResult.Failed -> {
                    clearDeleting(photo)
                    refreshCurrentData()
                }
                is PhotoRepository.DeleteResult.NeedsUserAction -> {
                    clearDeleting(photo)
                    pendingDeletePhoto = photo
                    pendingDeleteConfirmation = result.pendingIntent
                }
            }
        }
    }

    fun onDeleteConfirmationHandled(confirmed: Boolean) {
        val photo = pendingDeletePhoto
        pendingDeletePhoto = null
        pendingDeleteConfirmation = null
        if (confirmed && photo != null) {
            removePhotoFromState(photo)
            refreshCurrentData()
        }
    }

    private fun removePhotoFromState(photo: Photo) {
        val uriKey = currentUri ?: return
        val updated = photos.filterNot { it.uri == photo.uri }
        photos = updated
        photoCache[uriKey] = updated
        store.savePhotoCache(uriKey, updated)
        cacheOverview(uriKey, updated)
        selectedIndex = selectedIndex?.let { index ->
            when {
                updated.isEmpty() -> null
                index >= updated.size -> updated.lastIndex
                else -> index
            }
        }
    }

    private fun markDeleting(photo: Photo) {
        val now = System.currentTimeMillis()
        deleteTombstones[photo.uri.toString()] = now + 15_000
        deleteTombstones[photoSignature(photo)] = now + 15_000
    }

    private fun clearDeleting(photo: Photo) {
        deleteTombstones.remove(photo.uri.toString())
        deleteTombstones.remove(photoSignature(photo))
    }

    private fun applyDeleteTombstones(list: List<Photo>): List<Photo> {
        val now = System.currentTimeMillis()
        deleteTombstones.entries.removeIf { it.value <= now }
        if (deleteTombstones.isEmpty()) return list
        return list.filterNot { photo ->
            deleteTombstones.containsKey(photo.uri.toString()) ||
                deleteTombstones.containsKey(photoSignature(photo))
        }
    }

    private fun photoSignature(photo: Photo): String =
        "${photo.name}|${photo.fileSizeBytes}|${photo.modifiedMillis}"

    fun closeSwitcher() { showSwitcher = false }
    fun openDetail(index: Int) { selectedIndex = index }
    fun closeDetail() { selectedIndex = null }

    companion object {
        private val HOME_SUBTITLES = listOf(
            "每一个画廊，都是你来时的路",
            "那些感动你的，那些你凝望的",
            "摄影的第一课：所有平凡都藏着神迹",
            "将流逝的时间，翻译成凝固的瞬间",
            "昧旦启明，蓄势新篇",
            "别想太多，先按快门",
            "影像不是记录，是诠释",
            "相机只是工具，心灵才是真正的镜头",
            "光影交错瞬间，捕捉爱与自由的轮廓",
            "慢门流淌时光，瞬间即是永恒",
            "取景框里的世界，比双眼更温柔",
            "追光者，终成为光",
            "按下快门的勇气，比技巧更珍贵",
            "光轨划过暗房，向流星写下情书",
            "镜头，比情话更擅长说永远",
        )
    }
}
