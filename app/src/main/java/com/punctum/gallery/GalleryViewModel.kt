package com.punctum.gallery

import android.app.Application
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
import kotlin.random.Random

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GalleryStore(app)
    private val photoCache = mutableMapOf<String, List<Photo>>()

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
            refreshPhotos(uriKey, replaceVisible = false)
            return
        }
        loadingPhotos = true
        photos = emptyList()
        refreshPhotos(uriKey, replaceVisible = true)
    }

    private fun refreshPhotos(uriKey: String, replaceVisible: Boolean) {
        viewModelScope.launch {
            val gallery = galleries.firstOrNull { it.uri.toString() == uriKey } ?: return@launch
            val list = PhotoRepository.loadPhotos(getApplication(), gallery.uri)
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
        val overview = PhotoRepository.overviewFromPhotos(gallery, list)
        overviews = overviews + (uriKey to overview)
        store.saveOverview(overview)
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
        viewModelScope.launch {
            galleries.forEach { g ->
                val key = g.uri.toString()
                if (!force && overviews[key]?.loading == false) return@forEach
                val cached = photoCache[key]
                val ov = if (cached != null) PhotoRepository.overviewFromPhotos(g, cached)
                else PhotoRepository.loadOverview(getApplication(), g)
                overviews = overviews + (key to ov)
                store.saveOverview(ov)
            }
        }
    }

    fun deletePhoto(photo: Photo) {
        val uriKey = currentUri ?: return
        viewModelScope.launch {
            val deleted = PhotoRepository.deletePhoto(getApplication(), photo.uri)
            if (!deleted) return@launch
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
    }

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
        )
    }
}
