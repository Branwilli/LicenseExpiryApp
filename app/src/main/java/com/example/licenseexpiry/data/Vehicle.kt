package com.example.licenseexpiry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickName: String,
    val plateNumber: String,
    val ownerNotes: String? = null
)
