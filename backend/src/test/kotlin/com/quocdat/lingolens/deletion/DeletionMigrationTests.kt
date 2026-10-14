package com.quocdat.lingolens.deletion

import com.quocdat.lingolens.integration.MigrationChecksums
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.*
import java.security.MessageDigest

@Testcontainers @Tag("postgres")
class DeletionMigrationTests {
    @Test fun `V16 to V17 adds anonymous receipts without changing any legacy rows or history`() {
        val source=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password);val jdbc=JdbcTemplate(source)
        fun migrate(target:String)=Flyway.configure().dataSource(source).target(target).load().also{it.migrate();it.validate()}
        migrate("16")
        jdbc.update("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (101,'deletion-upgrade@example.invalid','unchanged','Upgrade','B2',17)")
        jdbc.update("INSERT INTO learner_settings(user_id,target_level,daily_new_word_goal) VALUES (101,'B2',17)")
        jdbc.update("INSERT INTO learning_entries VALUES (101,'book','{}',1000,1000,FALSE)")
        val tables=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history'",String::class.java)
        val before=tables.associateWith{jdbc.queryForList("SELECT * FROM $it")}
        val history=jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")
        migrate("17")
        before.forEach{(table,rows)->assertEquals(rows,jdbc.queryForList("SELECT * FROM $table"),table)}
        assertEquals(history,jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version<>'17' ORDER BY installed_rank"))
        assertTrue(jdbc.queryForList("SELECT * FROM account_deletion_receipts").isEmpty())
        MigrationChecksums.assertCommittedSourcesUnchanged()
        com.quocdat.lingolens.auth.IdentityTokenRolloutMigrationTests.assertRolloutSourceUnchanged()
        com.quocdat.lingolens.analytics.AnalyticsMigrationTests.assertAnalyticsSourcesUnchanged()
        com.quocdat.lingolens.topic.TopicMigrationTests.assertTopicSourceUnchanged()
        assertReceiptSourceUnchanged();migrate("17")
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        fun assertReceiptSourceUnchanged(){
            val bytes=requireNotNull(DeletionMigrationTests::class.java.getResourceAsStream("/db/migration/V17__anonymous_deletion_receipts.sql"))
                .bufferedReader(Charsets.UTF_8).use{it.readText()}.replace("\r\n","\n").toByteArray(Charsets.UTF_8)
            assertEquals("93269f53f125e202465b6730abd23778b35ffc5e8eaaf00d90b1b3f00c970398",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        }
    }
}
