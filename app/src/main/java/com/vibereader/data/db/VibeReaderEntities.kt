package com.vibereader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,

    @ColumnInfo(name = "book_title")
    val bookTitle: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long, // Unix Timestamp

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    // 'active' or 'inactive' as you specified
    @ColumnInfo(name = "status")
    val status: String = "active"
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE // If a session is deleted, delete its words
        )
    ]
)
data class Word(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "word_id")
    val wordId: Long = 0,

    @ColumnInfo(name = "session_id", index = true) // Index for faster lookups
    val sessionId: Long,

    @ColumnInfo(name = "term")
    val term: String,

    @ColumnInfo(name = "definition")
    val definition: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_favorite", defaultValue = "0") // 0 = false, 1 = true
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE // If a session is deleted, delete its quotes
        )
    ]
)
data class Quote(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "quote_id")
    val quoteId: Long = 0,

    @ColumnInfo(name = "session_id", index = true) // Index for faster lookups
    val sessionId: Long,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false
)