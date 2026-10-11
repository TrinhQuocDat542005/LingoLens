package com.quocdat.lingolens.sync

import kotlinx.coroutines.CancellationException

class SyncCoordinator(private val legacy:suspend(Long)->SyncOutcome,private val lessons:suspend(Long)->SyncOutcome) {
    suspend fun run(account:Long):SyncOutcome {
        suspend fun runQueue(action:suspend(Long)->SyncOutcome)=try{action(account)}catch(error:CancellationException){throw error}catch(_:Exception){SyncOutcome.RETRY}
        val results=listOf(runQueue(legacy),runQueue(lessons))
        return when{SyncOutcome.RETRY in results->SyncOutcome.RETRY;SyncOutcome.PAUSED in results->SyncOutcome.PAUSED;SyncOutcome.BLOCKED in results->SyncOutcome.BLOCKED;else->SyncOutcome.COMPLETE}
    }
}
