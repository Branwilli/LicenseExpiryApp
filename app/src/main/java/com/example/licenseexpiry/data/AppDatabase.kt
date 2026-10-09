package com.example.licenseexpiry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// setup a room database while defining the schema of the database
@Database(
    entities = [Vehicle::class, LicenseEntry::class],
    version = 2, // bumped: added remoteId to Vehicle and LicenseEntry
    exportSchema = false
)

// Defines how the room builds and exposes the database
abstract class AppDatabase : RoomDatabase() {

    abstract fun licenseDao(): LicenseDao // Exposes and generate LicenseDao implementation to run queries

    // Ensures only one AppDatabase exists in memory.
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "license_expiry_db"
                )
                    // TODO: write a real Migration(1, 2) before shipping with
                    // existing users' data. Destructive is fine during
                    // early development since there's no user data to lose yet.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
