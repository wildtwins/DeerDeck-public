package com.wildtwins.deerdeck.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeerDeckDatabaseMigrationTest {
    private val databaseName = "deerdeck-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DeerDeckDatabase::class.java,
    )

    @Test
    fun migration10To11PreservesSessionsAndAddsCaptureMetadata() {
        helper.createDatabase(databaseName, 10).apply {
            execSQL(
                """
                INSERT INTO observation_sessions (
                    id, startedAt, profileKey, weatherSummary, temperatureF, environmentStatus
                ) VALUES (7, 1700000000000, 'wildlife_watching', 'Clear', 45.0, 'Captured')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            11,
            true,
            DeerDeckDatabase.MIGRATION_10_11,
        ).use { database ->
            database.query(
                """
                SELECT id, weatherSummary, environmentSchemaVersion, environmentAttemptCount,
                    locationCapturedAt, weatherObservedAt, elevationSource, weatherSource
                FROM observation_sessions
                """.trimIndent(),
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals("Clear", cursor.getString(1))
                for (column in 2..7) {
                    assertEquals(true, cursor.isNull(column))
                }
            }
        }
    }
}
