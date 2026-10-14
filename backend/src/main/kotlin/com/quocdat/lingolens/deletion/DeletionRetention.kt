package com.quocdat.lingolens.deletion

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
@ConditionalOnProperty(prefix="app.deletion.retention",name=["enabled"],havingValue="true",matchIfMissing=true)
class DeletionRetention(private val service:DeletionService) {
    @EventListener(ApplicationReadyEvent::class) fun ready(){prune()}
    @Scheduled(cron="0 15 3 * * *",zone="UTC") fun prune(){
        try{service.prune()}catch(_:Exception){LoggerFactory.getLogger(javaClass).warn("Deletion receipt pruning failed; expired receipts remain unconfirmable")}
    }
}
