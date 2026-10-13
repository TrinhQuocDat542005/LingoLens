package com.quocdat.lingolens.lesson

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.quocdat.lingolens.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class LessonDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "lesson-migration.db"
    private var database: LearningDatabase? = null
    private val legacyTables = listOf("SavedWordEntity", "LearningEvent", "VocabularyCache", "OutboxOperation",
        "SyncAccount", "ReviewCard", "ReviewEvent", "ReviewSession")

    @After fun close() { database?.close(); context.deleteDatabase(name) }

    private fun fixture(version: Int) {
        context.deleteDatabase(name)
        val resource = requireNotNull(javaClass.getResourceAsStream("/com.quocdat.lingolens.data.local.LearningDatabase/$version.json"))
        val schema = resource.bufferedReader().use { JsonParser.parseReader(it).asJsonObject["database"].asJsonObject }
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            schema["entities"].asJsonArray.forEach { node ->
                val entity = node.asJsonObject
                val table = entity["tableName"].asString
                old.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                entity["indices"]?.asJsonArray?.forEach { old.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", table)) }
            }
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, ?)", arrayOf(schema["identityHash"].asString))
            for (account in 1..2) {
                old.execSQL("INSERT INTO SavedWordEntity VALUES (?, 'book', ?, ?)", arrayOf<Any>(account, "{\"owner\":$account}", 100L + account))
                old.execSQL("INSERT INTO LearningEvent VALUES (?, 'book', ?)", arrayOf<Any>(account, 50L + account))
                if (version >= 2) {
                    old.execSQL("INSERT INTO OutboxOperation VALUES (?, ?, ?, 'REVIEW_WORD', ?, ?, 3, 'retained error')",
                        arrayOf<Any>(account, "old-op-$account", account, "{\"word\":\"book\"}", if (account == 1) "PENDING" else "BLOCKED"))
                    old.execSQL("INSERT INTO SyncAccount VALUES (?, 1, 123456, 'retained status')", arrayOf(account))
                }
                if (version >= 3) {
                    old.execSQL("INSERT INTO ReviewCard VALUES (?, 'book', 777, 4, 2.1, 3, 1, 'REVIEW', 555)", arrayOf(account))
                    old.execSQL("INSERT INTO ReviewEvent VALUES (?, ?, 'book', 'GOOD', 555, ?)", arrayOf<Any>("event-$account", account, "review-$account"))
                    old.execSQL("INSERT INTO ReviewSession VALUES (?, ?, '[\"book\"]', 1, 1, 444, NULL)", arrayOf<Any>("review-$account", account))
                }
            }
            old.execSQL("INSERT INTO VocabularyCache VALUES ('book', '{\"translation\":\"sách\"}', 222)")
            old.version = version
        } finally { old.close() }
    }

    private fun open(): LearningDatabase = Room.databaseBuilder(context, LearningDatabase::class.java, name)
        .addMigrations(LearningDatabase.MIGRATION_1_2, LearningDatabase.MIGRATION_2_3, LearningDatabase.MIGRATION_3_4)
        .build().also { database = it; it.openHelper.writableDatabase }

    private fun snapshotRaw(): Map<String, List<List<String?>>> {
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        return try { legacyTables.associateWith { table ->
            old.rawQuery("SELECT * FROM $table ORDER BY 1, 2", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
            }
        } } finally { old.close() }
    }

    @Test fun `v3 upgrade retains every legacy row and opens real v4 database`() = runBlocking {
        fixture(3); val before = snapshotRaw(); val db = open()
        assertEquals(4, db.openHelper.writableDatabase.version)
        assertEquals(before, snapshotRaw())
        assertEquals("review-1", db.reviewDao().activeSession(1)?.id)
        assertEquals(listOf("old-op-1"), db.syncDao().pending(1).map { it.operationId })
        assertEquals("BLOCKED", db.syncDao().pending(2).single().state)
        for (table in listOf("ContentPack", "LessonSession", "LessonAttemptOutbox", "CurriculumAccountState")) {
            db.openHelper.writableDatabase.query("SELECT count(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun `v1 and v2 upgrades preserve original words events cache and outbox`() = runBlocking {
        for (version in 1..2) {
            fixture(version); val db = open()
            assertEquals(4, db.openHelper.writableDatabase.version)
            assertEquals("{\"owner\":1}", db.learningDao().find(1, "book")?.content)
            assertEquals(52L, db.learningDao().allEvents(2).single().occurredAt)
            assertEquals(222L, db.learningDao().cached("book")?.cachedAt)
            assertEquals(102L, db.reviewDao().card(2, "book")?.dueAt)
            if (version == 2) assertEquals("old-op-2", db.syncDao().pending(2).single().operationId)
            db.close(); database = null
        }
    }

    private fun pack(account: Long) = ContentPack(account, "revision", "lesson", "course", 1, "{}", "{}", "checksum", 2, 100, true)
    private fun session(account: Long) = LessonSession(account, "attempt", "lesson", "revision", "LESSON", "[]", 0, "{}", "LEARN", 100, 100)
    private fun operation(account: Long) = LessonAttemptOutbox(account, "attempt", "attempt", "{}", "hash", "PENDING", 101, 0)

    @Test fun `new tables isolate identical identifiers by account and survive database reopen`() = runBlocking {
        fixture(3); var db = open()
        for (account in 1L..2L) {
            db.lessonDao().insertPack(pack(account))
            db.lessonDao().saveSession(session(account).copy(answersJson = "{\"owner\":$account}"))
            db.lessonDao().enqueue(operation(account))
            db.lessonDao().saveCurriculum(CurriculumAccountState(account, "course", "ACTIVE", "[]", account.toInt() * 30, 102))
        }
        db.close(); database = null; db = open()
        assertEquals("{\"owner\":1}", db.lessonDao().session(1, "attempt")?.answersJson)
        assertEquals("{\"owner\":2}", db.lessonDao().session(2, "attempt")?.answersJson)
        assertEquals(30, db.lessonDao().curriculum(1, "course")?.confirmedXpTotal)
        assertEquals(60, db.lessonDao().curriculum(2, "course")?.confirmedXpTotal)
        assertNull(db.lessonDao().pack(3, "revision")); assertNull(db.lessonDao().session(3, "attempt"))
        assertTrue(db.lessonDao().pending(3).isEmpty())
        db.lessonDao().acknowledge(1, "attempt")
        assertTrue(db.lessonDao().pending(1).isEmpty()); assertEquals(1, db.lessonDao().pending(2).size)
    }

    @Test fun `foreign keys prevent cross account references and removal of referenced pack`() = runBlocking {
        fixture(3); val db = open(); db.lessonDao().insertPack(pack(1))
        var rejected = false
        try { db.lessonDao().saveSession(session(2)) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected); assertNull(db.lessonDao().session(2, "attempt"))
        db.lessonDao().saveSession(session(1))
        rejected = false
        try { db.lessonDao().enqueue(operation(2)) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected)
        rejected = false
        try { db.openHelper.writableDatabase.execSQL("DELETE FROM ContentPack WHERE accountId=1") } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue(rejected); assertNotNull(db.lessonDao().pack(1, "revision"))
    }

    @Test fun `failed durable enqueue rolls back session and outbox together`() = runBlocking {
        fixture(3); val db = open(); db.lessonDao().insertPack(pack(1))
        var rejected = false
        try {
            db.withTransaction {
                db.lessonDao().saveSession(session(1))
                db.lessonDao().enqueue(operation(1))
                error("Injected failure after enqueue")
            }
        } catch (error: IllegalStateException) { rejected = error.message == "Injected failure after enqueue" }
        assertTrue(rejected)
        assertNull(db.lessonDao().session(1, "attempt")); assertTrue(db.lessonDao().pending(1).isEmpty())
        assertEquals("old-op-1", db.syncDao().pending(1).single().operationId)
    }
}
