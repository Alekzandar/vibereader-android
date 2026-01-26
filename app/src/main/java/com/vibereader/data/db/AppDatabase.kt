package com.vibereader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room Database for Vibe Reader.
 * Version 1 includes the relational entities: Book, Session, Word, and Quote.
 */
@Database(entities = [Book::class, Session::class, Word::class, Quote::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vibeReaderDao(): VibeReaderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of the database.
         * Using "vibe_reader_db" to ensure consistency across the UI and Background Service.
         * destructiveMigration is enabled to handle the transition to the relational schema.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibe_reader_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}