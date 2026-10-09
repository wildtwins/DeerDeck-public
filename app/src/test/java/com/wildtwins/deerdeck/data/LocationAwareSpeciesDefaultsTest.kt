package com.wildtwins.deerdeck.data

import com.wildtwins.deerdeck.data.settings.ObservationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAwareSpeciesDefaultsTest {
    @Test
    fun midwestCoordinatesLoadRegionalWildlife() {
        val (region, wildlife) = LocationAwareSpeciesDefaults.forProfile(
            profile = ObservationProfile.WildlifeWatching,
            latitude = 44.98,
            longitude = -93.27,
        )

        assertEquals("Great Lakes and Midwest", region)
        assertEquals(listOf("Fawn", "Buck", "Doe"), wildlife.take(3).map { it.name })
        assertTrue(wildlife.any { it.name == "Groundhog" })
    }

    @Test
    fun southwestCoordinatesLoadBirdsOnlyForBirdProfile() {
        val (region, birds) = LocationAwareSpeciesDefaults.forProfile(
            profile = ObservationProfile.BirdWatching,
            latitude = 33.45,
            longitude = -112.07,
        )

        assertEquals("Southwest", region)
        assertTrue(birds.any { it.name == "Roadrunner" })
        assertTrue(birds.none { it.name == "Mule Deer" })
    }

    @Test
    fun customProfilesNeverReceiveRegionalDefaults() {
        val (_, wildlife) = LocationAwareSpeciesDefaults.forProfile(
            profile = ObservationProfile.custom("custom_test", "Backyard"),
            latitude = 44.98,
            longitude = -93.27,
        )

        assertTrue(wildlife.isEmpty())
    }
}
