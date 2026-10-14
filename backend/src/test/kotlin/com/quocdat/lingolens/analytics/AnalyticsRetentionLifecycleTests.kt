package com.quocdat.lingolens.analytics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import java.util.function.Supplier

class AnalyticsRetentionLifecycleTests {
    @Test fun `test setting removes both startup and daily retention jobs`() {
        val service=mock(AnalyticsService::class.java)
        ApplicationContextRunner().withUserConfiguration(AnalyticsScheduling::class.java,AnalyticsRetentionLifecycle::class.java)
            .withBean(AnalyticsService::class.java,Supplier{service}).withPropertyValues("app.analytics.retention.enabled=false")
            .run{context->
                assertNull(context.startupFailure)
                assertTrue(context.getBeansOfType(AnalyticsRetentionLifecycle::class.java).isEmpty())
                assertTrue(context.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks.isEmpty())
                verifyNoInteractions(service)
            }
    }
    @Test fun `production default retains startup prune and one daily scheduled job`() {
        val service=mock(AnalyticsService::class.java)
        ApplicationContextRunner().withUserConfiguration(AnalyticsScheduling::class.java,AnalyticsRetentionLifecycle::class.java)
            .withBean(AnalyticsService::class.java,Supplier{service})
            .run{context->
                assertNull(context.startupFailure)
                assertEquals(1,context.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks.size)
                context.getBean(AnalyticsRetentionLifecycle::class.java).ready()
                val calls=mockingDetails(service).invocations.filter{it.method.name=="prune"}
                assertEquals(1,calls.size)
                assertTrue(calls.single().arguments.single() is java.time.Instant)
            }
    }
}
