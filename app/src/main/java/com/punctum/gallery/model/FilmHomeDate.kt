package com.punctum.gallery.model

/** Resolve overview's three date forms into the reference's yyyy.MM-yyyy.MM format. */
internal fun filmHomeDate(raw: String): String {
    val compact = raw.replace("年", ".").replace("月", "").replace("/", ".")
        .replace(Regex("\\s*[—–~至]\\s*"), "-").replace(" ", "")
    val range = Regex("^(\\d{4})\\.(\\d{1,2})(?:-(?:(\\d{4})\\.)?(\\d{1,2}))?$").matchEntire(compact)
        ?: return compact.ifBlank { "时间未知" }
    val (y1, m1, y2, m2) = range.destructured
    val start = "$y1.${m1.padStart(2, '0')}"
    return if (m2.isEmpty()) start else "$start-${y2.ifBlank { y1 }}.${m2.padStart(2, '0')}"
}
