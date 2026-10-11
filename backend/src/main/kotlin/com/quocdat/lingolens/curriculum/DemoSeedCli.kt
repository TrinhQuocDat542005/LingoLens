package com.quocdat.lingolens.curriculum

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate

object DemoSeedCli {
    @JvmStatic fun main(args: Array<String>) {
        val profiles = System.getenv("SPRING_PROFILES_ACTIVE").orEmpty().split(',').map(String::trim).toSet()
        require(profiles.any { it in setOf("dev","test") } && profiles.none { it in setOf("prod","production") }) { "Explicit dev/test profile required" }
        val source = DriverManagerDataSource(requireNotNull(System.getenv("DB_URL")), requireNotNull(System.getenv("DB_USERNAME")), requireNotNull(System.getenv("DB_PASSWORD")))
        Flyway.configure().dataSource(source).load().migrate()
        val bytes = if (args.isEmpty()) requireNotNull(DemoSeedCli::class.java.getResource("/content/demo-week5.json")).readBytes()
            else java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args.single()))
        val imported = ContentSeedImporter(JdbcTemplate(source), jacksonObjectMapper(), TransactionTemplate(DataSourceTransactionManager(source))).import(bytes, profiles)
        println(if (imported) "DEMO pack imported once" else "DEMO pack already imported; unchanged")
    }
}
