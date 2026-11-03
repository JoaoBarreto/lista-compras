package com.example.listacompras.ui.main

import com.example.listacompras.domain.model.ListKind

/** The top-level tabs. The names of the first two are also view-model and saved-state keys: do not rename. */
enum class MainTab {
    SHOPPING,
    TASKS,
    CALENDAR,
    ;

    /** The shopping-style list shown by this tab, or null for the calendar. */
    val listKind: ListKind?
        get() = when (this) {
            SHOPPING -> ListKind.SHOPPING
            TASKS -> ListKind.TASKS
            CALENDAR -> null
        }
}
