package com.example.listacompras.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.listacompras.data.sync.OutboxProcessor
import com.example.listacompras.data.sync.Reconciler
import com.example.listacompras.data.sync.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background check (about every 15 min, as often as Android allows): sync first, then
 * look for items the other user added. Without a session or network nothing happens.
 */
@HiltWorker
class NotificationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: OutboxProcessor,
    private val reconciler: Reconciler,
    private val checker: ShoppingNotificationChecker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = processor.process().takeIf { it != SyncOutcome.Done } ?: reconciler.pull()
        if (outcome == SyncOutcome.Done) checker.check()
        // Never retry on its own: the next period is the retry.
        return Result.success()
    }
}
