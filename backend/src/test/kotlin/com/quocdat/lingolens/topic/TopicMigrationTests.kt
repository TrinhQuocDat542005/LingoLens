package com.quocdat.lingolens.topic

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
class TopicMigrationTests {
    @Test fun `V15 to V16 preserves all legacy rows and only adds published object mapping`() {
        val source=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password)
        val jdbc=JdbcTemplate(source)
        fun migrate(target:String)=Flyway.configure().dataSource(source).target(target).load().also{it.migrate();it.validate()}
        migrate("15")
        jdbc.update("INSERT INTO users(id,email,password,name,target_level,daily_goal) VALUES (101,'topic-legacy@example.invalid','unchanged','Legacy','B2',17)")
        jdbc.update("INSERT INTO learner_settings(user_id,target_level,daily_new_word_goal) VALUES (101,'B2',17)")
        jdbc.update("INSERT INTO learning_entries VALUES (101,'book','{}',1000,1000,FALSE)")
        jdbc.update("INSERT INTO review_cards(user_id,word,due_at,repetitions) VALUES (101,'book',2000,2)")
        val tables=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','topics','topic_vocabularies')",String::class.java)
        val before=tables.associateWith{jdbc.queryForList("SELECT * FROM $it")}
        val history=jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")
        migrate("16")
        before.forEach{(table,rows)->assertEquals(rows,jdbc.queryForList("SELECT * FROM $table"),table)}
        assertEquals(history,jdbc.queryForList("SELECT version,checksum,success FROM flyway_schema_history WHERE version<>'16' ORDER BY installed_rank"))
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM topic_vocabularies",Int::class.java)!!)
        assertTrue(jdbc.queryForObject("SELECT is_published FROM topics WHERE slug='objects'",Boolean::class.java)!!)
        MigrationChecksums.assertCommittedSourcesUnchanged()
        com.quocdat.lingolens.auth.IdentityTokenRolloutMigrationTests.assertRolloutSourceUnchanged()
        com.quocdat.lingolens.analytics.AnalyticsMigrationTests.assertAnalyticsSourcesUnchanged()
        assertTopicSourceUnchanged()
        assertTrue(jdbc.queryForObject("SELECT checksum IS NOT NULL FROM flyway_schema_history WHERE version='16' AND success",Boolean::class.java)!!)
        migrate("16")
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM topic_vocabularies",Int::class.java)!!)
    }
    companion object {
        @Container @JvmField val postgres=PostgreSQLContainer<Nothing>("postgres:15-alpine")
        fun assertTopicSourceUnchanged(){
            val bytes=requireNotNull(TopicMigrationTests::class.java.getResourceAsStream("/db/migration/V16__published_object_topic.sql"))
                .bufferedReader(Charsets.UTF_8).use{it.readText()}.replace("\r\n","\n").toByteArray(Charsets.UTF_8)
            assertEquals("c18bd56f7c6721077ea1382ac883dea678c3252beabacad3585b123802f5a1fa",
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        }
    }
}
