package com.example.listacompras.data.notify

/** Shows or clears the "new items on the shopping list" notification. */
interface NewItemsNotifier {
    /** [names] are all the unseen items, oldest first. Replaces any notification already shown. */
    fun show(names: List<String>)

    fun cancel()
}
