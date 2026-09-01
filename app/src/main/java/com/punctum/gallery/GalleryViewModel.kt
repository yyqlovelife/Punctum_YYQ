package com.punctum.gallery

import android.app.Application
import android.app.PendingIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.punctum.gallery.data.GalleryImageCache
import com.punctum.gallery.data.GalleryStore
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.InvitationCardStyle
import com.punctum.gallery.model.Photo
import com.punctum.gallery.model.SystemAlbum
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GalleryStore(app)
    private val photoCache = mutableMapOf<String, List<Photo>>()
    private val thumbnailWarmJobs = mutableMapOf<String, Job>()
    private val coverBuildJobs = mutableMapOf<String, Job>()
    private val photoRefreshJobs = mutableMapOf<String, Job>()
    private var overviewRefreshJob: Job? = null
    private var dataRefreshJob: Job? = null
    private var foregroundSyncJob: Job? = null
    private var pendingDeletePhoto: Photo? = null
    private var pendingMovePhoto: Photo? = null
    private var pendingMoveDestination: SystemAlbum? = null
    private var moveInProgress: Photo? = null
    private val deleteTombstones = mutableMapOf<String, Long>()
    private val moveTombstones = mutableMapOf<String, Long>()

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
    var pendingMediaManagementPermission by mutableStateOf(false)
        private set
    var pendingMoveConfirmation by mutableStateOf<PendingIntent?>(null)
        private set
    var completedMove by mutableStateOf<CompletedPhotoMove?>(null)
        private set
    var moveError by mutableStateOf<String?>(null)
        private set
    var homeToast by mutableStateOf<String?>(null)
        private set
    var pendingHomeScrollIndex by mutableStateOf<Int?>(null)
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
        val stored = store.loadGalleries().filter { gallery ->
            SystemAlbum.isAlbumUri(gallery.uri) || permitted.contains(gallery.uri.toString())
        }
        galleries = stored
        if (stored.isNotEmpty()) {
            store.saveGalleries(stored)
            overviews = store.loadOverviewCache(stored)
            refreshOverviews(force = true)
        }
    }

    fun addSystemAlbums(albums: List<SystemAlbum>) {
        val existing = galleries.map { it.uri.toString() }.toSet()
        val insertAt = galleries.size
        val added = albums.mapNotNull { album ->
            val key = album.uri.toString()
            if (key in existing) {
                null
            } else {
                Gallery(album.uri, album.displayName)
            }
        }
        if (added.isNotEmpty()) {
            galleries = galleries + added
            store.saveGalleries(galleries)
            pendingHomeScrollIndex = insertAt
        }
        goHome()
        homeToast = "添加完成"
    }

    fun clearHomeToast() {
        homeToast = null
    }

    fun clearPendingHomeScroll() {
        pendingHomeScrollIndex = null
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
        coverBuildJobs.remove(key)?.cancel()
        photoRefreshJobs.remove(key)?.cancel()
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
            if (overviews[uriKey] == null) cacheOverview(uriKey, cached)
            loadingPhotos = false
            refreshPhotos(uriKey, replaceVisible = false, cached = cached)
            return
        }
        val persisted = store.loadPhotoCache(uriKey)
        if (persisted.isNotEmpty()) {
            photoCache[uriKey] = persisted
            photos = persisted
            if (overviews[uriKey] == null) cacheOverview(uriKey, persisted)
            loadingPhotos = false
            refreshPhotos(uriKey, replaceVisible = false, cached = persisted)
            return
        }
        loadingPhotos = true
        photos = emptyList()
        refreshPhotos(uriKey, replaceVisible = true, cached = emptyList())
    }

    private fun refreshPhotos(uriKey: String, replaceVisible: Boolean, cached: List<Photo> = photoCache[uriKey].orEmpty()) {
        if (photoRefreshJobs[uriKey]?.isActive == true) return
        photoRefreshJobs[uriKey] = viewModelScope.launch {
            val gallery = galleries.firstOrNull { it.uri.toString() == uriKey } ?: return@launch
            val currentCached = photoCache[uriKey] ?: cached
            val latestPhotos = visiblePhotosForGallery(
                uriKey,
                PhotoRepository.loadLatestPhotos(getApplication(), gallery, currentCached),
            )
            val latestIdentities = latestPhotos.map(::photoFileIdentity).toSet()
            val quickList = visiblePhotosForGallery(
                uriKey,
                (
                    latestPhotos +
                        currentCached.filterNot { photoFileIdentity(it) in latestIdentities }
                    ).sortedWith(PHOTO_NEWEST_FIRST),
            )
            if (quickList != currentCached) {
                val mergedQuick = mergePhotoLists(currentCached, quickList)
                photoCache[uriKey] = mergedQuick
                store.savePhotoCache(uriKey, mergedQuick)
                if (currentUri == uriKey && !isVisiblePhotoListFrozen()) {
                    photos = mergePhotoLists(photos, mergedQuick)
                    if (mergedQuick.isNotEmpty()) loadingPhotos = false
                }
            }
            val list = visiblePhotosForGallery(
                uriKey,
                PhotoRepository.loadPhotos(getApplication(), gallery.uri, quickList),
            )
            val merged = mergePhotoLists(photoCache[uriKey].orEmpty(), list)
            photoCache[uriKey] = merged
            store.savePhotoCache(uriKey, merged)
            cacheOverview(uriKey, merged)
            if (currentUri == uriKey) {
                if (!isVisiblePhotoListFrozen() && (replaceVisible || photos != merged)) {
                    photos = mergePhotoLists(photos, merged)
                }
                loadingPhotos = false
            }
        }
    }

    private fun cacheOverview(uriKey: String, list: List<Photo>) {
        val gallery = galleries.firstOrNull { it.uri.toString() == uriKey } ?: return
        val base = PhotoRepository.overviewFromPhotos(gallery, list)
        val previous = overviews[uriKey]
        val coversUnchanged = previous?.coverUris == base.coverUris
        val reusableAssets = coversUnchanged &&
            previous?.postcardCoverPath?.let { File(it).isFile } == true &&
            previous?.ticketCoverPath?.let { File(it).isFile } == true &&
            previous?.ticketDominantColorArgb != null &&
            previous?.ticketColorVersion == com.punctum.gallery.data.GalleryImageCache.TICKET_COLOR_VERSION
        val immediate = if (coversUnchanged) {
            val cached = requireNotNull(previous)
            base.copy(
                postcardCoverPath = cached.postcardCoverPath,
                ticketCoverPath = cached.ticketCoverPath,
                ticketDominantColorArgb = cached.ticketDominantColorArgb,
                ticketColorVersion = cached.ticketColorVersion,
            )
        } else {
            base
        }
        updateOverviewIfChanged(uriKey, immediate)
        if (base.coverUris.isEmpty()) {
            coverBuildJobs.remove(uriKey)?.cancel()
            return
        }
        if (reusableAssets) return

        val expectedCoverUris = base.coverUris
        coverBuildJobs.remove(uriKey)?.cancel()
        coverBuildJobs[uriKey] = viewModelScope.launch {
            val overview = PhotoRepository.buildOverviewFromPhotos(getApplication(), gallery, list)
            val latestList = photoCache[uriKey] ?: return@launch
            val latestCoverUris = PhotoRepository.overviewFromPhotos(gallery, latestList).coverUris
            if (latestCoverUris == expectedCoverUris &&
                galleries.any { it.uri.toString() == uriKey }
            ) {
                updateOverviewIfChanged(uriKey, overview)
            }
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
        if (overviewRefreshJob?.isActive == true) return
        overviewRefreshJob = viewModelScope.launch {
            val gallerySnapshot = galleries.toList()
            val memoryCacheSnapshot = photoCache.toMap()
            val cachedPhotosByGallery = withContext(Dispatchers.IO) {
                gallerySnapshot.associate { gallery ->
                    val key = gallery.uri.toString()
                    key to (memoryCacheSnapshot[key] ?: store.loadPhotoCache(key))
                }
            }

            // 每个画廊只扫描一次，并行按 EXIF 拍摄时间计算数量、跨度和前 4 张封面。
            // 已有照片复用 v3 元数据缓存，只为新增或发生变化的文件重新读取 EXIF。
            coroutineScope {
                gallerySnapshot.forEach { gallery ->
                    launch {
                        val key = gallery.uri.toString()
                        if (!force && overviews[key]?.loading == false) return@launch
                        val cached = cachedPhotosByGallery[key].orEmpty()
                        coverBuildJobs.remove(key)?.cancel()
                        val overview = PhotoRepository.loadOverview(getApplication(), gallery, cached)
                        coverBuildJobs.remove(key)?.cancel()
                        if (galleries.any { it.uri.toString() == key }) {
                            updateOverviewIfChanged(key, overview)
                        }
                    }
                }
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
        val from = firstPhotoIndex.coerceAtLeast(0)
        val to = (lastPhotoIndex + 8).coerceAtMost(list.lastIndex)
        if (from > to) return
        val window = list.subList(from, to + 1)
        thumbnailWarmJobs[uriKey]?.cancel()
        thumbnailWarmJobs[uriKey] = viewModelScope.launch {
            val warmed = PhotoRepository.ensureThumbnails(getApplication(), uriKey, window)
            val warmedByUri = warmed.associateBy { it.uri }
            val current = photoCache[uriKey].orEmpty()
            val updated = current.map { warmedByUri[it.uri] ?: it }
            if (updated == current) return@launch
            photoCache[uriKey] = updated
            store.savePhotoCache(uriKey, updated)
            if (currentUri == uriKey && !isVisiblePhotoListFrozen()) {
                photos = applyMoveTombstones(uriKey, updated)
            }
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
                        .data(com.punctum.gallery.data.PhotoStill.forDetail(photo))
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
        performDelete(photo)
    }

    private fun performDelete(photo: Photo) {
        viewModelScope.launch {
            val gallery = currentGallery
            if (gallery == null) {
                clearDeleting(photo)
                refreshCurrentData()
                return@launch
            }
            when (val result = PhotoRepository.deletePhoto(getApplication(), gallery.uri, photo)) {
                PhotoRepository.DeleteResult.Deleted -> Unit
                PhotoRepository.DeleteResult.NeedsMediaManagementPermission -> {
                    pendingDeletePhoto = photo
                    pendingMediaManagementPermission = true
                }
                PhotoRepository.DeleteResult.Failed -> {
                    clearDeleting(photo)
                    refreshCurrentData()
                }
                is PhotoRepository.DeleteResult.NeedsUserAction -> {
                    pendingDeletePhoto = photo
                    pendingDeleteConfirmation = result.pendingIntent
                }
            }
        }
    }

    fun onMediaManagementPermissionHandled(granted: Boolean) {
        val photo = pendingDeletePhoto
        pendingDeletePhoto = null
        pendingMediaManagementPermission = false
        if (photo == null) return
        if (granted) {
            markDeleting(photo)
            performDelete(photo)
        } else {
            clearDeleting(photo)
            refreshCurrentData()
        }
    }

    fun onDeleteConfirmationHandled(confirmed: Boolean) {
        val photo = pendingDeletePhoto
        pendingDeletePhoto = null
        pendingDeleteConfirmation = null
        if (confirmed && photo != null) {
            markDeleting(photo)
            removePhotoFromState(photo)
        } else if (photo != null) {
            clearDeleting(photo)
            refreshCurrentData()
        }
    }

    fun movePhoto(photo: Photo, destination: SystemAlbum) {
        if (destination.uri.toString() == currentUri) return
        val index = photos.indexOfFirst { it.uri == photo.uri }
        val next = if (index >= 0) photos.getOrNull(index + 1) else null
        moveInProgress = photo
        viewModelScope.launch {
            when (val result = PhotoRepository.movePhoto(getApplication(), photo, destination)) {
                PhotoRepository.MoveResult.Moved -> {
                    markMovedFrom(currentUri, photo)
                    completedMove = CompletedPhotoMove(photo, destination, next)
                }
                PhotoRepository.MoveResult.SameAlbum ->
                    moveInProgress = null
                is PhotoRepository.MoveResult.NeedsUserAction -> {
                    moveInProgress = null
                    pendingMovePhoto = photo
                    pendingMoveDestination = destination
                    pendingMoveConfirmation = result.pendingIntent
                }
                PhotoRepository.MoveResult.Failed -> {
                    moveInProgress = null
                    moveError = "移动失败，请稍后重试"
                }
            }
        }
    }

    fun onMoveConfirmationHandled(confirmed: Boolean) {
        val photo = pendingMovePhoto
        val destination = pendingMoveDestination
        pendingMovePhoto = null
        pendingMoveDestination = null
        pendingMoveConfirmation = null
        if (confirmed && photo != null && destination != null) {
            movePhoto(photo, destination)
        }
    }

    fun acknowledgeCompletedMove() {
        val move = completedMove ?: return
        removePhotoFromState(move.photo)
        val destKey = move.destination.uri.toString()
        if (photos.isEmpty()) {
            homeToast = "该项目已移动到 ${move.destination.displayName}"
        }
        if (galleries.any { it.uri.toString() == destKey }) {
            clearMovedInto(destKey, move.photo)
            val destList = ((photoCache[destKey] ?: store.loadPhotoCache(destKey)) + move.photo)
                .distinctBy { it.uri }
                .sortedWith(PHOTO_NEWEST_FIRST)
            photoCache[destKey] = destList
            store.savePhotoCache(destKey, destList)
            cacheOverview(destKey, destList)
        }
        moveInProgress = null
        completedMove = null
    }

    private fun mergePhotoLists(current: List<Photo>, incoming: List<Photo>): List<Photo> {
        if (current.isEmpty()) return incoming
        val currentByUri = current.associateBy { it.uri }
        return incoming.map { new ->
            val old = currentByUri[new.uri]
            if (old != null && old.width == new.width && old.height == new.height) {
                if (old.thumbnailPath == null && new.thumbnailPath != null) {
                    old.copy(thumbnailPath = new.thumbnailPath)
                } else {
                    old
                }
            } else {
                new
            }
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

    private fun isVisiblePhotoListFrozen(): Boolean =
        moveInProgress != null || completedMove != null

    private fun visiblePhotosForGallery(uriKey: String, list: List<Photo>): List<Photo> =
        excludeHeldMove(
            applyMoveTombstones(
                uriKey,
                applyDeleteTombstones(list.distinctBy { it.uri }),
            ),
            uriKey,
        )

    private fun excludeHeldMove(list: List<Photo>, uriKey: String): List<Photo> {
        if (uriKey != currentUri) return list
        val heldUri = completedMove?.photo?.uri ?: return list
        return list.filterNot { it.uri == heldUri }
    }

    private fun markMovedFrom(uriKey: String?, photo: Photo) {
        if (uriKey.isNullOrBlank()) return
        val until = System.currentTimeMillis() + MOVE_TOMBSTONE_MILLIS
        moveTombstones[moveTombstoneUriKey(uriKey, photo)] = until
        moveTombstones[moveTombstoneIdentityKey(uriKey, photo)] = until
    }

    private fun clearMovedInto(uriKey: String, photo: Photo) {
        moveTombstones.remove(moveTombstoneUriKey(uriKey, photo))
        moveTombstones.remove(moveTombstoneIdentityKey(uriKey, photo))
    }

    private fun applyMoveTombstones(uriKey: String, list: List<Photo>): List<Photo> {
        val now = System.currentTimeMillis()
        moveTombstones.entries.removeIf { it.value <= now }
        if (moveTombstones.isEmpty()) return list
        return list.filterNot { photo ->
            moveTombstones.containsKey(moveTombstoneUriKey(uriKey, photo)) ||
                moveTombstones.containsKey(moveTombstoneIdentityKey(uriKey, photo))
        }
    }

    private fun moveTombstoneUriKey(uriKey: String, photo: Photo): String =
        "$uriKey|uri|${photo.uri}"

    private fun moveTombstoneIdentityKey(uriKey: String, photo: Photo): String =
        "$uriKey|id|${photoFileIdentity(photo)}"

    private fun markDeleting(photo: Photo) {
        val now = System.currentTimeMillis()
        deleteTombstones[photo.uri.toString()] = now + DELETE_TOMBSTONE_MILLIS
        deleteTombstones[photoSignature(photo)] = now + DELETE_TOMBSTONE_MILLIS
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

    private fun photoFileIdentity(photo: Photo): String =
        "${photo.name}|${photo.fileSizeBytes}"

    fun closeSwitcher() { showSwitcher = false }
    fun openDetail(index: Int) { selectedIndex = index }
    fun closeDetail() { selectedIndex = null }

    fun clearMoveError() { moveError = null }

    companion object {
        private const val DELETE_TOMBSTONE_MILLIS = 120_000L
        private const val MOVE_TOMBSTONE_MILLIS = 45_000L
        private val PHOTO_NEWEST_FIRST =
            compareByDescending<Photo> { it.takenMillis }
                .thenByDescending { it.modifiedMillis }
                .thenBy { it.uri.toString() }

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
            "观止，关心每一幅照片被重新看见的时刻",
            "每一次回望，都重新感受影像的重量",
        )
    }
}

data class CompletedPhotoMove(
    val photo: Photo,
    val destination: SystemAlbum,
    val nextPhoto: Photo?,
)
