package com.wildtwins.deerdeck.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppSettingsRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun startsWithOnlyLockedBuiltInProfiles() {
        val repository = AppSettingsRepository(context)

        assertEquals(listOf("Wildlife", "Birds"), repository.profiles.value.map { it.label })
        assertTrue(repository.profiles.value.all { it.isBuiltIn })
    }

    @Test
    fun customProfilesHaveNoFixedCountLimitAndPersist() {
        val repository = AppSettingsRepository(context)

        repeat(30) { index ->
            assertTrue(repository.addCustomProfile("Profile ${index + 1}") != null)
        }

        val restored = AppSettingsRepository(context)
        assertEquals(32, restored.profiles.value.size)
        assertEquals("Profile 30", restored.activeProfile.value.label)
        assertTrue(restored.activeProfile.value.isCustom)
    }

    @Test
    fun customProfilesCanBeRenamedAndDeletedButBuiltInsCannot() {
        val repository = AppSettingsRepository(context)
        val custom = repository.addCustomProfile("Backyard")!!

        assertTrue(repository.renameCustomProfile(custom, "North Woods"))
        val renamed = repository.activeProfile.value
        assertEquals("North Woods", renamed.label)
        assertFalse(repository.deleteCustomProfile(ObservationProfile.WildlifeWatching))
        assertTrue(repository.deleteCustomProfile(renamed))
        assertEquals(ObservationProfile.WildlifeWatching, repository.activeProfile.value)
    }

    private fun clearPreferences() {
        context.getSharedPreferences("withrow_deerdeck_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
