package com.punctum.gallery.model

enum class InvitationCardStyle(val id: String, val label: String) {
    POSTCARD("postcard", "明信片"),
    TICKET("ticket", "票根");

    fun next(): InvitationCardStyle =
        if (this == POSTCARD) TICKET else POSTCARD

    companion object {
        fun from(id: String?): InvitationCardStyle =
            entries.firstOrNull { it.id == id } ?: POSTCARD
    }
}
