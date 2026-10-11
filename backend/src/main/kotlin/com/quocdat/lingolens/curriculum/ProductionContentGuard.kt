package com.quocdat.lingolens.curriculum

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@DependsOn("flywayInitializer")
class ProductionContentGuard(private val jdbc: JdbcTemplate, private val environment: Environment) : InitializingBean {
    override fun afterPropertiesSet() = verify(jdbc, environment.activeProfiles.toSet())
    companion object {
        fun verify(jdbc: JdbcTemplate, profiles: Set<String>) {
            if (profiles.none { it in setOf("prod", "production") }) return
            val demo = jdbc.queryForObject("SELECT (SELECT count(*) FROM content_import_receipts WHERE content_status='DEMO') + (SELECT count(*) FROM courses WHERE content_status='DEMO') + (SELECT count(*) FROM lesson_revisions WHERE content_status='DEMO')", Long::class.java)!!
            check(demo == 0L) { "Production startup refused: DEMO content exists" }
        }
    }
}
