package com.wildtwins.deerdeck.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "species",
    indices = [Index(value = ["profileKey", "name"], unique = true)],
)
data class SpeciesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String = "wildlife_watching",
    val name: String,
    val color: Long,
    val emoji: String? = null,
    val sortOrder: Int,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
)
