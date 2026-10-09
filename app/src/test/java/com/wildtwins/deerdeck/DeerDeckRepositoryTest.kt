package com.wildtwins.deerdeck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wildtwins.deerdeck.data.DeerDeckRepository
import com.wildtwins.deerdeck.data.db.DeerDeckDatabase
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.backup.BackupDataset
import com.wildtwins.deerdeck.data.backup.BackupSettings
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeerDeckRepositoryTest {
    private val wildlifeProfile = ObservationProfile.WildlifeWatching.storageValue
    private lateinit var database: DeerDeckDatabase
    private lateinit var repository: DeerDeckRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeerDeckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeerDeckRepository(database.dao()) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureDefaultSpeciesCreatesDeckButtonsInOrder() = runTest {
        repository.ensureDefaultSpecies()

        val species = database.dao().getActiveSpecies(wildlifeProfile)

        assertEquals(
            listOf("Fawn", "Buck", "Doe", "Turkey", "Pheasant", "Skunk", "Fox", "Raccoon", "Opossum", "Coyote"),
            species.map { it.name },
        )
        assertTrue(species.all { it.isDefault })
    }

    @Test
    fun ensureDefaultSpeciesArchivesRemovedDefaultsAndAddsNewOnExistingInstall() = runTest {
        database.dao().insertSpecies(
            com.wildtwins.deerdeck.data.model.SpeciesEntity(
                name = "Bird",
                color = 0xFF3A6EA5,
                sortOrder = 3,
                isDefault = true,
            ),
        )
        database.dao().insertSpecies(
            com.wildtwins.deerdeck.data.model.SpeciesEntity(
                name = "Rabbit",
                color = 0xFF8A817C,
                sortOrder = 5,
                isDefault = true,
            ),
        )
        database.dao().insertSpecies(
            com.wildtwins.deerdeck.data.model.SpeciesEntity(
                name = "Deer",
                color = 0xFF7A2E1F,
                sortOrder = 1,
                isDefault = true,
            ),
        )
        database.dao().insertSpecies(
            com.wildtwins.deerdeck.data.model.SpeciesEntity(
                name = "FAE N",
                color = 0xFF0046BE,
                sortOrder = 2,
                isDefault = false,
            ),
        )

        repository.ensureDefaultSpecies()

        val species = database.dao().getActiveSpecies(wildlifeProfile)
        assertTrue(species.none { it.name == "Bird" })
        assertTrue(species.none { it.name == "Deer" })
        assertTrue(species.none { it.name == "FAE N" })
        assertTrue(species.none { it.name == "Rabbit" })
        assertTrue(species.none { it.name == "Other" })
        assertTrue(species.any { it.name == "Fawn" })
        assertTrue(species.any { it.name == "Buck" })
        assertTrue(species.any { it.name == "Doe" })
        assertTrue(species.any { it.name == "Pheasant" })
        assertTrue(species.any { it.name == "Skunk" })
    }

    @Test
    fun quickObservationStartsSessionAndIncrementsCount() = runTest {
        repository.ensureDefaultSpecies()
        val deer = database.dao().getActiveSpecies(wildlifeProfile).first { it.name == "Doe" }

        repository.addQuickObservation(deer.id)
        repository.addQuickObservation(deer.id)

        val session = repository.activeSession.first()
        assertNotNull(session)
        val counts = repository.countsForSession(session!!.id).first()
        assertEquals(2, counts.first { it.speciesName == "Doe" }.total)
    }

    @Test
    fun undoLastObservationRemovesMostRecentCount() = runTest {
        repository.ensureDefaultSpecies()
        val species = database.dao().getActiveSpecies(wildlifeProfile)
        val deer = species.first { it.name == "Doe" }
        val turkey = species.first { it.name == "Turkey" }

        now = 2_000L
        repository.addQuickObservation(deer.id)
        now = 3_000L
        repository.addQuickObservation(turkey.id)

        repository.undoLastObservation()

        val session = repository.activeSession.first()!!
        val counts = repository.countsForSession(session.id).first()
        assertEquals(1, counts.first { it.speciesName == "Doe" }.total)
        assertEquals(0, counts.first { it.speciesName == "Turkey" }.total)
    }

    @Test
    fun decrementSpeciesCountReducesQuantityThenDeletesLatestObservation() = runTest {
        repository.ensureDefaultSpecies()
        val doe = database.dao().getActiveSpecies(wildlifeProfile).first { it.name == "Doe" }
        repository.addObservation(
            speciesId = doe.id,
            quantity = 2,
            notes = "Tree line",
            sex = "Doe",
            ageClass = "Adult",
            behavior = "Feeding",
        )

        repository.decrementSpeciesCount(doe.id)
        var counts = repository.countsForSession(repository.currentSessionId()!!).first()
        assertEquals(1, counts.first { it.speciesName == "Doe" }.total)
        assertEquals("Tree line", database.dao().getAllObservationsForBackup().single().notes)

        repository.decrementSpeciesCount(doe.id)
        counts = repository.countsForSession(repository.currentSessionId()!!).first()
        assertEquals(0, counts.first { it.speciesName == "Doe" }.total)
        assertTrue(database.dao().getAllObservationsForBackup().isEmpty())
    }

    @Test
    fun detailedObservationAndPhotoAreLinkedToActiveSession() = runTest {
        repository.ensureDefaultSpecies()
        val deer = database.dao().getActiveSpecies(wildlifeProfile).first { it.name == "Doe" }

        val observationId = repository.addObservation(
            speciesId = deer.id,
            quantity = 3,
            notes = "At the tree line",
            sex = "Doe",
            ageClass = "Adult",
            behavior = "Feeding",
        )
        repository.addPhoto(uri = "file:///tmp/deerdeck-test.jpg", observationId = observationId)

        val session = repository.activeSession.first()!!
        val counts = repository.countsForSession(session.id).first()
        val photos = repository.photosForSession(session.id).first()

        assertEquals(3, counts.first { it.speciesName == "Doe" }.total)
        assertEquals(1, photos.size)
        assertEquals(observationId, photos.first().observationId)
    }

    @Test
    fun sessionPhotoWithoutDetailedObservationUsesActiveSession() = runTest {
        val sessionId = repository.startSession()

        repository.addMedia(
            uri = "content://media/external/images/media/42",
            mediaType = "photo",
        )

        val photos = repository.photosForSession(sessionId).first()
        assertEquals(1, photos.size)
        assertEquals(sessionId, photos.single().sessionId)
        assertEquals(null, photos.single().observationId)
    }

    @Test
    fun namedFieldLocationIsStoredOnTheSession() = runTest {
        val sessionId = repository.startSession(fieldLocationName = "North stand")

        val session = database.dao().getSession(sessionId)

        assertEquals("North stand", session?.fieldLocationName)
    }

    @Test
    fun customSpeciesCanBeAddedAfterDefaults() = runTest {
        val customProfile = ObservationProfile.custom("custom_test", "Backyard")

        repository.addSpecies(customProfile, "Groundhog", 0xFFBC6C25)

        val species = database.dao().getActiveSpecies(customProfile.storageValue)
        val custom = species.first { it.name == "Groundhog" }
        assertEquals(false, custom.isDefault)
    }

    @Test
    fun moveSpeciesToIndexPersistsObserveTileOrder() = runTest {
        val customProfile = ObservationProfile.custom("custom_test", "Backyard")
        repository.addSpecies(customProfile, "Doe", 0xFF009E73)
        repository.addSpecies(customProfile, "Turkey", 0xFF6A3D9A)
        repository.addSpecies(customProfile, "Fox", 0xFFCC79A7)
        val fox = database.dao().getActiveSpecies(customProfile.storageValue).first { it.name == "Fox" }

        repository.moveSpeciesToIndex(fox.id, 0, customProfile)

        assertEquals(
            listOf("Fox", "Doe", "Turkey"),
            database.dao().getActiveSpecies(customProfile.storageValue).map { it.name },
        )
    }

    @Test
    fun builtInProfilesRejectManualWildlifeChanges() = runTest {
        repository.ensureDefaultSpecies()
        val original = database.dao().getActiveSpecies(wildlifeProfile)

        assertEquals(
            -1L,
            repository.addSpecies(ObservationProfile.WildlifeWatching, "Groundhog", 0xFFBC6C25),
        )
        repository.moveSpeciesToIndex(original.last().id, 0, ObservationProfile.WildlifeWatching)
        repository.archiveSpecies(original.first().id)

        assertEquals(original, database.dao().getActiveSpecies(wildlifeProfile))
    }

    @Test
    fun locationAwareDefaultsReplaceBuiltInListBeforeSessionStarts() = runTest {
        repository.ensureDefaultSpecies()

        val region = repository.refreshLocationAwareSpecies(
            profile = ObservationProfile.WildlifeWatching,
            latitude = 33.45,
            longitude = -112.07,
        )
        val regional = database.dao().getActiveSpecies(wildlifeProfile)
        val sessionId = repository.startSession()

        assertEquals("Southwest", region)
        assertTrue(regional.any { it.name == "Javelina" })
        assertTrue(regional.none { it.name == "Fawn" })
        assertEquals("Wildlife", database.dao().getSession(sessionId)?.title)
    }

    @Test
    fun archivedCustomWildlifeRemainsVisibleInPastSessionCounts() = runTest {
        val customProfile = ObservationProfile.custom("custom_test", "Backyard")
        repository.addSpecies(customProfile, "Groundhog", 0xFFBC6C25)
        val groundhog = database.dao().getActiveSpecies(customProfile.storageValue).single()
        val sessionId = repository.startSession(profile = customProfile)
        repository.addQuickObservation(groundhog.id)
        repository.endActiveSession()

        repository.archiveSpecies(groundhog.id)

        val counts = repository.countsForSession(sessionId, customProfile).first()
        assertEquals(1, counts.single { it.speciesName == "Groundhog" }.total)
        assertTrue(database.dao().getActiveSpecies(customProfile.storageValue).isEmpty())
    }

    @Test
    fun activeSessionAutoEndsAfterThreeHoursWithoutActivity() = runTest {
        val startedAt = now
        repository.startSession()

        now = startedAt + (3 * 60 * 60 * 1_000L) - 1
        assertFalse(repository.endInactiveActiveSessionIfNeeded())

        now += 1
        assertTrue(repository.endInactiveActiveSessionIfNeeded())

        val session = database.dao().getSession(1)!!
        assertEquals(startedAt + (3 * 60 * 60 * 1_000L), session.endedAt)
        assertTrue(session.notes!!.contains("Auto-ended"))
    }

    @Test
    fun replaceDatasetRestoresIdsTagsAndObservationsTransactionally() = runTest {
        repository.ensureDefaultSpecies()
        val dataset = BackupDataset(
            settings = BackupSettings(themeMode = "dark"),
            wildlife = listOf(
                SpeciesEntity(id = 50, name = "Crane", color = 0xFF336699, sortOrder = 10),
            ),
            sessions = listOf(
                ObservationSessionEntity(
                    id = 60,
                    startedAt = 10_000,
                    endedAt = 20_000,
                    tags = "[\"Foggy\"]",
                ),
            ),
            observations = listOf(
                ObservationEntity(
                    id = 70,
                    sessionId = 60,
                    speciesId = 50,
                    observedAt = 12_000,
                    quantity = 4,
                ),
            ),
            media = emptyList(),
        )

        repository.replaceDataset(dataset)

        assertEquals(listOf("Crane"), database.dao().getAllSpecies().map { it.name })
        assertEquals("[\"Foggy\"]", database.dao().getSession(60)?.tags)
        assertEquals(4, database.dao().getAllObservationsForBackup().single().quantity)
    }
}
