package com.example.listacompras.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: OutboxProcessor,
    private val reconciler: Reconciler,
) : CoroutineWorker(context, params) {

    // Send first, then pull: the pull must not run while local changes are still unsent. The two
    // take the shared lock one after the other, never nested.
    override suspend fun doWork(): Result = when (processor.process().takeIf { it != SyncOutcome.Done } ?: reconciler.pull()) {
        SyncOutcome.Done -> Result.success()
        SyncOutcome.Retry -> Result.retry()
        // The session is gone; the next login requests a new sync.
        SyncOutcome.AuthLost -> Result.failure()
    }
}
