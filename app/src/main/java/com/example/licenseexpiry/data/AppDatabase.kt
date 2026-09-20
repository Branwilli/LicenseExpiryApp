package com.example.licenseexpiry.data 

import android.content.Context
import androidx.room.AppDatabase
import adroidx.room.Room
import androidx.room.RoomDatabase

// Set's up the room and dictates the tables, schema version, and how to manage the room
@Database(
    entities = [Vechile::class, LicenseEntry::class],
    version = 1, // Should increase whenever there is a change in the database
    exportSchema = false
)
// Serves as the blueprint 
abstract class AppDatabase: RoomDatabase() {
    abstract fun licenseDao(): LicenseDao // Provides a way for rest of app to access DAO

    // Defines the functions and properties that belong to the class
    companion object {
        @Volatile 
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "license_expiry_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
