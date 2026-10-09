package com.wildtwins.deerdeck.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = ObservationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SpeciesEntity::class,
            parentColumns = ["id"],
            childColumns = ["speciesId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("speciesId"),
        Index("observedAt"),
    ],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val speciesId: Long,
    val observedAt: Long,
    val quantity: Int = 1,
    val notes: String? = null,
    val sex: String? = null,
    val ageClass: String? = null,
    val behavior: String? = null,
)
