package com.vibereader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data model for the Archive tiles.
 */
data class SessionWithMetrics(
    @Embedded val session: Session,
    @ColumnInfo(name = "book_title") val bookTitle: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "quote_count") val quoteCount: Int
)

@Dao
interface VibeReaderDao {

    // --- Book Queries ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: Book): Long

    @Query("SELECT * FROM books WHERE title = :title LIMIT 1")
    suspend fun getBookByTitle(title: String): Book?

    @Query("SELECT title FROM books ORDER BY title ASC")
    fun getAllBookTitles(): Flow<List<String>>

    // --- Session Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Query("SELECT COUNT(*) FROM sessions WHERE book_id = :bookId")
    suspend fun getSessionCountForBook(bookId: Long): Int

    @Query("SELECT * FROM sessions WHERE status = 'active' LIMIT 1")
    fun getActiveSession(): Flow<Session?>

    /**
     * The "Archive" Query: Fetches sessions with pre-calculated metrics.
     */
    @Query("""
        SELECT s.*, b.title as book_title,
        (SELECT COUNT(*) FROM words WHERE session_id = s.session_id) as word_count,
        (SELECT COUNT(*) FROM quotes WHERE session_id = s.session_id) as quote_count
        FROM sessions s 
        JOIN books b ON s.book_id = b.book_id
        WHERE s.status = 'inactive' 
        ORDER BY s.end_time DESC
    """)
    fun getSessionsWithMetrics(): Flow<List<SessionWithMetrics>>

    // --- Drill-down Queries ---
    @Query("SELECT * FROM words WHERE session_id = :sessionId ORDER BY timestamp DESC")
    fun getWordsForSession(sessionId: Long): Flow<List<Word>>

    @Query("SELECT * FROM quotes WHERE session_id = :sessionId ORDER BY timestamp DESC")
    fun getQuotesForSession(sessionId: Long): Flow<List<Quote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: Quote)

    // --- Delete Queries ---
    @Delete
    suspend fun deleteWord(word: Word)

    @Delete
    suspend fun deleteQuote(quote: Quote)

    @Query("DELETE FROM words WHERE session_id = :sessionId AND definition LIKE '%not found%'")
    suspend fun deleteUndefinedWords(sessionId: Long): Int
}