package com.vibereader.ui.session

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.*
import com.vibereader.ui.SpeechCaptureActivity
import java.text.SimpleDateFormat
import java.util.*

// ============================================================================
// Date Formatting Utilities
// ============================================================================

private fun formatSessionDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

private fun formatSessionTime(timestamp: Long): String {
    return SimpleDateFormat("h:mma", Locale.getDefault()).format(Date(timestamp)).lowercase()
}

private fun formatFullDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, h:mma", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDuration(startTime: Long, endTime: Long?): String {
    val duration = (endTime ?: System.currentTimeMillis()) - startTime
    val minutes = duration / 60000
    return if (minutes < 60) "${minutes} min" else "${minutes / 60}h ${minutes % 60}m"
}

// ============================================================================
// Start Session View
// ============================================================================

/**
 * The UI for starting a session.
 * Includes the title input and "Resume" chips for known books.
 */
@Composable
fun StartSessionView(onStart: (String) -> Unit, knownTitles: List<String>) {
    var title by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "What are you reading?",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Book Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (knownTitles.isNotEmpty()) {
            Text(
                "Resume reading:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                knownTitles.forEach { t ->
                    SuggestionChip(
                        onClick = { title = t },
                        label = { Text(t) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        Button(
            onClick = { onStart(title) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            enabled = title.isNotBlank()
        ) {
            Text("Start Reading")
        }
    }
}

// ============================================================================
// Active Session View
// ============================================================================

/**
 * The UI for an active session.
 * Features a prominent Smart Capture button with secondary Define/Quote options.
 */
@Composable
fun ActiveSessionView(sessionName: String, onEnd: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Currently Reading",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            sessionName,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )

        Text(
            "Use the lock screen controls, or tap below",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                val intent = Intent(context, SpeechCaptureActivity::class.java).apply {
                    action = ReadingSessionService.ACTION_SMART_CAPTURE
                }
                context.startActivity(intent)
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Mic, contentDescription = "Capture", modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(4.dp))
                Text("Capture", style = MaterialTheme.typography.labelMedium)
            }
        }

        Text(
            "Tap to speak",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, SpeechCaptureActivity::class.java).apply {
                        action = "ACTION_DEFINE"
                    }
                    context.startActivity(intent)
                }
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Define Word")
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(context, SpeechCaptureActivity::class.java).apply {
                        action = "ACTION_QUOTE"
                    }
                    context.startActivity(intent)
                }
            ) {
                Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Quote")
            }
        }

        Spacer(Modifier.height(48.dp))

        TextButton(
            onClick = onEnd,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("End Session")
        }
    }
}

// ============================================================================
// Library View (Book-centric, replaces old ArchiveListView)
// ============================================================================

/**
 * Library view showing books with their sessions as pills.
 * Tap book card → Book Detail; Tap session pill → Session Detail.
 */
@Composable
fun LibraryView(
    books: List<BookWithMetrics>,
    recentSessionsByBook: Map<Long, List<SessionSummary>>,
    onBookClick: (bookId: Long, bookTitle: String) -> Unit,
    onSessionClick: (sessionId: Long, sessionName: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                "Your Library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (books.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No books yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Text(
                            "Start a reading session to capture your first insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(books, key = { it.book.bookId }) { bookWithMetrics ->
            BookCard(
                book = bookWithMetrics,
                recentSessions = recentSessionsByBook[bookWithMetrics.book.bookId] ?: emptyList(),
                onBookClick = { onBookClick(bookWithMetrics.book.bookId, bookWithMetrics.book.title) },
                onSessionClick = onSessionClick
            )
        }
    }
}

/**
 * A card displaying a book with aggregated metrics and session pills.
 */
