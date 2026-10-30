package com.example.listacompras.domain.model

/** What the user sees about synchronization. Not to be confused with the per-item [SyncState]. */
enum class SyncStatus { Synced, Syncing, Offline, Error }
