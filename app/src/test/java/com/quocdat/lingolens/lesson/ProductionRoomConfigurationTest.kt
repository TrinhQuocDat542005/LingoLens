package com.quocdat.lingolens.lesson

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProductionRoomConfigurationTest {
    @Test fun `production builder registers every migration without destructive fallback`() {
        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val container = File(sourceRoot, "com/quocdat/lingolens/AppContainer.kt").readText()
        val builder = container.substringAfter("private val learningDatabase =").substringBefore("val syncScheduler")
        assertTrue(builder.contains("Room.databaseBuilder"))
        assertTrue(builder.contains("\"learning.db\""))
        assertTrue(builder.contains(".addMigrations("))
        for (migration in listOf("MIGRATION_1_2", "MIGRATION_2_3", "MIGRATION_3_4")) assertTrue(migration, builder.contains(migration))
        assertTrue(builder.contains(".build()"))
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach {
            assertFalse(it.path, it.readText().contains("fallbackToDestructiveMigration"))
        }
    }
}
