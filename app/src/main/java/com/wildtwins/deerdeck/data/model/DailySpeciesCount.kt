package com.wildtwins.deerdeck.data.model

data class DailySpeciesCount(
    val speciesId: Long,
    val speciesName: String,
    val color: Long,
    val dayIndex: Int,
    val total: Int,
)
