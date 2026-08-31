package com.punctum.gallery.model

enum class InvitationCardStyle(val id: String, val label: String) {
    POSTCARD("postcard", "明信片"),
    TICKET("ticket", "票根"),
    REVERSAL_FILM("reversal_film", "反转胶片");

    fun next(): InvitationCardStyle = when (this) {
        POSTCARD -> TICKET
        TICKET -> REVERSAL_FILM
        REVERSAL_FILM -> POSTCARD
    }

    companion object {
        fun from(id: String?): InvitationCardStyle =
            entries.firstOrNull { it.id == id } ?: POSTCARD
    }
}
