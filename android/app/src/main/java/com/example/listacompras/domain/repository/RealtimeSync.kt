package com.example.listacompras.domain.repository

interface RealtimeSync {

    /**
     * Keeps the live connection to the server open and applies its changes to local storage.
     * Suspends until cancelled, so call it from a lifecycle-scoped coroutine (while the app is in
     * the foreground). While logged out it just waits.
     */
    suspend fun run()
}