@Composable
fun BookCard(
    book: BookWithMetrics,
    recentSessions: List<SessionSummary>,
    onBookClick: () -> Unit,
    onSessionClick: (sessionId: Long, sessionName: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onBookClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row with metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    book.book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    MetricsBadge("${book.totalWordCount}W", Color(0xFFBBDEFB))
                    Spacer(Modifier.width(4.dp))
                    MetricsBadge("${book.totalQuoteCount}Q", Color(0xFFC8E6C9))
                }
            }

            // Session count subtitle
            Text(
                "${book.sessionCount} session${if (book.sessionCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Session pills row
            if (recentSessions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentSessions, key = { it.sessionId }) { session ->
                        SessionPill(
                            session = session,
                            onClick = { onSessionClick(session.sessionId, session.displayName) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A small pill showing session date/time.
 */
@Composable
fun SessionPill(
    session: SessionSummary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(72.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                formatSessionDate(session.startTime),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                formatSessionTime(session.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

// ============================================================================
// Book Detail View
// ============================================================================

/**
 * Detail view for a single book showing all sessions, words, and quotes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailView(
    bookTitle: String,
    sessions: List<SessionWithMetrics>,
    words: List<Word>,
    quotes: List<Quote>,
    onBack: () -> Unit,
    onSessionClick: (sessionId: Long, sessionName: String) -> Unit
) {
    val totalWords = words.size
    val totalQuotes = quotes.size

    // Map session IDs to display names for tagging
    val sessionNames = sessions.associate { it.session.sessionId to it.session.displayName }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
        )

        // Summary header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sessions.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Sessions", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalWords", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Words", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalQuotes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Quotes", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Sessions section
            item {
                Text(
                    "Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(sessions, key = { it.session.sessionId }) { sessionWithMetrics ->
                SessionRow(
                    session = sessionWithMetrics,
                    onClick = { onSessionClick(sessionWithMetrics.session.sessionId, sessionWithMetrics.session.displayName) }
                )
            }

            // Words section
            if (words.isNotEmpty()) {
                item {
                    Text(
                        "All Words ($totalWords)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }

                items(words.take(10), key = { it.wordId }) { word ->
                    WordRowWithSessionTag(
                        word = word,
                        sessionName = sessionNames[word.sessionId]?.substringAfterLast(": ") ?: ""
                    )
                }

                if (words.size > 10) {
                    item {
                        Text(
                            "... and ${words.size - 10} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Quotes section
            if (quotes.isNotEmpty()) {
                item {
                    Text(
                        "All Quotes ($totalQuotes)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }

                items(quotes.take(10), key = { it.quoteId }) { quote ->
                    QuoteRowWithSessionTag(
                        quote = quote,
                        sessionName = sessionNames[quote.sessionId]?.substringAfterLast(": ") ?: ""
                    )
                }

                if (quotes.size > 10) {
                    item {
                        Text(
                            "... and ${quotes.size - 10} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Bottom padding
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun SessionRow(
    session: SessionWithMetrics,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.session.displayName.substringAfterLast(": ").ifEmpty { "Session 1" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${formatFullDateTime(session.session.startTime)} • ${formatDuration(session.session.startTime, session.session.endTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Row {
                MetricsBadge("${session.wordCount}W", Color(0xFFBBDEFB))
                Spacer(Modifier.width(4.dp))
                MetricsBadge("${session.quoteCount}Q", Color(0xFFC8E6C9))
            }
        }
    }
}

@Composable
fun WordRowWithSessionTag(word: Word, sessionName: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Translate,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    if (sessionName.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                sessionName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    word.definition,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QuoteRowWithSessionTag(quote: Quote, sessionName: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.FormatQuote,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "\"${quote.content}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (sessionName.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            sessionName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Session Detail View (formerly ArchiveDetailView)
// ============================================================================

/**
 * Detail view for a single session's captures.
 * Supports swipe-to-delete (with confirmation) and swipe-to-convert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailView(
    sessionName: String,
    words: List<Word>,
    quotes: List<Quote>,
    onBack: () -> Unit,
    onDeleteWord: (Word) -> Unit,
    onDeleteQuote: (Quote) -> Unit,
    onDeleteUndefinedWords: () -> Unit,
    onConvertWordToQuote: (Word) -> Unit,
    onConvertQuoteToWord: (Quote) -> Unit
) {
    // Delete confirmation state
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteWord by remember { mutableStateOf<Word?>(null) }
    var pendingDeleteQuote by remember { mutableStateOf<Quote?>(null) }

    val hasUndefinedWords = words.any { isUndefinedWord(it) }
    val undefinedCount = words.count { isUndefinedWord(it) }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                pendingDeleteWord = null
                pendingDeleteQuote = null
            },
            title = { Text("Delete?") },
            text = {
                Text(
                    if (pendingDeleteWord != null) "Delete \"${pendingDeleteWord?.term}\"?"
                    else "Delete this quote?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteWord?.let { onDeleteWord(it) }
                        pendingDeleteQuote?.let { onDeleteQuote(it) }
                        showDeleteConfirmation = false
                        pendingDeleteWord = null
                        pendingDeleteQuote = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        pendingDeleteWord = null
                        pendingDeleteQuote = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(sessionName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
        )

        // Clear Undefined banner
        if (hasUndefinedWords) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$undefinedCount undefined word${if (undefinedCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(
                        onClick = onDeleteUndefinedWords,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }
            }
        }

        // Swipe hints
        Text(
            "Swipe left to delete • Swipe right to convert",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (words.isNotEmpty()) {
                item {
                    Text(
                        "Words (${words.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                items(words, key = { it.wordId }) { word ->
                    SwipeableWordItem(
                        word = word,
                        onDelete = {
                            pendingDeleteWord = word
                            showDeleteConfirmation = true
                        },
                        onConvert = { onConvertWordToQuote(word) }
                    )
                }
            }

            if (quotes.isNotEmpty()) {
                item {
                    Text(
                        "Quotes (${quotes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    )
                }
                items(quotes, key = { it.quoteId }) { quote ->
                    SwipeableQuoteItem(
                        quote = quote,
                        onDelete = {
                            pendingDeleteQuote = quote
                            showDeleteConfirmation = true
                        },
                        onConvert = { onConvertQuoteToWord(quote) }
                    )
                }
            }

            if (words.isEmpty() && quotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SpeakerNotesOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No captures in this session",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isUndefinedWord(word: Word): Boolean {
    return word.definition.contains("not found", ignoreCase = true) ||
            word.definition.contains("Definition not found", ignoreCase = true) ||
            word.definition.contains("Converted from quote", ignoreCase = true) ||
            word.definition.contains("tap to look up", ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableWordItem(
    word: Word,
    onDelete: () -> Unit,
    onConvert: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // Don't dismiss - wait for confirmation
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onConvert()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.targetValue
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiary
                    else -> Color.Transparent
                },
                label = "swipe-color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = "Convert to Quote", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Quote", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        val isUndefined = isUndefinedWord(word)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (isUndefined) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) else CardDefaults.cardColors()
        ) {
            ListItem(
                headlineContent = {
                    Text(word.term, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = {
                    Text(
                        word.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = if (isUndefined) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                },
                leadingContent = {
                    Icon(
                        if (isUndefined) Icons.Default.ErrorOutline else Icons.Default.Translate,
                        null,
                        tint = if (isUndefined) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableQuoteItem(
    quote: Quote,
    onDelete: () -> Unit,
    onConvert: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // Don't dismiss - wait for confirmation
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onConvert()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.targetValue
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                label = "swipe-color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Translate, contentDescription = "Convert to Word", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Define", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(
                        "\"${quote.content}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.FormatQuote,
                        null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }
}

// ============================================================================
// Utility Components
// ============================================================================

@Composable
fun MetricsBadge(text: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
