package com.example.listacompras.data.sync

/** A full pull of the server state; implemented by [Reconciler], an interface so tests can fake it. */
fun interface FullPull {
    suspend fun pull(): SyncOutcome
}
