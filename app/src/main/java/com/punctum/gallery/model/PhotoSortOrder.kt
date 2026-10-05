package com.punctum.gallery.model

enum class PhotoSortOrder(val id: String, val label: String) {
    CAPTURE("capture", "拍摄"),
    MODIFIED("modified", "编辑");

    fun next(): PhotoSortOrder = if (this == CAPTURE) MODIFIED else CAPTURE

    fun comparator(): Comparator<Photo> = photoTimeComparator(
        this, { it.takenMillis }, { it.modifiedMillis }, { it.uri.toString() },
    )

    companion object {
        fun from(id: String?): PhotoSortOrder = entries.firstOrNull { it.id == id } ?: CAPTURE
    }
}

internal fun <T> photoTimeComparator(
    order: PhotoSortOrder,
    captured: (T) -> Long,
    modified: (T) -> Long,
    identity: (T) -> String,
): Comparator<T> = when (order) {
    PhotoSortOrder.CAPTURE -> compareByDescending(captured).thenByDescending(modified)
    PhotoSortOrder.MODIFIED -> compareByDescending<T> {
        modified(it).takeIf { time -> time > 0L } ?: captured(it)
    }.thenByDescending(captured)
}.thenBy(identity)
