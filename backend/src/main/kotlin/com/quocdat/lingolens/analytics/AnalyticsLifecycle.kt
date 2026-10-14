package com.quocdat.lingolens.analytics

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

data class LessonConfirmedEvent(val account:Long,val attempt:UUID)

@Configuration @EnableScheduling
class AnalyticsScheduling

@Component
class AnalyticsLifecycle(private val analytics:AnalyticsService) {
    private val log=LoggerFactory.getLogger(javaClass)
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    fun confirmed(event:LessonConfirmedEvent){
        try{analytics.recordPassed(event.account,event.attempt)}
        catch(error:Exception){log.warn("Analytics event write failed; official lesson transaction remains committed")}
    }
}

@Component
@ConditionalOnProperty(prefix="app.analytics.retention",name=["enabled"],havingValue="true",matchIfMissing=true)
class AnalyticsRetentionLifecycle(private val analytics:AnalyticsService) {
    private val log=LoggerFactory.getLogger(javaClass)
    @EventListener(ApplicationReadyEvent::class)
    fun ready(){prune()}
    @Scheduled(cron="0 0 3 * * *",zone="UTC")
    fun prune(){
        try{analytics.prune()}
        catch(error:Exception){log.warn("Analytics retention cleanup failed; expired events remain excluded from reports")}
    }
}
