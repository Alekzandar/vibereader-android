package com.vibereader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val displayName: String, // e.g., "The Great Gatsby: Session 2"

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "status")
    val status: String = "active"
)

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

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false
)

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

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false
)