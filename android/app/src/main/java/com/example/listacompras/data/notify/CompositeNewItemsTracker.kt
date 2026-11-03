package com.example.listacompras.data.notify

import com.example.listacompras.domain.repository.NewItemsTracker

/** Tells every notification checker (shopping, calendar) when the app is on screen. */
class CompositeNewItemsTracker(private val trackers: List<NewItemsTracker>) : NewItemsTracker {

    override suspend fun onAppVisible() = trackers.forEach { it.onAppVisible() }

    override suspend fun onAppHidden() = trackers.forEach { it.onAppHidden() }
}
