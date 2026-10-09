package com.wildtwins.deerdeck.data

import com.wildtwins.deerdeck.data.model.SpeciesEntity
import com.wildtwins.deerdeck.data.settings.ObservationProfile
import com.wildtwins.deerdeck.data.settings.ObservationProfileKind

data class RegionalSpeciesDefaults(
    val regionLabel: String,
    val wildlife: List<String>,
    val birds: List<String>,
)

object LocationAwareSpeciesDefaults {
    private val colors = listOf(
        0xFF0072B2,
        0xFFD55E00,
        0xFF009E73,
        0xFF6A3D9A,
        0xFFE69F00,
        0xFF8B1E3F,
        0xFFCC79A7,
        0xFF56B4E9,
        0xFF5A5A5A,
        0xFF386641,
        0xFF7A5195,
        0xFFEF5675,
    )

    fun forProfile(
        profile: ObservationProfile,
        latitude: Double,
        longitude: Double,
    ): Pair<String, List<SpeciesEntity>> {
        val defaults = resolve(latitude, longitude)
        val names = when (profile.kind) {
            ObservationProfileKind.Wildlife -> defaults.wildlife
            ObservationProfileKind.Birds -> defaults.birds
            ObservationProfileKind.Custom -> emptyList()
        }
        return defaults.regionLabel to names.mapIndexed { index, name ->
            SpeciesEntity(
                name = name,
                color = colors[index % colors.size],
                sortOrder = index + 1,
                isDefault = true,
            )
        }
    }

    fun resolve(latitude: Double, longitude: Double): RegionalSpeciesDefaults {
        return when {
            latitude >= 51.0 && longitude <= -130.0 -> alaska
            latitude in 18.0..23.0 && longitude in -161.0..-154.0 -> hawaii
            longitude <= -116.0 && latitude >= 42.0 -> pacificNorthwest
            longitude <= -103.0 && latitude < 38.0 -> southwest
            longitude <= -103.0 -> mountainWest
            longitude <= -95.0 -> greatPlains
            latitude < 36.5 && longitude > -103.0 -> southeast
            longitude > -80.0 && latitude >= 36.5 -> northeast
            latitude in 36.0..50.5 && longitude in -97.5..-80.0 -> greatLakesMidwest
            else -> northAmerica
        }
    }

    private val greatLakesMidwest = RegionalSpeciesDefaults(
        regionLabel = "Great Lakes and Midwest",
        wildlife = listOf(
            "Fawn", "Buck", "Doe", "Turkey", "Raccoon", "Coyote", "Red Fox", "Skunk", "Opossum", "Groundhog",
        ),
        birds = listOf(
            "Cardinal", "Blue Jay", "Robin", "Chickadee", "Mourning Dove",
            "Downy Woodpecker", "Crow", "Goldfinch", "Red-tailed Hawk", "Wild Turkey",
        ),
    )

    private val northeast = RegionalSpeciesDefaults(
        regionLabel = "Northeast",
        wildlife = listOf(
            "Fawn", "Buck", "Doe", "Black Bear", "Turkey", "Raccoon", "Red Fox", "Coyote", "Groundhog", "Bobcat",
        ),
        birds = listOf(
            "Cardinal", "Blue Jay", "Black-capped Chickadee", "Robin", "Mourning Dove",
            "Tufted Titmouse", "Downy Woodpecker", "Crow", "Goldfinch", "Red-tailed Hawk",
        ),
    )

    private val southeast = RegionalSpeciesDefaults(
        regionLabel = "Southeast",
        wildlife = listOf(
            "Fawn", "Buck", "Doe", "Turkey", "Raccoon", "Opossum", "Armadillo", "Coyote", "Bobcat", "Gray Fox",
        ),
        birds = listOf(
            "Cardinal", "Carolina Wren", "Blue Jay", "Mockingbird", "Mourning Dove",
            "Red-bellied Woodpecker", "Carolina Chickadee", "Crow", "Brown Thrasher", "Wild Turkey",
        ),
    )

