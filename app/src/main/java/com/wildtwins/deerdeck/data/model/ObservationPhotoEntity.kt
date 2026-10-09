package com.wildtwins.deerdeck.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observation_photos",
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ObservationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("observationId"),
        Index("sessionId"),
        Index("capturedAt"),
    ],
)
data class ObservationPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val observationId: Long? = null,
    val sessionId: Long,
    val uri: String,
    val capturedAt: Long,
    val mediaType: String = "photo",
    val caption: String? = null,
)
