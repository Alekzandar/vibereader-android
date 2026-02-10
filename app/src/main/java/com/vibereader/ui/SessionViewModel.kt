package com.vibereader.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.*
import com.vibereader.data.network.RetrofitClient
import com.vibereader.data.network.WikipediaClient
import retrofit2.HttpException
import com.vibereader.data.models.WeeklyVibe
import com.vibereader.data.network.GeminiClient
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

    // --- Relookup Operations ---

    /**
     * Re-lookup definition for an undefined word.
     * Tries Dictionary API first, then Wikipedia as fallback.
     */
    fun relookupWord(word: Word) {
        viewModelScope.launch {
            val term = word.term
            val cleanTerm = term.lowercase().trim()

            // Try Dictionary API first (with and without articles)
            val termsToTry = listOf(
                cleanTerm,
                cleanTerm.removePrefix("the ").removePrefix("a ").removePrefix("an ")
            ).distinct()

            for (searchTerm in termsToTry) {
                try {
                    val response = RetrofitClient.instance.getDefinition(searchTerm)
                    val meaning = response.firstOrNull()?.meanings?.firstOrNull()
                    val def = meaning?.definitions?.firstOrNull()?.definition

                    if (def != null) {
                        val partOfSpeech = meaning.partOfSpeech ?: "unknown"
                        val formatted = "($partOfSpeech) $def"
                        dao.updateWord(word.copy(definition = formatted))
                        Log.d("SessionViewModel", "Relookup success for '$term': $formatted")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d("SessionViewModel", "Dictionary API failed for '$searchTerm': ${e.message}")
                }
            }

            // Fallback to Wikipedia API
            val wikiTermsToTry = termsToTry.map { it.replace(" ", "_") }

            for (wikiTerm in wikiTermsToTry) {
                try {
                    Log.d("SessionViewModel", "Trying Wikipedia for: $wikiTerm")
                    val wikiResponse = WikipediaClient.instance.getSummary(wikiTerm)
                    val summary = wikiResponse.description
                        ?: wikiResponse.extract.take(200) + if (wikiResponse.extract.length > 200) "..." else ""

                    val formatted = "(Wikipedia) $summary"
                    dao.updateWord(word.copy(definition = formatted))
                    Log.d("SessionViewModel", "Wikipedia success for '$term': $formatted")
                    return@launch
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        Log.d("SessionViewModel", "Wikipedia page not found for '$wikiTerm'")
                    } else {
                        Log.e("SessionViewModel", "Wikipedia API error for '$wikiTerm'", e)
                    }
                } catch (e: Exception) {
                    Log.e("SessionViewModel", "Wikipedia API failed for '$wikiTerm'", e)
                }
            }

            // All attempts failed - update with failure message
            dao.updateWord(word.copy(definition = "Definition not found for '$term'"))
            Log.d("SessionViewModel", "Relookup failed for '$term'")
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

    // --- Weekly Vibe (AI Insights) ---

    private val _weeklyVibe = MutableStateFlow<WeeklyVibe?>(null)
    val weeklyVibe: StateFlow<WeeklyVibe?> = _weeklyVibe.asStateFlow()

    private val _isGeneratingVibe = MutableStateFlow(false)
    val isGeneratingVibe: StateFlow<Boolean> = _isGeneratingVibe.asStateFlow()

    private val _vibeError = MutableStateFlow<String?>(null)
    val vibeError: StateFlow<String?> = _vibeError.asStateFlow()

    // Safeguard: track what data existed at last vibe generation
    private var lastVibeSessionCount: Int = 0
    private var lastVibeTimestamp: Long = 0L

    // Total session count flow for vibe generation check
    private val _totalSessionCount = MutableStateFlow(0)

    // Derived state: can generate vibe if new sessions exist since last generation
    val canGenerateVibe: StateFlow<Boolean> = combine(
        _totalSessionCount,
        _weeklyVibe
    ) { currentCount, vibe ->
        // Can generate if: never generated OR new sessions since last generation
        lastVibeTimestamp == 0L || currentCount > lastVibeSessionCount
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Generates a Weekly Vibe using Gemini AI based on captures from the last 7 days.
     */
    fun generateWeeklyVibe() {
        // Prevent multiple simultaneous requests
        if (_isGeneratingVibe.value) return

        viewModelScope.launch {
            _isGeneratingVibe.value = true
            _vibeError.value = null

            try {
                // Calculate week range (last 7 days)
                val now = System.currentTimeMillis()
                val weekAgo = now - (7 * 24 * 60 * 60 * 1000L)

                // Fetch data from the last week
                val words = dao.getWordsInRange(weekAgo, now)
                val quotes = dao.getQuotesInRange(weekAgo, now)
                val sessions = dao.getSessionsInRange(weekAgo, now)
                val bookTitles = dao.getBookTitlesInRange(weekAgo, now)

                // Update total session count for safeguard
                _totalSessionCount.value = sessions.size

                Log.d("SessionViewModel", "Generating vibe: ${words.size} words, ${quotes.size} quotes, ${sessions.size} sessions")

                // Call Gemini
                val result = GeminiClient.generateWeeklyVibe(
                    words = words,
                    quotes = quotes,
                    bookTitles = bookTitles,
                    sessionCount = sessions.size
                )

                result.onSuccess { vibeResponse ->
                    _weeklyVibe.value = WeeklyVibe(
                        generatedAt = now,
                        weekStart = weekAgo,
                        weekEnd = now,
                        vibeTitle = vibeResponse.vibeTitle,
                        vibeEmoji = vibeResponse.vibeEmoji,
                        themeTags = vibeResponse.themeTags,
                        insightNuggets = vibeResponse.insights,
                        wordSpotlight = vibeResponse.wordSpotlight,
                        quoteSpotlight = vibeResponse.quoteSpotlight,
                        wordCount = words.size,
                        quoteCount = quotes.size,
                        sessionCount = sessions.size,
                        bookTitles = bookTitles
                    )
                    // Update safeguard state on successful generation
                    lastVibeSessionCount = sessions.size
                    lastVibeTimestamp = now
                    Log.d("SessionViewModel", "Weekly vibe generated successfully")
                }

                result.onFailure { error ->
                    _vibeError.value = error.message ?: "Failed to generate vibe"
                    Log.e("SessionViewModel", "Vibe generation failed", error)
                }

            } catch (e: Exception) {
                _vibeError.value = e.message ?: "An unexpected error occurred"
                Log.e("SessionViewModel", "Vibe generation error", e)
            } finally {
                _isGeneratingVibe.value = false
            }
        }
    }

    /**
     * Clears the current weekly vibe and any error state.
     */
    fun clearWeeklyVibe() {
        _weeklyVibe.value = null
        _vibeError.value = null
    }

    // --- Debug / Seed Data ---

    /**
     * Seeds the database with test data for development/testing.
     * Creates sample books, sessions, words, and quotes from the past week.
     */
    fun seedTestData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L

            // Create test books
            val book1Id = dao.insertBook(Book(title = "Hyperion"))
            val book2Id = dao.insertBook(Book(title = "The Time Regulation Institute"))
            val book3Id = dao.insertBook(Book(title = "Dune"))

            // Use actual IDs (insertBook returns 0 if already exists due to IGNORE)
            val hyperionId = dao.getBookByTitle("Hyperion")?.bookId ?: book1Id
            val tanpinarId = dao.getBookByTitle("The Time Regulation Institute")?.bookId ?: book2Id
            val duneId = dao.getBookByTitle("Dune")?.bookId ?: book3Id

            // Create sessions from past week
            val session1Id = dao.insertSession(Session(
                bookId = hyperionId,
                displayName = "Hyperion: Session 1",
                startTime = now - (6 * dayMs),
                endTime = now - (6 * dayMs) + (45 * 60 * 1000),
                status = "inactive"
            ))

            val session2Id = dao.insertSession(Session(
                bookId = hyperionId,
                displayName = "Hyperion: Session 2",
                startTime = now - (4 * dayMs),
                endTime = now - (4 * dayMs) + (60 * 60 * 1000),
                status = "inactive"
            ))

            val session3Id = dao.insertSession(Session(
                bookId = tanpinarId,
                displayName = "The Time Regulation Institute",
                startTime = now - (3 * dayMs),
                endTime = now - (3 * dayMs) + (30 * 60 * 1000),
                status = "inactive"
            ))

            val session4Id = dao.insertSession(Session(
                bookId = duneId,
                displayName = "Dune: Session 1",
                startTime = now - (1 * dayMs),
                endTime = now - (1 * dayMs) + (90 * 60 * 1000),
                status = "inactive"
            ))

            // Seed words
            val testWords = listOf(
                Triple(hyperionId, session1Id, Triple("hegemony", "(noun) leadership or dominance, especially by one country or social group over others", now - (6 * dayMs))),
                Triple(hyperionId, session1Id, Triple("aeolian", "(adjective) relating to or arising from the action of the wind", now - (6 * dayMs) + 1000)),
                Triple(hyperionId, session1Id, Triple("argot", "(noun) the jargon or slang of a particular group or class", now - (6 * dayMs) + 2000)),
                Triple(hyperionId, session2Id, Triple("geodesic", "(adjective) of or relating to the shortest possible line between two points on a sphere", now - (4 * dayMs))),
                Triple(hyperionId, session2Id, Triple("tussock", "(noun) a compact tuft of grass or sedge", now - (4 * dayMs) + 1000)),
                Triple(tanpinarId, session3Id, Triple("sincere", "(adjective) free from pretense or deceit; genuine", now - (3 * dayMs))),
                Triple(tanpinarId, session3Id, Triple("bureaucracy", "(noun) a system of government in which most decisions are made by state officials", now - (3 * dayMs) + 1000)),
                Triple(tanpinarId, session3Id, Triple("melancholy", "(noun) a deep, pensive sadness", now - (3 * dayMs) + 2000)),
                Triple(duneId, session4Id, Triple("prescient", "(adjective) having or showing knowledge of events before they take place", now - (1 * dayMs))),
                Triple(duneId, session4Id, Triple("sietch", "(noun) a Fremen cave dwelling or community", now - (1 * dayMs) + 1000)),
                Triple(duneId, session4Id, Triple("gom jabbar", "(noun) a specific poison needle tipped with meta-cyanide", now - (1 * dayMs) + 2000)),
            )

            testWords.forEach { (bookId, sessionId, wordData) ->
                dao.insertWord(Word(
                    bookId = bookId,
                    sessionId = sessionId,
                    term = wordData.first,
                    definition = wordData.second,
                    timestamp = wordData.third
                ))
            }

            // Seed quotes
            val testQuotes = listOf(
                Triple(hyperionId, session1Id, Pair("The difference between the right word and the almost right word is the difference between lightning and the lightning bug.", now - (6 * dayMs) + 5000)),
                Triple(hyperionId, session1Id, Pair("History viewed from the inside is always a dark, digestive mess, far different from the easily recognizable cow viewed from afar by historians.", now - (6 * dayMs) + 6000)),
                Triple(hyperionId, session2Id, Pair("You just know more about what is, what isn't, and how little time there is to learn the difference.", now - (4 * dayMs) + 5000)),
                Triple(tanpinarId, session3Id, Pair("Why write at all if you cannot say honestly what you mean?", now - (3 * dayMs) + 5000)),
                Triple(tanpinarId, session3Id, Pair("Sometimes I consider just what strange creatures we are: we bemoan the brevity of our lives but do everything in our power to squander the day.", now - (3 * dayMs) + 6000)),
                Triple(tanpinarId, session3Id, Pair("Mankind's hell is mankind.", now - (3 * dayMs) + 7000)),
                Triple(duneId, session4Id, Pair("I must not fear. Fear is the mind-killer. Fear is the little-death that brings total obliteration.", now - (1 * dayMs) + 5000)),
                Triple(duneId, session4Id, Pair("The mystery of life isn't a problem to solve, but a reality to experience.", now - (1 * dayMs) + 6000)),
            )

            testQuotes.forEach { (bookId, sessionId, quoteData) ->
                dao.insertQuote(Quote(
                    bookId = bookId,
                    sessionId = sessionId,
                    content = quoteData.first,
                    timestamp = quoteData.second
                ))
            }

            Log.d("SessionViewModel", "Seed data inserted: 3 books, 4 sessions, ${testWords.size} words, ${testQuotes.size} quotes")
        }
    }
}
