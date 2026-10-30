package com.example.listacompras.domain.repository

/** Asks for the outbox to be sent as soon as the network allows. Safe to call after every write. */
interface SyncScheduler {
    fun requestSync()
}
