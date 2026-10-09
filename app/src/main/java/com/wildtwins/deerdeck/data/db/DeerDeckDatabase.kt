package com.wildtwins.deerdeck.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesEntity

@Database(
    entities = [
        ObservationSessionEntity::class,
        SpeciesEntity::class,
        ObservationEntity::class,
        ObservationPhotoEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class DeerDeckDatabase : RoomDatabase() {
    abstract fun dao(): DeerDeckDao

    companion object {
        @Volatile
        private var instance: DeerDeckDatabase? = null

        fun getInstance(context: Context): DeerDeckDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeerDeckDatabase::class.java,
                    "deerdeck.db",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                    )
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_photos ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'photo'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN sunsetAt INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN weatherSummary TEXT")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN temperatureF REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN windSummary TEXT")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN environmentCapturedAt INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN environmentStatus TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM observation_photos")
                db.execSQL("DELETE FROM observations")
                db.execSQL("DELETE FROM observation_sessions")
                db.execSQL("DELETE FROM species")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN moonPhase TEXT")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN moonIlluminationPercent REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN lastActivityAt INTEGER")
                db.execSQL("UPDATE observation_sessions SET lastActivityAt = startedAt WHERE lastActivityAt IS NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE species ADD COLUMN emoji TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN locationAccuracyMeters REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN locationProvider TEXT")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN elevationFeet REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN pressureInHg REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN humidityPercent REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN dewPointF REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN visibilityMiles REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN precipitationProbabilityPercent REAL")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN forecastSummary TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE species ADD COLUMN profileKey TEXT NOT NULL DEFAULT 'wildlife_watching'",
                )
                db.execSQL(
                    "ALTER TABLE observation_sessions ADD COLUMN profileKey TEXT NOT NULL DEFAULT 'wildlife_watching'",
                )
                db.execSQL("DROP INDEX IF EXISTS index_species_name")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_species_profileKey_name ON species(profileKey, name)",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN tags TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN fieldLocationName TEXT")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN environmentSchemaVersion INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN environmentAttemptCount INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN locationCapturedAt INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN weatherObservedAt INTEGER")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN elevationSource TEXT")
                db.execSQL("ALTER TABLE observation_sessions ADD COLUMN weatherSource TEXT")
            }
        }
    }
}
