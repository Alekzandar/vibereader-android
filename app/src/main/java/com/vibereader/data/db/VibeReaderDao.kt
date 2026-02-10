package com.vibereader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data model for session tiles (used in Book Detail view).
 */
data class SessionWithMetrics(
    @Embedded val session: Session,
    @ColumnInfo(name = "book_title") val bookTitle: String,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "quote_count") val quoteCount: Int
)

/**
 * Lightweight session summary for display in session pills.
 */
data class SessionSummary(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "quote_count") val quoteCount: Int
)

/**
 * Book with aggregated metrics for Library view.
 */
data class BookWithMetrics(
    @Embedded val book: Book,
    @ColumnInfo(name = "total_word_count") val totalWordCount: Int,
    @ColumnInfo(name = "total_quote_count") val totalQuoteCount: Int,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "most_recent_session_time") val mostRecentSessionTime: Long?
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
     * Synchronous active session check for TileService.
     */
    @Query("SELECT * FROM sessions WHERE status = 'active' LIMIT 1")
    suspend fun getActiveSessionSync(): Session?

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

    @Update
    suspend fun updateWord(word: Word)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: Quote)

    // --- Delete Queries ---
    @Delete
    suspend fun deleteWord(word: Word)

    @Delete
    suspend fun deleteQuote(quote: Quote)

    @Query("DELETE FROM words WHERE session_id = :sessionId AND definition LIKE '%not found%'")
    suspend fun deleteUndefinedWords(sessionId: Long): Int

    // --- Conversion Queries ---
    @Query("SELECT * FROM words WHERE word_id = :wordId")
    suspend fun getWordById(wordId: Long): Word?

    @Query("SELECT * FROM quotes WHERE quote_id = :quoteId")
    suspend fun getQuoteById(quoteId: Long): Quote?

    // --- Book-Level Queries (for Library restructure) ---

    /**
     * Get all books with aggregated metrics, sorted by most recent session.
     */
    @Query("""
        SELECT b.*,
            COALESCE((SELECT COUNT(*) FROM words WHERE book_id = b.book_id), 0) as total_word_count,
            COALESCE((SELECT COUNT(*) FROM quotes WHERE book_id = b.book_id), 0) as total_quote_count,
            COALESCE((SELECT COUNT(*) FROM sessions WHERE book_id = b.book_id AND status = 'inactive'), 0) as session_count,
            (SELECT MAX(end_time) FROM sessions WHERE book_id = b.book_id AND status = 'inactive') as most_recent_session_time
        FROM books b
        WHERE EXISTS (SELECT 1 FROM sessions WHERE book_id = b.book_id AND status = 'inactive')
        ORDER BY most_recent_session_time DESC
    """)
    fun getBooksWithMetrics(): Flow<List<BookWithMetrics>>

    /**
     * Get recent sessions for a specific book (for session pills).
     */
    @Query("""
        SELECT s.session_id, s.display_name, s.start_time, s.end_time,
            (SELECT COUNT(*) FROM words WHERE session_id = s.session_id) as word_count,
            (SELECT COUNT(*) FROM quotes WHERE session_id = s.session_id) as quote_count
        FROM sessions s
        WHERE s.book_id = :bookId AND s.status = 'inactive'
        ORDER BY s.start_time DESC
        LIMIT :limit
    """)
    fun getRecentSessionsForBook(bookId: Long, limit: Int = 5): Flow<List<SessionSummary>>

    /**
     * Get all sessions for a book (for Book Detail view).
     */
    @Query("""
        SELECT s.*, b.title as book_title,
            (SELECT COUNT(*) FROM words WHERE session_id = s.session_id) as word_count,
            (SELECT COUNT(*) FROM quotes WHERE session_id = s.session_id) as quote_count
        FROM sessions s
        JOIN books b ON s.book_id = b.book_id
        WHERE s.book_id = :bookId AND s.status = 'inactive'
        ORDER BY s.start_time DESC
    """)
    fun getSessionsForBook(bookId: Long): Flow<List<SessionWithMetrics>>

    /**
     * Get all words for a book (for Book Detail view).
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN sessions s ON w.session_id = s.session_id
        WHERE w.book_id = :bookId AND s.status = 'inactive'
        ORDER BY w.timestamp DESC
    """)
    fun getWordsForBook(bookId: Long): Flow<List<Word>>

    /**
     * Get all quotes for a book (for Book Detail view).
     */
    @Query("""
        SELECT q.* FROM quotes q
        JOIN sessions s ON q.session_id = s.session_id
        WHERE q.book_id = :bookId AND s.status = 'inactive'
        ORDER BY q.timestamp DESC
    """)
    fun getQuotesForBook(bookId: Long): Flow<List<Quote>>

    /**
     * Get a book by ID.
     */
    @Query("SELECT * FROM books WHERE book_id = :bookId")
    suspend fun getBookById(bookId: Long): Book?

    /**
     * Get session display name by ID.
     */
    @Query("SELECT display_name FROM sessions WHERE session_id = :sessionId")
    suspend fun getSessionDisplayName(sessionId: Long): String?

    // --- Weekly Vibe Queries (time-range based) ---

    /**
     * Get words captured within a time range.
     */
    @Query("""
        SELECT * FROM words
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getWordsInRange(startTime: Long, endTime: Long): List<Word>

    /**
     * Get quotes captured within a time range.
     */
    @Query("""
        SELECT * FROM quotes
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getQuotesInRange(startTime: Long, endTime: Long): List<Quote>

    /**
     * Get completed sessions within a time range.
     */
    @Query("""
        SELECT s.*, b.title as book_title,
            (SELECT COUNT(*) FROM words WHERE session_id = s.session_id) as word_count,
            (SELECT COUNT(*) FROM quotes WHERE session_id = s.session_id) as quote_count
        FROM sessions s
        JOIN books b ON s.book_id = b.book_id
        WHERE s.start_time BETWEEN :startTime AND :endTime
            AND s.status = 'inactive'
        ORDER BY s.start_time DESC
    """)
    suspend fun getSessionsInRange(startTime: Long, endTime: Long): List<SessionWithMetrics>

    /**
     * Get distinct book titles from sessions in a time range.
     */
    @Query("""
        SELECT DISTINCT b.title FROM sessions s
        JOIN books b ON s.book_id = b.book_id
        WHERE s.start_time BETWEEN :startTime AND :endTime
            AND s.status = 'inactive'
    """)
    suspend fun getBookTitlesInRange(startTime: Long, endTime: Long): List<String>
}