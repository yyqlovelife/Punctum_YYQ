package com.punctum.gallery.model

import android.net.Uri

/** DCIM / Pictures 下的一个系统图集。 */
data class SystemAlbum(
    val bucketId: Long,
    val displayName: String,
    val relativePath: String,
    val itemCount: Int,
) {
    val uri: Uri
        get() = albumUri(bucketId, relativePath, displayName)

    companion object {
        private const val SCHEME = "punctum"
        private const val HOST = "album"

        fun albumUri(bucketId: Long, relativePath: String, displayName: String): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST)
                .appendQueryParameter("id", bucketId.toString())
                .appendQueryParameter("path", relativePath)
                .appendQueryParameter("name", displayName)
                .build()

        fun isAlbumUri(uri: Uri): Boolean =
            uri.scheme == SCHEME && uri.host == HOST

        fun bucketId(uri: Uri): Long? =
            uri.getQueryParameter("id")?.toLongOrNull()
    }
}
