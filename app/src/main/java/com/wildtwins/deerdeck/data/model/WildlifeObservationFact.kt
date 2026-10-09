package com.wildtwins.deerdeck.data.model

data class WildlifeObservationFact(
    val observationId: Long,
    val speciesId: Long,
    val wildlifeName: String,
    val color: Long,
    val sessionId: Long,
    val observedAt: Long,
    val quantity: Int,
)
