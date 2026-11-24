package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 35.0,
    val addressHint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
