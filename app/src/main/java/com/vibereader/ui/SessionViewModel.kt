package com.vibereader.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * The central logic hub for Vibe Reader.
 * Extends AndroidViewModel to access the Application context internally,
 * allowing the UI to start/stop sessions without passing Context parameters.
 */
class SessionViewModel(
    application: Application,
    private val database: AppDatabase
) : AndroidViewModel(application) {

    private val dao = database.vibeReaderDao()

    // --- State Streams for the UI ---
    val activeSession = dao.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val archiveSessions = dao.getSessionsWithMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownBookTitles = dao.getAllBookTitles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Archive Selection State ---
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredWords = _selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.getWordsForSession(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredQuotes = _selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.getQuotesForSession(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Actions ---

    /**
     * Updates the current session selection for the Archive detail view.
     */
    fun selectArchiveSession(id: Long?) {
        _selectedSessionId.value = id
    }

    /**
     * Starts a reading session and the background service.
     * Logic: Auto-creates book if needed, auto-names session, starts Service.
     */
    fun startSession(title: String) {
        viewModelScope.launch {
            // 1. Get or Create Book
            val book = dao.getBookByTitle(title)
            val bookId = book?.bookId ?: dao.insertBook(Book(title = title))

            // 2. Auto-naming
            val count = dao.getSessionCountForBook(bookId)
            val displayName = if (count == 0) title else "$title: Session ${count + 1}"

            // 3. Create Session
            dao.insertSession(Session(
                bookId = bookId,
                displayName = displayName,
                startTime = System.currentTimeMillis(),
                status = "active"
            ))

            // 4. Trigger the background service using internal application context
            val context = getApplication<Application>()
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ReadingSessionService.ACTION_START
                putExtra(ReadingSessionService.EXTRA_BOOK_NAME, displayName)
            }
            context.startService(intent)
        }
    }

    /**
     * Ends the active session and stops the background service.
     */
    fun endSession() {
        val current = activeSession.value ?: return
        viewModelScope.launch {
            // 1. Update Database
            dao.updateSession(current.copy(
                status = "inactive",
                endTime = System.currentTimeMillis()
            ))

            // 2. Stop Service
            val context = getApplication<Application>()
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ReadingSessionService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Deletes a single word entry.
     */
    fun deleteWord(word: Word) {
        viewModelScope.launch {
            dao.deleteWord(word)
        }
    }

    /**
     * Deletes a single quote entry.
     */
    fun deleteQuote(quote: Quote) {
        viewModelScope.launch {
            dao.deleteQuote(quote)
        }
    }

    /**
     * Deletes all words with "not found" definitions in the current session.
     */
    fun deleteUndefinedWords() {
        val sessionId = _selectedSessionId.value ?: return
        viewModelScope.launch {
            dao.deleteUndefinedWords(sessionId)
        }
    }
}