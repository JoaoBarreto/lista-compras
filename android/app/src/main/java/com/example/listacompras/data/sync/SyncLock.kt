package com.example.listacompras.data.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes everything that sends or applies server state ([OutboxProcessor], [Reconciler]).
 * Without it a pull could fetch a snapshot, miss an item whose CREATE is sent meanwhile, and then
 * delete that item locally. Not reentrant: never call one of them while holding the lock.
 */
class SyncLock {
    val mutex = Mutex()
}
