package com.wildtwins.deerdeck.data.backup

import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationPhotoEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.SharedLocationPrecision
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeerDeckBackupJsonTest {
    @Test
    fun backupIncludesDatasetAndVerifiesReadBack() {
        val archive = DeerDeckBackupJson.build(
            createdAt = 1_725_000_000_000L,
            appVersionName = "1.5.0",
            appVersionCode = 14,
            themeMode = AppThemeMode.Dark,
            autoBackupEnabled = false,
            mediaSaveLocation = "deerdeck_only",
            hapticFeedbackEnabled = false,
            environmentCaptureEnabled = false,
            activeProfile = "wildlife_watching",
            customProfiles = listOf(ObservationProfile.custom("custom_backyard", "Backyard")),
            sessionTags = listOf("Clear", "Feeder full"),
            fieldLocations = listOf("North stand", "Back field"),
            selectedFieldLocation = "North stand",
            sharedLocationPrecision = SharedLocationPrecision.Hidden,
            includeNamedFieldLocationsInShares = true,
            fieldModeEnabled = true,
            keepScreenAwake = false,
            wildlife = listOf(
                SpeciesEntity(id = 10, name = "Buck", color = 0xFF3355AA, sortOrder = 10),
            ),
            sessions = listOf(
                ObservationSessionEntity(
                    id = 20,
                    startedAt = 1_725_000_000_000L,
                    endedAt = 1_725_000_900_000L,
                    tags = "[\"Clear\"]",
                    fieldLocationName = "North stand",
                    latitude = 45.0,
                    longitude = -93.0,
                    weatherSummary = "Clear",
                    pressureInHg = 29.92,
                    moonPhase = "Waxing crescent",
                    environmentSchemaVersion = 1,
                    environmentAttemptCount = 2,
                    locationCapturedAt = 1_725_000_000_000L,
                    weatherObservedAt = 1_724_999_700_000L,
                    elevationSource = "USGS 3DEP",
                    weatherSource = "NWS KMSP",
                ),
            ),
            observations = listOf(
                ObservationEntity(
                    id = 30,
                    sessionId = 20,
                    speciesId = 10,
                    observedAt = 1_725_000_030_000L,
                    quantity = 2,
                ),
            ),
            media = listOf(
                ObservationPhotoEntity(
                    id = 40,
                    observationId = 30,
                    sessionId = 20,
                    uri = "content://media/photo/40",
                    capturedAt = 1_725_000_040_000L,
                    mediaType = "photo",
                ),
                ObservationPhotoEntity(
                    id = 41,
                    sessionId = 20,
                    uri = "content://media/audio/41",
                    capturedAt = 1_725_000_050_000L,
                    mediaType = "audio",
                ),
            ),
        )

        val root = JSONObject(archive.content)
        val payload = root.getJSONObject("payload")

        assertEquals("withrow-deerdeck-backup", root.getString("format"))
        assertEquals(1, root.getInt("formatVersion"))
        assertEquals(1, root.getJSONObject("counts").getInt("sessions"))
        assertEquals("1.5.0", payload.getJSONObject("app").getString("versionName"))
        assertEquals("dark", payload.getJSONObject("settings").getString("themeMode"))
        assertEquals("wildlife_watching", payload.getJSONObject("settings").getString("activeProfile"))
        assertEquals(1, payload.getJSONObject("settings").getJSONArray("customProfiles").length())
        assertEquals(2, payload.getJSONObject("settings").getJSONArray("fieldLocations").length())
        assertEquals(1, payload.getJSONArray("wildlife").length())
        assertEquals(1, payload.getJSONArray("sessions").length())
        assertEquals(1, payload.getJSONArray("observations").length())
        assertEquals(2, payload.getJSONArray("media").length())

        DeerDeckBackupJson.verifyReadBack(
            content = archive.content,
            expectedFileChecksum = archive.fileChecksumSha256,
            expectedCounts = archive.counts,
        )

        val restored = DeerDeckBackupJson.parse(archive.content)
        assertEquals(false, restored.settings.autoBackupEnabled)
        assertEquals("deerdeck_only", restored.settings.mediaSaveLocation)
        assertEquals(false, restored.settings.hapticFeedbackEnabled)
        assertEquals(false, restored.settings.environmentCaptureEnabled)
        assertEquals("Backyard", restored.settings.customProfiles?.single()?.label)
        assertEquals(listOf("Clear", "Feeder full"), restored.settings.sessionTags)
        assertEquals(listOf("North stand", "Back field"), restored.settings.fieldLocations)
        assertEquals("North stand", restored.settings.selectedFieldLocation)
        assertEquals("hidden", restored.settings.sharedLocationPrecision)
        assertEquals(true, restored.settings.includeNamedFieldLocationsInShares)
        assertEquals(true, restored.settings.fieldModeEnabled)
        assertEquals(false, restored.settings.keepScreenAwake)
        assertEquals("North stand", restored.sessions.single().fieldLocationName)
        assertEquals("[\"Clear\"]", restored.sessions.single().tags)
        assertEquals(1, restored.sessions.single().environmentSchemaVersion)
        assertEquals(2, restored.sessions.single().environmentAttemptCount)
        assertEquals("USGS 3DEP", restored.sessions.single().elevationSource)
        assertEquals("NWS KMSP", restored.sessions.single().weatherSource)
        assertEquals(2, restored.observations.single().quantity)
        assertEquals("audio", restored.media.last().mediaType)
    }

    @Test
    fun verifyReadBackRejectsMismatchedCounts() {
        val archive = DeerDeckBackupJson.build(
            createdAt = 1_725_000_000_000L,
            appVersionName = "1.5.0",
            appVersionCode = 14,
            themeMode = AppThemeMode.System,
            wildlife = emptyList(),
            sessions = emptyList(),
            observations = emptyList(),
            media = emptyList(),
        )

        try {
            DeerDeckBackupJson.verifyReadBack(
                content = archive.content,
                expectedFileChecksum = archive.fileChecksumSha256,
                expectedCounts = archive.counts.copy(sessions = 1),
            )
            fail("Expected backup verification to reject mismatched counts.")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun parseRejectsTamperedPayload() {
        val archive = DeerDeckBackupJson.build(
            createdAt = 1_725_000_000_000L,
            appVersionName = "1.7.0",
            appVersionCode = 16,
            themeMode = AppThemeMode.System,
            wildlife = emptyList(),
            sessions = emptyList(),
            observations = emptyList(),
            media = emptyList(),
        )

        val tampered = archive.content.replace("\"themeMode\": \"system\"", "\"themeMode\": \"dark\"")
        try {
            DeerDeckBackupJson.parse(tampered)
            fail("Expected restore parser to reject a modified payload.")
        } catch (_: IOException) {
            // Expected.
        }
    }
}
