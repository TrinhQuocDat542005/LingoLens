package com.quocdat.lingolens.curriculum

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.util.zip.CRC32

abstract class ContentSchemaMigration(private val version: Int) : BaseJavaMigration() {
    override fun getChecksum(): Int = CRC32().apply {
        update(resource(false).inputStream.bufferedReader().use { it.readText() }.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
        update(resource(true).inputStream.bufferedReader().use { it.readText() }.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
    }.value.toInt()
    override fun migrate(context: Context) {
        ScriptUtils.executeSqlScript(context.connection, resource(false))
        // H2 is an API test substitute, not evidence for PostgreSQL constraints/locks.
        if (context.connection.metaData.databaseProductName == "PostgreSQL") {
            context.connection.createStatement().use { statement ->
                statement.execute(resource(true).inputStream.bufferedReader().use { it.readText() })
            }
        }
    }
    private fun resource(postgres: Boolean) = ClassPathResource("db/content-schema/V$version${if (postgres) "-postgres" else ""}.sql")
}
