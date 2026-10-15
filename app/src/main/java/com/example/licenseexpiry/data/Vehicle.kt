package com.example.licenseexpiry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val vehicleType: String = "Car",
    val vehicleYear: String = "2024",
    val plateNumber: String,
    val ownerNotes: String? = null,
    val remoteId: Long? = null
)
