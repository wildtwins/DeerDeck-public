package com.wildtwins.deerdeck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wildtwins.deerdeck.data.backup.BackupDataset
import com.wildtwins.deerdeck.data.model.DatasetCounts
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationExportRow
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.DailySpeciesCount
import com.wildtwins.deerdeck.data.model.RecentObservation
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SessionSummary
import com.wildtwins.deerdeck.data.model.SessionWildlifeCount
import com.wildtwins.deerdeck.data.model.SpeciesCount
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.model.WildlifeObservationFact
import kotlinx.coroutines.flow.Flow

@Dao
interface DeerDeckDao {
    @Insert
    suspend fun insertSession(session: ObservationSessionEntity): Long

    @Update
    suspend fun updateSession(session: ObservationSessionEntity)

    @Query("SELECT * FROM observation_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<ObservationSessionEntity?>

    @Query("SELECT * FROM observation_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): ObservationSessionEntity?

    @Query("SELECT * FROM observation_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): ObservationSessionEntity?

    @Query("DELETE FROM observation_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query(
        """
        SELECT
            s.id,
            s.startedAt,
            s.endedAt,
            s.profileKey,
            s.title,
            s.notes,
            s.tags,
            s.fieldLocationName,
            s.latitude,
            s.longitude,
            s.locationAccuracyMeters,
            s.locationProvider,
            s.elevationFeet,
            s.sunsetAt,
            s.weatherSummary,
            s.temperatureF,
            s.windSummary,
            s.pressureInHg,
            s.humidityPercent,
            s.dewPointF,
            s.visibilityMiles,
            s.precipitationProbabilityPercent,
            s.forecastSummary,
            s.moonPhase,
            s.moonIlluminationPercent,
            s.environmentStatus,
            s.environmentSchemaVersion,
            s.environmentAttemptCount,
            s.locationCapturedAt,
            s.weatherObservedAt,
            s.elevationSource,
            s.weatherSource,
            COALESCE((SELECT SUM(o.quantity) FROM observations o WHERE o.sessionId = s.id), 0) AS totalObservations,
            (SELECT COUNT(*) FROM observation_photos p WHERE p.sessionId = s.id) AS photoCount
        FROM observation_sessions s
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT
            s.id,
            s.startedAt,
            s.endedAt,
            s.profileKey,
            s.title,
            s.notes,
            s.tags,
            s.fieldLocationName,
            s.latitude,
            s.longitude,
            s.locationAccuracyMeters,
            s.locationProvider,
            s.elevationFeet,
            s.sunsetAt,
            s.weatherSummary,
            s.temperatureF,
            s.windSummary,
            s.pressureInHg,
            s.humidityPercent,
            s.dewPointF,
            s.visibilityMiles,
            s.precipitationProbabilityPercent,
            s.forecastSummary,
            s.moonPhase,
            s.moonIlluminationPercent,
            s.environmentStatus,
            s.environmentSchemaVersion,
            s.environmentAttemptCount,
            s.locationCapturedAt,
            s.weatherObservedAt,
            s.elevationSource,
            s.weatherSource,
            COALESCE((SELECT SUM(o.quantity) FROM observations o WHERE o.sessionId = s.id), 0) AS totalObservations,
            (SELECT COUNT(*) FROM observation_photos p WHERE p.sessionId = s.id) AS photoCount
        FROM observation_sessions s
        WHERE s.startedAt >= :startAt AND s.startedAt < :endAt
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeSessionSummariesBetween(startAt: Long, endAt: Long): Flow<List<SessionSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpecies(species: SpeciesEntity): Long

    @Update
    suspend fun updateSpecies(species: SpeciesEntity)

    @Query("SELECT * FROM species WHERE profileKey = :profileKey AND isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeActiveSpecies(profileKey: String): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM species WHERE profileKey = :profileKey AND isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getActiveSpecies(profileKey: String): List<SpeciesEntity>

    @Query("SELECT * FROM species ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSpecies(): List<SpeciesEntity>

    @Query("SELECT * FROM species ORDER BY id ASC")
    suspend fun getAllSpeciesForBackup(): List<SpeciesEntity>

    @Query("SELECT * FROM species WHERE profileKey = :profileKey AND name = :name LIMIT 1")
    suspend fun getSpeciesByName(profileKey: String, name: String): SpeciesEntity?

    @Query("SELECT * FROM species WHERE id = :speciesId LIMIT 1")
    suspend fun getSpecies(speciesId: Long): SpeciesEntity?

    @Query("SELECT COUNT(*) FROM species")
    suspend fun getSpeciesCount(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM species WHERE profileKey = :profileKey")
    suspend fun getMaxSpeciesSortOrder(profileKey: String): Int

    @Query("UPDATE species SET isArchived = 1 WHERE id = :speciesId")
    suspend fun archiveSpecies(speciesId: Long)

    @Query("UPDATE species SET sortOrder = :sortOrder WHERE id = :speciesId")
    suspend fun updateSpeciesSortOrder(speciesId: Long, sortOrder: Int)

    @Insert
    suspend fun insertObservation(observation: ObservationEntity): Long

    @Query("DELETE FROM observations WHERE id = :observationId")
    suspend fun deleteObservation(observationId: Long)

    @Query("SELECT * FROM observations WHERE sessionId = :sessionId ORDER BY observedAt DESC, id DESC LIMIT 1")
    suspend fun getLastObservationForSession(sessionId: Long): ObservationEntity?

    @Query(
        "SELECT * FROM observations WHERE sessionId = :sessionId AND speciesId = :speciesId " +
            "ORDER BY observedAt DESC, id DESC LIMIT 1",
    )
    suspend fun getLastObservationForSessionAndSpecies(sessionId: Long, speciesId: Long): ObservationEntity?

    @Query("UPDATE observations SET quantity = :quantity WHERE id = :observationId")
    suspend fun updateObservationQuantity(observationId: Long, quantity: Int)

    @Query("SELECT * FROM observations WHERE id = :observationId")
    suspend fun getObservation(observationId: Long): ObservationEntity?

    @Query("SELECT * FROM observations ORDER BY observedAt ASC, id ASC")
    suspend fun getAllObservationsForBackup(): List<ObservationEntity>

    @Query(
        """
        SELECT
            sp.id AS speciesId,
            sp.name AS speciesName,
            sp.color AS color,
            COALESCE(SUM(o.quantity), 0) AS total
        FROM species sp
        LEFT JOIN observations o ON o.speciesId = sp.id AND o.sessionId = :sessionId
        WHERE sp.profileKey = :profileKey
            AND (sp.isArchived = 0 OR o.id IS NOT NULL)
        GROUP BY sp.id
        ORDER BY sp.sortOrder ASC, sp.name ASC
        """,
    )
    fun observeCountsForSession(sessionId: Long, profileKey: String): Flow<List<SpeciesCount>>

    @Query(
        """
        SELECT
            sp.id AS speciesId,
            sp.name AS speciesName,
            sp.color AS color,
            COALESCE(SUM(o.quantity), 0) AS total
        FROM species sp
        INNER JOIN observations o ON o.speciesId = sp.id
        GROUP BY sp.id
        ORDER BY total DESC, sp.sortOrder ASC, sp.name ASC
        """,
    )
    fun observeOverallSpeciesCounts(): Flow<List<SpeciesCount>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM species) AS wildlife,
            (SELECT COUNT(*) FROM observation_sessions) AS sessions,
            (SELECT COUNT(*) FROM observations) AS observations,
            (SELECT COUNT(*) FROM observation_photos) AS media
        """,
    )
    fun observeDatasetCounts(): Flow<DatasetCounts>

    @Query(
        """
        SELECT
            o.sessionId AS sessionId,
            sp.name AS wildlifeName,
            SUM(o.quantity) AS total
        FROM observations o
        INNER JOIN species sp ON sp.id = o.speciesId
        GROUP BY o.sessionId, sp.id
        ORDER BY o.sessionId DESC, total DESC, sp.name ASC
        """,
    )
    fun observeSessionWildlifeCounts(): Flow<List<SessionWildlifeCount>>

    @Query(
        """
        SELECT
            o.id AS observationId,
            sp.id AS speciesId,
            sp.name AS wildlifeName,
            sp.color AS color,
            o.sessionId AS sessionId,
            o.observedAt AS observedAt,
            o.quantity AS quantity
        FROM observations o
        INNER JOIN species sp ON sp.id = o.speciesId
        ORDER BY o.observedAt ASC, o.id ASC
        """,
    )
    fun observeWildlifeObservationFacts(): Flow<List<WildlifeObservationFact>>

    @Query(
        """
        SELECT
            sp.id AS speciesId,
            sp.name AS speciesName,
            sp.color AS color,
            COALESCE(SUM(o.quantity), 0) AS total
        FROM species sp
        INNER JOIN observations o ON o.speciesId = sp.id
        INNER JOIN observation_sessions s ON s.id = o.sessionId
        WHERE s.startedAt >= :startAt AND s.startedAt < :endAt
        GROUP BY sp.id
        ORDER BY total DESC, sp.sortOrder ASC, sp.name ASC
        """,
    )
    fun observeSpeciesCountsBetween(startAt: Long, endAt: Long): Flow<List<SpeciesCount>>

    @Query(
        """
        SELECT
            sp.id AS speciesId,
            sp.name AS speciesName,
            sp.color AS color,
            CAST((o.observedAt - :startAt) / :dayMillis AS INTEGER) AS dayIndex,
            SUM(o.quantity) AS total
        FROM observations o
        INNER JOIN species sp ON sp.id = o.speciesId
        WHERE o.observedAt >= :startAt AND o.observedAt < :endAt
        GROUP BY sp.id, dayIndex
        ORDER BY sp.sortOrder ASC, sp.name ASC, dayIndex ASC
        """,
    )
    fun observeDailySpeciesCountsBetween(
        startAt: Long,
        endAt: Long,
        dayMillis: Long,
    ): Flow<List<DailySpeciesCount>>

    @Query("SELECT * FROM observation_photos WHERE sessionId = :sessionId ORDER BY capturedAt DESC")
    fun observePhotosForSession(sessionId: Long): Flow<List<ObservationPhotoEntity>>

    @Query("SELECT * FROM observation_photos ORDER BY capturedAt DESC, id DESC")
    fun observeAllMedia(): Flow<List<ObservationPhotoEntity>>

    @Query("SELECT * FROM observation_photos ORDER BY capturedAt ASC, id ASC")
    suspend fun getAllMediaForBackup(): List<ObservationPhotoEntity>

    @Insert
    suspend fun insertPhoto(photo: ObservationPhotoEntity): Long

    @Query(
        """
        SELECT
            o.id AS id,
            sp.name AS speciesName,
            sp.color AS color,
            o.observedAt AS observedAt,
            o.quantity AS quantity,
            o.notes AS notes,
            o.sex AS sex,
            o.ageClass AS ageClass,
            o.behavior AS behavior
        FROM observations o
        INNER JOIN species sp ON sp.id = o.speciesId
        WHERE o.sessionId = :sessionId
        ORDER BY o.observedAt DESC, o.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentObservations(sessionId: Long, limit: Int = 4): Flow<List<RecentObservation>>

    @Query(
        """
        SELECT
            s.id AS sessionId,
            s.startedAt AS sessionStartedAt,
            s.endedAt AS sessionEndedAt,
            s.notes AS sessionNotes,
            s.tags AS sessionTags,
            s.fieldLocationName AS sessionFieldLocationName,
            s.latitude AS sessionLatitude,
            s.longitude AS sessionLongitude,
            s.locationAccuracyMeters AS sessionLocationAccuracyMeters,
            s.locationProvider AS sessionLocationProvider,
            s.elevationFeet AS sessionElevationFeet,
            s.sunsetAt AS sessionSunsetAt,
            s.weatherSummary AS sessionWeatherSummary,
            s.temperatureF AS sessionTemperatureF,
            s.windSummary AS sessionWindSummary,
            s.pressureInHg AS sessionPressureInHg,
            s.humidityPercent AS sessionHumidityPercent,
            s.dewPointF AS sessionDewPointF,
            s.visibilityMiles AS sessionVisibilityMiles,
            s.precipitationProbabilityPercent AS sessionPrecipitationProbabilityPercent,
            s.forecastSummary AS sessionForecastSummary,
            s.moonPhase AS sessionMoonPhase,
            s.moonIlluminationPercent AS sessionMoonIlluminationPercent,
            s.environmentStatus AS sessionEnvironmentStatus,
            s.environmentSchemaVersion AS sessionEnvironmentSchemaVersion,
            s.environmentAttemptCount AS sessionEnvironmentAttemptCount,
            s.locationCapturedAt AS sessionLocationCapturedAt,
            s.weatherObservedAt AS sessionWeatherObservedAt,
            s.elevationSource AS sessionElevationSource,
            s.weatherSource AS sessionWeatherSource,
            sp.name AS speciesName,
            o.observedAt AS observedAt,
            o.quantity AS quantity,
            o.sex AS sex,
            o.ageClass AS ageClass,
            o.behavior AS behavior,
            o.notes AS notes
        FROM observations o
        INNER JOIN observation_sessions s ON s.id = o.sessionId
        INNER JOIN species sp ON sp.id = o.speciesId
        ORDER BY s.startedAt DESC, o.observedAt ASC, o.id ASC
        """,
    )
    suspend fun getObservationExportRows(): List<ObservationExportRow>

    @Query("SELECT * FROM observation_sessions ORDER BY startedAt ASC, id ASC")
    suspend fun getAllSessionsForBackup(): List<ObservationSessionEntity>

    @Query("UPDATE observation_sessions SET lastActivityAt = :activityAt WHERE id = :sessionId AND endedAt IS NULL")
    suspend fun updateSessionLastActivity(sessionId: Long, activityAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreSpecies(items: List<SpeciesEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreSessions(items: List<ObservationSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreObservations(items: List<ObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun restoreMedia(items: List<ObservationPhotoEntity>)

    @Query("DELETE FROM observation_photos")
    suspend fun deleteAllMedia()

    @Query("DELETE FROM observations")
    suspend fun deleteAllObservations()

    @Query("DELETE FROM observation_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM species")
    suspend fun deleteAllSpecies()

    @Transaction
    suspend fun replaceDataset(dataset: BackupDataset) {
        clearDataset()
        if (dataset.wildlife.isNotEmpty()) restoreSpecies(dataset.wildlife)
        if (dataset.sessions.isNotEmpty()) restoreSessions(dataset.sessions)
        if (dataset.observations.isNotEmpty()) restoreObservations(dataset.observations)
        if (dataset.media.isNotEmpty()) restoreMedia(dataset.media)
    }

    @Transaction
    suspend fun clearDataset() {
        deleteAllMedia()
        deleteAllObservations()
        deleteAllSessions()
        deleteAllSpecies()
    }
}
