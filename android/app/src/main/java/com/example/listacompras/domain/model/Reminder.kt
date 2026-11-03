package com.example.listacompras.domain.model

/** How long before an event its reminder fires. [slug] is the value stored in the `reminder` select. */
enum class Reminder(val slug: String) {
    AT_TIME("at_time"),
    ONE_HOUR("1h"),
    ONE_DAY("1d"),
    TWO_DAYS("2d"),
    THREE_DAYS("3d"),
    FOUR_DAYS("4d"),
    FIVE_DAYS("5d"),
    ;

    companion object {
        /** Unknown or blank values (for example a newer server option) map to null, meaning no reminder. */
        fun fromSlug(slug: String?): Reminder? = entries.firstOrNull { it.slug == slug }
    }
}
