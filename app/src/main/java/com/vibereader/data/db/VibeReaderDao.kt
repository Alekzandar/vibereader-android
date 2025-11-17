package com.vibereader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VibeReaderDao {

    // --- Session Functions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long // Returns the new session_id

    @Query("UPDATE sessions SET status = :status, end_time = :endTime WHERE session_id = :sessionId")
    suspend fun updateSessionStatus(sessionId: Long, status: String, endTime: Long?)

    @Query("SELECT * FROM sessions WHERE status = 'active' LIMIT 1")
    fun getActiveSession(): Flow<Session?> // Use Flow to observe changes

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<Session>>

    // --- Word Functions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word)

    @Query("SELECT * FROM words ORDER BY timestamp DESC")
    fun getAllWords(): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE session_id = :sessionId ORDER BY timestamp DESC")
    fun getWordsForSession(sessionId: Long): Flow<List<Word>>

    // --- Quote Functions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: Quote)

    @Query("SELECT * FROM quotes ORDER BY timestamp DESC")
    fun getAllQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quotes WHERE session_id = :sessionId ORDER BY timestamp DESC")
    fun getQuotesForSession(sessionId: Long): Flow<List<Quote>>
}