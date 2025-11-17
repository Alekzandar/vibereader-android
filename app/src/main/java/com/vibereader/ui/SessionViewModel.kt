package com.vibereader.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider // <-- MOVED THIS IMPORT HERE
import androidx.lifecycle.viewModelScope
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.AppDatabase
import com.vibereader.data.db.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.vibereader.data.db.Word
import com.vibereader.data.db.Quote

class SessionViewModel(
    private val db: AppDatabase
) : ViewModel() {

    // Observes the 'active' session from the database.
    // The UI will automatically update when this changes.
    val activeSession: StateFlow<Session?> = db.vibeReaderDao().getActiveSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Observes the list of all past sessions.
    val recentSessions: StateFlow<List<Session>> = db.vibeReaderDao().getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observes all saved words from the database.
    val allWords: StateFlow<List<Word>> = db.vibeReaderDao().getAllWords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observes all saved quotes from the database.
    val allQuotes: StateFlow<List<Quote>> = db.vibeReaderDao().getAllQuotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    fun startSession(context: Context, bookTitle: String) {
        viewModelScope.launch {
            // 1. Create a new session in the database
            val newSession = Session(
                bookTitle = bookTitle,
                startTime = System.currentTimeMillis(),
                status = "active"
            )
            db.vibeReaderDao().insertSession(newSession)

            // 2. Start the ForegroundService
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ReadingSessionService.ACTION_START_SESSION
                putExtra(ReadingSessionService.EXTRA_BOOK_TITLE, bookTitle)
            }
            context.startForegroundService(intent)
        }
    }

    fun stopSession(context: Context) {
        viewModelScope.launch {
            activeSession.value?.let { currentSession ->
                // 1. Update the session in the database
                db.vibeReaderDao().updateSessionStatus(
                    sessionId = currentSession.sessionId,
                    status = "inactive",
                    endTime = System.currentTimeMillis()
                )

                // 2. Stop the ForegroundService
                val intent = Intent(context, ReadingSessionService::class.java).apply {
                    action = ReadingSessionService.ACTION_STOP_SESSION
                }
                context.startService(intent) // Use startService to send a command
            }
        }
    }
}

// We'll need this ViewModelFactory to pass the database to the ViewModel
// Put this at the bottom of the same file
@Suppress("UNCHECKED_CAST")
class SessionViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}