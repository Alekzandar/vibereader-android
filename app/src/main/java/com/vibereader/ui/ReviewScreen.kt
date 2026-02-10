package com.vibereader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.vibereader.data.db.SessionSummary
import kotlinx.coroutines.launch
import com.vibereader.ui.session.BookDetailView
import com.vibereader.ui.session.LibraryView
import com.vibereader.ui.session.SessionDetailView

/**
 * ReviewScreen handles the "Library" tab with 3-tier navigation:
 * 1. Book List (LibraryView) - shows all books with session pills
 * 2. Book Detail (BookDetailView) - shows single book with all sessions/words/quotes
 * 3. Session Detail (SessionDetailView) - shows single session's captures
 */
@Composable
fun ReviewScreen(viewModel: SessionViewModel) {
    // Navigation state
    val navState by viewModel.libraryNavState.collectAsState()
    val selectedBookTitle by viewModel.selectedBookTitle.collectAsState()
    val selectedSessionName by viewModel.selectedSessionName.collectAsState()

    // Book-level data
    val books by viewModel.booksWithMetrics.collectAsState()
    val sessionsForBook by viewModel.sessionsForSelectedBook.collectAsState()
    val wordsForBook by viewModel.wordsForSelectedBook.collectAsState()
    val quotesForBook by viewModel.quotesForSelectedBook.collectAsState()

    // Session-level data
    val wordsForSession by viewModel.wordsForSelectedSession.collectAsState()
    val quotesForSession by viewModel.quotesForSelectedSession.collectAsState()

    // Weekly Vibe state
    val weeklyVibe by viewModel.weeklyVibe.collectAsState()
    val isGeneratingVibe by viewModel.isGeneratingVibe.collectAsState()
    val vibeError by viewModel.vibeError.collectAsState()
    val canGenerateVibe by viewModel.canGenerateVibe.collectAsState()

    // Collect recent sessions for each book (for session pills)
    val recentSessionsByBook = remember { mutableStateMapOf<Long, List<SessionSummary>>() }

    // Load recent sessions for each book when books list changes
    // Each book gets its own coroutine so collections run in parallel
    LaunchedEffect(books) {
        books.forEach { bookWithMetrics ->
            launch {
                viewModel.database.vibeReaderDao()
                    .getRecentSessionsForBook(bookWithMetrics.book.bookId, 5)
                    .collect { sessions ->
                        recentSessionsByBook[bookWithMetrics.book.bookId] = sessions
                    }
            }
        }
    }

    when (navState) {
        LibraryNavState.BOOK_LIST -> {
            LibraryView(
                books = books,
                recentSessionsByBook = recentSessionsByBook,
                onBookClick = { bookId, bookTitle ->
                    viewModel.selectBook(bookId, bookTitle)
                },
                onSessionClick = { sessionId, sessionName ->
                    viewModel.selectSession(sessionId, sessionName)
                },
                weeklyVibe = weeklyVibe,
                isGeneratingVibe = isGeneratingVibe,
                vibeError = vibeError,
                canGenerateVibe = canGenerateVibe,
                onGenerateVibe = { viewModel.generateWeeklyVibe() },
                onSeedTestData = { viewModel.seedTestData() }
            )
        }

        LibraryNavState.BOOK_DETAIL -> {
            BookDetailView(
                bookTitle = selectedBookTitle ?: "Book",
                sessions = sessionsForBook,
                words = wordsForBook,
                quotes = quotesForBook,
                onBack = { viewModel.navigateBack() },
                onSessionClick = { sessionId, sessionName ->
                    viewModel.selectSession(sessionId, sessionName)
                }
            )
        }

        LibraryNavState.SESSION_DETAIL -> {
            SessionDetailView(
                sessionName = selectedSessionName ?: "Session",
                words = wordsForSession,
                quotes = quotesForSession,
                onBack = { viewModel.navigateBack() },
                onDeleteWord = { viewModel.deleteWord(it) },
                onDeleteQuote = { viewModel.deleteQuote(it) },
                onDeleteUndefinedWords = { viewModel.deleteUndefinedWordsInSession() },
                onConvertWordToQuote = { viewModel.convertWordToQuote(it) },
                onConvertQuoteToWord = { viewModel.convertQuoteToWord(it) },
                onRelookupWord = { viewModel.relookupWord(it) }
            )
        }
    }
}
