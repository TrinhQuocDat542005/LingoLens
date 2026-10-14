package com.quocdat.lingolens.analytics

import android.content.Context
import androidx.work.*
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.sync.SyncOutcome
import java.util.concurrent.TimeUnit

class AnalyticsWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val account=inputData.getLong("accountId",-1)
        if(account<=0)return Result.failure()
        val repository=(applicationContext as LingoLensApplication).container.analyticsRepository
        repository.bestEffort{repository.recover(account)}
        return if(repository.sync(account)==SyncOutcome.RETRY)Result.retry() else Result.success()
    }
}
class AnalyticsScheduler(context:Context) {
    private val manager=WorkManager.getInstance(context.applicationContext)
    private val network=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun schedule(account:Long){
        val request=OneTimeWorkRequestBuilder<AnalyticsWorker>().setInputData(workDataOf("accountId" to account)).setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        manager.enqueueUniqueWork("analytics-now-$account",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }
    fun resume(account:Long){
        schedule(account)
        val request=PeriodicWorkRequestBuilder<AnalyticsWorker>(15,TimeUnit.MINUTES).setInputData(workDataOf("accountId" to account)).setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        manager.enqueueUniquePeriodicWork("analytics-periodic-$account",ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun cancel(account:Long){manager.cancelUniqueWork("analytics-now-$account");manager.cancelUniqueWork("analytics-periodic-$account")}
}
