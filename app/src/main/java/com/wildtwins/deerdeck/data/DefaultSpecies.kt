package com.wildtwins.deerdeck.data

import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.ObservationProfileKind

object DefaultSpecies {
    val removedNames = setOf("Bird", "Squirrel", "Deer", "FAE N", "Rabbit", "Other")

    val wildlife = listOf(
        SpeciesEntity(name = "Fawn", color = 0xFF0072B2, sortOrder = 1, isDefault = true),
        SpeciesEntity(name = "Buck", color = 0xFFD55E00, sortOrder = 2, isDefault = true),
        SpeciesEntity(name = "Doe", color = 0xFF009E73, sortOrder = 3, isDefault = true),
        SpeciesEntity(name = "Turkey", color = 0xFF6A3D9A, sortOrder = 4, isDefault = true),
        SpeciesEntity(name = "Pheasant", color = 0xFFE69F00, sortOrder = 5, isDefault = true),
        SpeciesEntity(name = "Skunk", color = 0xFF8B1E3F, sortOrder = 6, isDefault = true),
        SpeciesEntity(name = "Fox", color = 0xFFCC79A7, sortOrder = 7, isDefault = true),
        SpeciesEntity(name = "Raccoon", color = 0xFF56B4E9, sortOrder = 8, isDefault = true),
        SpeciesEntity(name = "Opossum", color = 0xFF5A5A5A, sortOrder = 9, isDefault = true),
        SpeciesEntity(name = "Coyote", color = 0xFF386641, sortOrder = 10, isDefault = true),
    )

    val birds = listOf(
        SpeciesEntity(name = "Cardinal", color = 0xFFD55E00, sortOrder = 1, isDefault = true),
        SpeciesEntity(name = "Blue Jay", color = 0xFF0072B2, sortOrder = 2, isDefault = true),
        SpeciesEntity(name = "Robin", color = 0xFFCC79A7, sortOrder = 3, isDefault = true),
        SpeciesEntity(name = "Woodpecker", color = 0xFFE69F00, sortOrder = 4, isDefault = true),
        SpeciesEntity(name = "Crow", color = 0xFF5A5A5A, sortOrder = 5, isDefault = true),
        SpeciesEntity(name = "Hawk", color = 0xFF8B1E3F, sortOrder = 6, isDefault = true),
        SpeciesEntity(name = "Owl", color = 0xFF6A3D9A, sortOrder = 7, isDefault = true),
        SpeciesEntity(name = "Finch", color = 0xFF009E73, sortOrder = 8, isDefault = true),
        SpeciesEntity(name = "Sparrow", color = 0xFF405A7A, sortOrder = 9, isDefault = true),
        SpeciesEntity(name = "Turkey", color = 0xFF386641, sortOrder = 10, isDefault = true),
    )

    val all = wildlife

    fun forProfile(profile: ObservationProfile): List<SpeciesEntity> {
        return when (profile.kind) {
            ObservationProfileKind.Wildlife -> wildlife
            ObservationProfileKind.Birds -> birds
            ObservationProfileKind.Custom -> emptyList()
        }
    }
}
