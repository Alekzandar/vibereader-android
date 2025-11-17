package com.vibereader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, Word::class, Quote::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vibeReaderDao(): VibeReaderDao

    companion object {
        // Volatile ensures this variable is always up-to-date across all threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Return the existing instance if it's already created
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibe_reader_database"
                )
                    .fallbackToDestructiveMigration() // Simple migration for MVP
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}