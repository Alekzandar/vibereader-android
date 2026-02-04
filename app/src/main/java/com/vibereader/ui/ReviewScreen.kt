package com.vibereader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vibereader.ui.session.ArchiveListView
import com.vibereader.ui.session.ArchiveDetailView

/**
 * ReviewScreen handles the "Library" tab logic.
 * It uses the ViewModel to toggle between the list of past sessions
 * and the "drill-down" detail view of a specific session's captures.
 */
@Composable
fun ReviewScreen(viewModel: SessionViewModel) {
    // 1. Observe the list of all past sessions (with their W/Q metrics)
    val sessions by viewModel.archiveSessions.collectAsState()

    // 2. Observe if a specific session is currently selected for drill-down
    val drillDownId by viewModel.selectedSessionId.collectAsState()

    // 3. Observe the filtered words and quotes for the selected session
    val words by viewModel.filteredWords.collectAsState()
    val quotes by viewModel.filteredQuotes.collectAsState()

    if (drillDownId == null) {
        // If no session is selected, show the high-level list of all books/sessions
        ArchiveListView(
            sessions = sessions,
            onSelect = { id -> viewModel.selectArchiveSession(id) }
        )
    } else {
        // If a session is selected, show the "Highlights" (Words & Quotes) for that session
        ArchiveDetailView(
            onBack = { viewModel.selectArchiveSession(null) },
            words = words,
            quotes = quotes,
            onDeleteWord = { viewModel.deleteWord(it) },
            onDeleteQuote = { viewModel.deleteQuote(it) },
            onDeleteUndefinedWords = { viewModel.deleteUndefinedWords() },
            onConvertWordToQuote = { viewModel.convertWordToQuote(it) },
            onConvertQuoteToWord = { viewModel.convertQuoteToWord(it) }
        )
    }
}