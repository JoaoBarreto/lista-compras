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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (processor.process()) {
        SyncOutcome.Done -> Result.success()
        SyncOutcome.Retry -> Result.retry()
        // The session is gone; the next login requests a new sync.
        SyncOutcome.AuthLost -> Result.failure()
    }
}
