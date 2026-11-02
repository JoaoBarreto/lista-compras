package com.example.listacompras.ui.shopping

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.listacompras.R
import com.example.listacompras.domain.model.ListKind

/** The wording of one list; the screen itself does not know which list it shows. */
data class ShoppingTexts(
    val sectionOpen: String,
    val sectionDone: String,
    val clearDone: String,
    val addLabel: String,
    val empty: String,
    val allDone: String,
    val waitingList: String,
    /** Label of the optional quantity field; null when the list has no quantities (Tarefas). */
    val quantityLabel: String?,
)

@Composable
fun shoppingTexts(kind: ListKind): ShoppingTexts {
    val ids = when (kind) {
        ListKind.SHOPPING -> listOf(
            R.string.shopping_section_to_buy, R.string.shopping_section_bought, R.string.shopping_clear_bought,
            R.string.shopping_add_label, R.string.shopping_empty, R.string.shopping_all_bought,
        )
        ListKind.TASKS -> listOf(
            R.string.tasks_section_open, R.string.tasks_section_done, R.string.tasks_clear_done,
            R.string.tasks_add_label, R.string.tasks_empty, R.string.tasks_all_done,
        )
    }
    val s = ids.map { stringResource(it) }
    return ShoppingTexts(s[0], s[1], s[2], s[3], s[4], s[5], stringResource(R.string.shopping_waiting_list),
        if (kind == ListKind.SHOPPING) stringResource(R.string.shopping_quantity_label) else null,
    )
}
