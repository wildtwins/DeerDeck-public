package com.wildtwins.deerdeck.data.model

data class RecentObservation(
    val id: Long,
    val speciesName: String,
    val color: Long,
    val observedAt: Long,
    val quantity: Int,
    val notes: String?,
    val sex: String?,
    val ageClass: String?,
    val behavior: String?,
)
