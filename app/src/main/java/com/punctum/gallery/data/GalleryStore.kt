package com.punctum.gallery.data

import android.content.Context
import android.net.Uri
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.Photo
import org.json.JSONArray
import org.json.JSONObject

/** 用 SharedPreferences + JSON 持久化画廊列表（含自定义名/风格）与「上次所在画廊」。 */
class GalleryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("punctum_galleries", Context.MODE_PRIVATE)

    fun loadGalleries(): List<Gallery> {
        val raw = prefs.getString(KEY_GALLERIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Gallery(
                    uri = Uri.parse(o.getString("uri")),
                    displayName = o.getString("name"),
                    styleId = o.optString("style", "original"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveGalleries(galleries: List<Gallery>) {
        val arr = JSONArray()
        galleries.forEach { g ->
            arr.put(JSONObject().apply {
                put("uri", g.uri.toString())
                put("name", g.displayName)
                put("style", g.styleId)
            })
        }
        prefs.edit().putString(KEY_GALLERIES, arr.toString()).apply()
    }

    fun loadOverviewCache(galleries: List<Gallery>): Map<String, GalleryOverview> {
        val galleriesByUri = galleries.associateBy { it.uri.toString() }
        val raw = prefs.getString(KEY_OVERVIEWS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val gallery = galleriesByUri[key] ?: return@forEach
                    val cached = obj.getJSONObject(key)
                    val covers = cached.optJSONArray("covers") ?: JSONArray()
                    put(
                        key,
                        GalleryOverview(
                            gallery = gallery,
                            count = cached.optInt("count", 0),
                            timeSpan = cached.optString("timeSpan", ""),
                            coverUris = (0 until covers.length()).mapNotNull { i ->
                                covers.optString(i).takeIf { it.isNotBlank() }?.let(Uri::parse)
                            },
                            loading = false,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveOverview(overview: GalleryOverview) {
        val key = overview.gallery.uri.toString()
        val root = try {
            JSONObject(prefs.getString(KEY_OVERVIEWS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val covers = JSONArray()
        overview.coverUris.forEach { covers.put(it.toString()) }
        root.put(
            key,
            JSONObject().apply {
                put("count", overview.count)
                put("timeSpan", overview.timeSpan)
                put("covers", covers)
            }
        )
        prefs.edit().putString(KEY_OVERVIEWS, root.toString()).apply()
    }

    fun removeOverview(uriKey: String) {
        val root = try {
            JSONObject(prefs.getString(KEY_OVERVIEWS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        root.remove(uriKey)
        prefs.edit().putString(KEY_OVERVIEWS, root.toString()).apply()
    }

    fun loadPhotoCache(uriKey: String): List<Photo> {
        val root = try {
            JSONObject(prefs.getString(KEY_PHOTOS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val arr = root.optJSONArray(uriKey) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = if (obj.has("lat")) obj.optDouble("lat") else null
            val lon = if (obj.has("lon")) obj.optDouble("lon") else null
            Photo(
                uri = Uri.parse(obj.optString("uri")),
                name = obj.optString("name", "未命名"),
                width = obj.optInt("width", 0),
                height = obj.optInt("height", 0),
                takenMillis = obj.optLong("takenMillis", 0L),
                dateTaken = obj.optString("dateTaken").ifBlank { null },
                latLong = if (lat != null && lon != null) doubleArrayOf(lat, lon) else null,
                shutter = obj.optString("shutter").ifBlank { null },
                iso = obj.optString("iso").ifBlank { null },
                aperture = obj.optString("aperture").ifBlank { null },
                focalLength = obj.optString("focalLength").ifBlank { null },
                device = obj.optString("device").ifBlank { null },
                lens = obj.optString("lens").ifBlank { null },
            )
        }
    }

    fun savePhotoCache(uriKey: String, photos: List<Photo>) {
        val root = try {
            JSONObject(prefs.getString(KEY_PHOTOS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val arr = JSONArray()
        photos.forEach { p ->
            arr.put(JSONObject().apply {
                put("uri", p.uri.toString())
                put("name", p.name)
                put("width", p.width)
                put("height", p.height)
                put("takenMillis", p.takenMillis)
                put("dateTaken", p.dateTaken)
                p.latLong?.let {
                    put("lat", it[0])
                    put("lon", it[1])
                }
                put("shutter", p.shutter)
                put("iso", p.iso)
                put("aperture", p.aperture)
                put("focalLength", p.focalLength)
                put("device", p.device)
                put("lens", p.lens)
            })
        }
        root.put(uriKey, arr)
        prefs.edit().putString(KEY_PHOTOS, root.toString()).apply()
    }

    fun removePhotoCache(uriKey: String) {
        val root = try {
            JSONObject(prefs.getString(KEY_PHOTOS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        root.remove(uriKey)
        prefs.edit().putString(KEY_PHOTOS, root.toString()).apply()
    }

    var lastGalleryUri: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) { prefs.edit().putString(KEY_LAST, value).apply() }

    var invitationStyleId: String
        get() = prefs.getString(KEY_INVITATION_STYLE, "postcard") ?: "postcard"
        set(value) { prefs.edit().putString(KEY_INVITATION_STYLE, value).apply() }

    companion object {
        private const val KEY_GALLERIES = "galleries"
        private const val KEY_LAST = "last_gallery"
        private const val KEY_OVERVIEWS = "overviews"
        private const val KEY_INVITATION_STYLE = "invitation_style"
        private const val KEY_PHOTOS = "photos"
    }
}
