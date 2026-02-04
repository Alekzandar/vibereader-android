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
 * Navigation states for the Library tab.
 */
enum class LibraryNavState {
    BOOK_LIST,      // Library View - list of books
    BOOK_DETAIL,    // Book Detail View - single book expanded
    SESSION_DETAIL  // Session Detail View - single session's captures
}

/**
 * The central logic hub for Vibe Reader.
 * Extends AndroidViewModel to access the Application context internally,
 * allowing the UI to start/stop sessions without passing Context parameters.
 */
class SessionViewModel(
    application: Application,
    val database: AppDatabase  // Exposed for ReviewScreen to access DAO
) : AndroidViewModel(application) {

    private val dao = database.vibeReaderDao()

    // --- State Streams for the UI ---
    val activeSession = dao.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val knownBookTitles = dao.getAllBookTitles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Library Navigation State ---
    private val _libraryNavState = MutableStateFlow(LibraryNavState.BOOK_LIST)
    val libraryNavState = _libraryNavState.asStateFlow()

    // --- Book Selection State ---
    private val _selectedBookId = MutableStateFlow<Long?>(null)
    val selectedBookId = _selectedBookId.asStateFlow()

    private val _selectedBookTitle = MutableStateFlow<String?>(null)
    val selectedBookTitle = _selectedBookTitle.asStateFlow()

    // --- Session Selection State ---
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    private val _selectedSessionName = MutableStateFlow<String?>(null)
    val selectedSessionName = _selectedSessionName.asStateFlow()

    // --- Book-Level Data Flows ---
    val booksWithMetrics = dao.getBooksWithMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentSessionsForSelectedBook = _selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else dao.getRecentSessionsForBook(bookId, 5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessionsForSelectedBook = _selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else dao.getSessionsForBook(bookId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val wordsForSelectedBook = _selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else dao.getWordsForBook(bookId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val quotesForSelectedBook = _selectedBookId.flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else dao.getQuotesForBook(bookId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Session-Level Data Flows (for Session Detail) ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val wordsForSelectedSession = _selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.getWordsForSession(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val quotesForSelectedSession = _selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.getQuotesForSession(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation Actions ---

    /**
     * Select a book to view its details.
     */
    fun selectBook(bookId: Long?, bookTitle: String? = null) {
        _selectedBookId.value = bookId
        _selectedBookTitle.value = bookTitle
        _libraryNavState.value = if (bookId != null) LibraryNavState.BOOK_DETAIL else LibraryNavState.BOOK_LIST
    }

    /**
     * Select a session to view its captures.
     */
    fun selectSession(sessionId: Long?, sessionName: String? = null) {
        _selectedSessionId.value = sessionId
        _selectedSessionName.value = sessionName
        _libraryNavState.value = if (sessionId != null) LibraryNavState.SESSION_DETAIL else {
            // Go back to book detail if a book is selected, otherwise book list
            if (_selectedBookId.value != null) LibraryNavState.BOOK_DETAIL else LibraryNavState.BOOK_LIST
        }
    }

    /**
     * Navigate back one level in the library.
     */
    fun navigateBack() {
        when (_libraryNavState.value) {
            LibraryNavState.SESSION_DETAIL -> {
                _selectedSessionId.value = null
                _selectedSessionName.value = null
                _libraryNavState.value = if (_selectedBookId.value != null) {
                    LibraryNavState.BOOK_DETAIL
                } else {
                    LibraryNavState.BOOK_LIST
                }
            }
            LibraryNavState.BOOK_DETAIL -> {
                _selectedBookId.value = null
                _selectedBookTitle.value = null
                _libraryNavState.value = LibraryNavState.BOOK_LIST
            }
            LibraryNavState.BOOK_LIST -> {
                // Already at top level
            }
        }
    }

    /**
     * Reset library navigation to top level.
     */
    fun resetLibraryNavigation() {
        _selectedBookId.value = null
        _selectedBookTitle.value = null
        _selectedSessionId.value = null
        _selectedSessionName.value = null
        _libraryNavState.value = LibraryNavState.BOOK_LIST
    }

    // --- Session Management ---

    /**
     * Starts a reading session and the background service.
     */
    fun startSession(title: String) {
        viewModelScope.launch {
            val book = dao.getBookByTitle(title)
            val bookId = book?.bookId ?: dao.insertBook(Book(title = title))

            val count = dao.getSessionCountForBook(bookId)
            val displayName = if (count == 0) title else "$title: Session ${count + 1}"

            dao.insertSession(Session(
                bookId = bookId,
                displayName = displayName,
                startTime = System.currentTimeMillis(),
                status = "active"
            ))

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
            dao.updateSession(current.copy(
                status = "inactive",
                endTime = System.currentTimeMillis()
            ))

            val context = getApplication<Application>()
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ReadingSessionService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // --- Delete Operations ---

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
    fun deleteUndefinedWordsInSession() {
        val sessionId = _selectedSessionId.value ?: return
        viewModelScope.launch {
            dao.deleteUndefinedWords(sessionId)
        }
    }

    // --- Conversion Operations ---

    /**
     * Converts a word entry to a quote (keeps the term as quote content).
     */
    fun convertWordToQuote(word: Word) {
        viewModelScope.launch {
            try {
                dao.insertQuote(
                    Quote(
                        bookId = word.bookId,
                        sessionId = word.sessionId,
                        content = word.term,
                        timestamp = word.timestamp
                    )
                )
                dao.deleteWord(word)
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("SessionViewModel", "Failed to convert word to quote", e)
            }
        }
    }

    /**
     * Converts a quote entry to a word.
     * Saves with placeholder definition - needs lookup.
     */
    fun convertQuoteToWord(quote: Quote) {
        viewModelScope.launch {
            try {
                dao.insertWord(
                    Word(
                        bookId = quote.bookId,
                        sessionId = quote.sessionId,
                        term = quote.content,
                        definition = "Converted from quote - tap to look up",
                        timestamp = quote.timestamp
                    )
                )
                dao.deleteQuote(quote)
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("SessionViewModel", "Failed to convert quote to word", e)
            }
        }
    }

    // --- Utility ---

    /**
     * Get session display name for showing in detail view header.
     */
    fun loadSessionName(sessionId: Long) {
        viewModelScope.launch {
            val name = dao.getSessionDisplayName(sessionId)
            _selectedSessionName.value = name
        }
    }
}
