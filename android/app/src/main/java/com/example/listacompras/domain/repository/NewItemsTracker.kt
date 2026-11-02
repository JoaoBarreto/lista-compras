package com.example.listacompras.domain.repository

/**
 * Tells the notification logic when the user can see the lists, so that what they have seen on
 * screen is never notified afterwards.
 */
interface NewItemsTracker {
    /** The app became visible: everything on the list is about to be seen; clear any notification. */
    suspend fun onAppVisible()

    /** The app left the screen: whatever arrived while it was visible counts as seen. */
    suspend fun onAppHidden()
}
