package com.punctum.gallery

/** Resolve identity before translating to a two-photo row (the header occupies row zero). */
internal fun galleryReturnPhoto(
    ids: List<String>, viewedID: String?, fallbackIndex: Int, excluded: Set<String> = emptySet(),
): String? {
    if (viewedID != null && viewedID in ids && viewedID !in excluded) return viewedID
    val origin = ids.indexOf(viewedID).takeIf { it >= 0 } ?: fallbackIndex.coerceAtLeast(0)
    return ids.drop(origin).firstOrNull { it !in excluded }
        ?: ids.take(origin).lastOrNull { it !in excluded }
}

internal fun galleryReturnRow(ids: List<String>, photoID: String?): Int? =
    ids.indexOf(photoID).takeIf { it >= 0 }?.let { it / 2 + 1 }

/** Positive values scroll content upwards; use the measured viewport, including its insets. */
internal fun galleryReturnCenterDelta(
    itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int,
): Float = itemOffset + itemSize / 2f - (viewportStart + viewportEnd) / 2f

/** Membership is captured at entry, so later row shifts cannot change the return decision. */
internal fun shouldCenterGalleryReturn(photoID: String?, entryVisibleIDs: Set<String>): Boolean =
    photoID != null && photoID !in entryVisibleIDs