    private val greatPlains = RegionalSpeciesDefaults(
        regionLabel = "Great Plains",
        wildlife = listOf(
            "Fawn", "Buck", "Doe", "Pronghorn", "Coyote", "Raccoon", "Skunk", "Red Fox", "Prairie Dog", "Badger",
        ),
        birds = listOf(
            "Western Meadowlark", "Red-winged Blackbird", "Mourning Dove", "American Kestrel", "Red-tailed Hawk",
            "Killdeer", "Horned Lark", "Crow", "Wild Turkey", "Great Blue Heron",
        ),
    )

    private val mountainWest = RegionalSpeciesDefaults(
        regionLabel = "Mountain West",
        wildlife = listOf(
            "Mule Deer", "Elk", "Pronghorn", "Moose", "Black Bear", "Coyote", "Mountain Lion", "Bobcat", "Beaver", "Porcupine",
        ),
        birds = listOf(
            "Black-billed Magpie", "Mountain Chickadee", "Steller's Jay", "Clark's Nutcracker", "Red-tailed Hawk",
            "Golden Eagle", "American Kestrel", "Dark-eyed Junco", "Raven", "Wild Turkey",
        ),
    )

    private val southwest = RegionalSpeciesDefaults(
        regionLabel = "Southwest",
        wildlife = listOf(
            "Mule Deer", "Coyote", "Javelina", "Bobcat", "Jackrabbit", "Desert Cottontail", "Raccoon", "Gray Fox", "Pronghorn", "Elk",
        ),
        birds = listOf(
            "Gambel's Quail", "Roadrunner", "Cactus Wren", "Curve-billed Thrasher", "Verdin",
            "Mourning Dove", "Red-tailed Hawk", "Great Horned Owl", "Raven", "House Finch",
        ),
    )

    private val pacificNorthwest = RegionalSpeciesDefaults(
        regionLabel = "Pacific Northwest",
        wildlife = listOf(
            "Black-tailed Deer", "Elk", "Black Bear", "Coyote", "Raccoon", "River Otter", "Beaver", "Bobcat", "Red Fox", "Porcupine",
        ),
        birds = listOf(
            "Steller's Jay", "American Robin", "Black-capped Chickadee", "Dark-eyed Junco", "Northern Flicker",
            "Crow", "Raven", "Bald Eagle", "Great Blue Heron", "Anna's Hummingbird",
        ),
    )

    private val alaska = RegionalSpeciesDefaults(
        regionLabel = "Alaska",
        wildlife = listOf(
            "Moose", "Caribou", "Black Bear", "Brown Bear", "Dall Sheep", "Red Fox", "Beaver", "Snowshoe Hare", "Porcupine", "Lynx",
        ),
        birds = listOf(
            "Bald Eagle", "Common Raven", "Black-billed Magpie", "Willow Ptarmigan", "Black-capped Chickadee",
            "Gray Jay", "Mallard", "Canada Goose", "Great Horned Owl", "Sandhill Crane",
        ),
    )

    private val hawaii = RegionalSpeciesDefaults(
        regionLabel = "Hawaii",
        wildlife = listOf(
            "Axis Deer", "Feral Pig", "Hawaiian Hoary Bat", "Mongoose", "Feral Goat", "Feral Sheep", "Wallaby", "Hawaiian Monk Seal",
        ),
        birds = listOf(
            "Hawaiian Goose", "Northern Cardinal", "Common Myna", "Zebra Dove", "Red-crested Cardinal",
            "White-rumped Shama", "Pacific Golden-Plover", "Hawaiian Hawk", "Java Sparrow", "White Tern",
        ),
    )

    private val northAmerica = RegionalSpeciesDefaults(
        regionLabel = "North America",
        wildlife = DefaultSpecies.wildlife.map { it.name },
        birds = DefaultSpecies.birds.map { it.name },
    )
}
