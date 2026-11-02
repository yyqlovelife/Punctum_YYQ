package com.punctum.gallery.model

import android.net.Uri

/** 画廊切换页里「画展邀请卡」所需的概览信息。 */
data class GalleryOverview(
    val gallery: Gallery,
    val count: Int = 0,
    val timeSpan: String = "",
    val coverUris: List<Uri> = emptyList(),
    val coverRevisions: List<String> = emptyList(),
    val postcardCoverPath: String? = null,
    val ticketCoverPath: String? = null,
    val ticketDominantColorArgb: Int? = null,
    val ticketColorVersion: Int = 0,
    val loading: Boolean = true,
) {
    /** Ignore generated paper/cover assets; compare the underlying media snapshot. */
    fun hasSamePhotoContent(other: GalleryOverview): Boolean =
        count == other.count && timeSpan == other.timeSpan &&
            coverUris == other.coverUris && coverRevisions == other.coverRevisions
}
