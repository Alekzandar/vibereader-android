package com.vibereader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibereader.data.db.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Handles the logic for starting/ending relational sessions and
 * providing the metrics for the Archive.
 */
class SessionViewModel(private val database: AppDatabase) : ViewModel() {
    private val dao = database.vibeReaderDao()

    // --- State Streams ---
    val activeSession = dao.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val archiveSessions = dao.getSessionsWithMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownBookTitles = dao.getAllBookTitles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Drill-down State (for Archive) ---
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

    fun selectArchiveSession(id: Long?) { _selectedSessionId.value = id }

    fun startSession(title: String) {
        viewModelScope.launch {
            // 1. Relational Check: Create book if it doesn't exist
            val book = dao.getBookByTitle(title)
            val bookId = book?.bookId ?: dao.insertBook(Book(title = title))

            // 2. Auto-naming: e.g., "The Book: Session 2"
            val count = dao.getSessionCountForBook(bookId)
            val displayName = if (count == 0) title else "$title: Session ${count + 1}"

            // 3. Start Session
            dao.insertSession(Session(
                bookId = bookId,
                displayName = displayName,
                startTime = System.currentTimeMillis()
            ))
        }
    }

    fun endSession() {
        viewModelScope.launch {
            val current = activeSession.value ?: return@launch
            dao.updateSession(current.copy(
                status = "inactive",
                endTime = System.currentTimeMillis()
            ))
        }
    }
}