package com.example.listacompras.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationCheckScheduler {

    private const val UNIQUE_NAME = "notification-check"

    /** Idempotent: an already scheduled check is kept. 15 minutes is the minimum Android allows. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
