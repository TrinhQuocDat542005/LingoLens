package com.quocdat.lingolens.sync

import android.content.Context
import androidx.work.*
import com.quocdat.lingolens.LingoLensApplication
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getLong("accountId", -1)
        if (accountId <= 0) return Result.failure()
        val engine = (applicationContext as LingoLensApplication).container.syncCoordinator
        return when (engine.run(accountId)) {
            SyncOutcome.RETRY -> Result.retry()
            else -> Result.success()
        }
    }
}

class SyncScheduler(context: Context) {
    private val work = WorkManager.getInstance(context.applicationContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule(accountId: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("accountId" to accountId))
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // Appending prevents losing an enqueue racing with a finishing worker.
        work.enqueueUniqueWork("sync-now-$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    fun resume(accountId: Long) {
        schedule(accountId)
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("accountId" to accountId)).setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        work.enqueueUniquePeriodicWork("sync-periodic-$accountId", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }
}
