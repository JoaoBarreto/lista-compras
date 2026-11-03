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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodic background check (about every 15 min, as often as Android allows): sync first, then
 * look for items and events the other user added. Without a session or network nothing happens.
 */
@HiltWorker
class NotificationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: OutboxProcessor,
    private val reconciler: Reconciler,
    private val checker: ShoppingNotificationChecker,
    private val eventChecker: EventNotificationChecker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = processor.process().takeIf { it != SyncOutcome.Done } ?: reconciler.pull()
        if (outcome == SyncOutcome.Done) {
            // Independent: one failing (or returning early) never skips the other.
            runCatching { checker.check() }.onFailure { if (it is CancellationException) throw it }
            runCatching { eventChecker.check() }.onFailure { if (it is CancellationException) throw it }
        }
        // Never retry on its own: the next period is the retry.
        return Result.success()
    }
}
