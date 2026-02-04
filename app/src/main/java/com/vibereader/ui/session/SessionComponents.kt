package com.vibereader.ui.session

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.*
import com.vibereader.ui.SpeechCaptureActivity

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
        // Session info
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

        // --- Primary: Smart Capture Button ---
        Button(
            onClick = {
                val intent = Intent(context, SpeechCaptureActivity::class.java).apply {
                    action = ReadingSessionService.ACTION_SMART_CAPTURE
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Capture",
                    modifier = Modifier.size(36.dp)
                )
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

        // --- Secondary: Explicit Define/Quote Buttons ---
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

        // --- End Session ---
        TextButton(
            onClick = onEnd,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("End Session")
        }
    }
}

/**
 * The Library/Archive list view.
 * Displays sessions with their associated word/quote counts.
 */
@Composable
fun ArchiveListView(sessions: List<SessionWithMetrics>, onSelect: (Long) -> Unit) {
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

        if (sessions.isEmpty()) {
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
                            "No reading sessions yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Text(
                            "Start a session to capture your first insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        items(sessions) { metrics ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSelect(metrics.session.sessionId) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            metrics.session.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        MetricsBadge("${metrics.wordCount}W", Color(0xFFBBDEFB))
                        Spacer(Modifier.width(4.dp))
                        MetricsBadge("${metrics.quoteCount}Q", Color(0xFFC8E6C9))
                    }
                    val duration = (metrics.session.endTime ?: System.currentTimeMillis()) - metrics.session.startTime
                    Text(
                        "${duration / 60000} min read • ${metrics.bookTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * The drill-down view for a specific session.
 * Lists all words and quotes captured during that specific reading period.
 * Supports swipe-to-delete and bulk delete for undefined words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailView(
    onBack: () -> Unit,
    words: List<Word>,
    quotes: List<Quote>,
    onDeleteWord: (Word) -> Unit = {},
    onDeleteQuote: (Quote) -> Unit = {},
    onDeleteUndefinedWords: () -> Unit = {}
) {
    val hasUndefinedWords = words.any { it.definition.contains("not found", ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Session Highlights") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                if (hasUndefinedWords) {
                    TextButton(
                        onClick = onDeleteUndefinedWords,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Undefined")
                    }
                }
            }
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
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                onDeleteWord(word)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                                label = "swipe-color"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        val isUndefined = word.definition.contains("not found", ignoreCase = true)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isUndefined) CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ) else CardDefaults.cardColors()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        word.term,
                                        style = MaterialTheme.typography.titleMedium
                                    )
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
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                onDeleteQuote(quote)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                                label = "swipe-color"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
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
