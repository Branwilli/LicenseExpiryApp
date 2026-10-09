package com.example.licenseexpiry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "license_entries",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class LicenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val licenseType: String,       // "Motor License", "Insurance", etc.
    val expiryDateMillis: Long,    // epoch millis, midnight of expiry date
    val reminderDaysBefore: Int = 7,
    val emailNotified: Boolean = false,
    val alarmScheduled: Boolean = false,
    val remoteId: Long? = null
)
