package com.vibereader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The high-level Book entity.
 * Sessions, Words, and Quotes are all linked back to a specific Book.
 */
@Entity(tableName = "books", indices = [Index(value = ["title"], unique = true)])
data class Book(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "book_id")
    val bookId: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A reading session linked to a Book.
 * Tracks start/end times and the 'active' status.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Session(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,

    @ColumnInfo(name = "book_id", index = true)
    val bookId: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "status")
    val status: String = "active"
)

/**
 * Vocabulary words captured during a session.
 */
@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(entity = Book::class, parentColumns = ["book_id"], childColumns = ["book_id"]),
        ForeignKey(entity = Session::class, parentColumns = ["session_id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Word(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "word_id")
    val wordId: Long = 0,

    @ColumnInfo(name = "book_id", index = true)
    val bookId: Long,

    @ColumnInfo(name = "session_id", index = true)
    val sessionId: Long,

    @ColumnInfo(name = "term")
    val term: String,

    @ColumnInfo(name = "definition")
    val definition: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)

/**
 * Quotes captured during a session.
 */
@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(entity = Book::class, parentColumns = ["book_id"], childColumns = ["book_id"]),
        ForeignKey(entity = Session::class, parentColumns = ["session_id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Quote(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "quote_id")
    val quoteId: Long = 0,

    @ColumnInfo(name = "book_id", index = true)
    val bookId: Long,

    @ColumnInfo(name = "session_id", index = true)
    val sessionId: Long,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)