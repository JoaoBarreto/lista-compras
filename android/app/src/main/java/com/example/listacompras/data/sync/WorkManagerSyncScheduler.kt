package com.example.listacompras.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.listacompras.domain.repository.SyncScheduler
import java.util.concurrent.TimeUnit

class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE queues a follow-up run when one is already running, so an operation
        // written just as a run finishes is never left unsent (KEEP would drop that request).
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "outbox-sync"
    }
}
