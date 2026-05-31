package com.punctum.gallery.model

import android.net.Uri

/** 一个画廊区 = 一个被映射的系统文件夹。 */
data class Gallery(
    val uri: Uri,
    val displayName: String,
    val styleId: String = GalleryStyle.ORIGINAL.id,
)

/** 画廊展示风格。V0.0.1 仅实现「原幅」，其余为后续扩展预留。 */
enum class GalleryStyle(val id: String, val label: String) {
    ORIGINAL("original", "原幅");

    companion object {
        fun from(id: String?): GalleryStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}
