package com.vibereader.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibereader.data.db.*

/**
 * The UI for starting a session.
 * Includes the title input and "Resume" chips for known books.
 */
@Composable
fun StartSessionView(onStart: (String) -> Unit, knownTitles: List<String>) {
    var title by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("What are you reading?", style = MaterialTheme.typography.headlineMedium)
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
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            enabled = title.isNotBlank()
        ) {
            Text("Start Flow")
        }
    }
}

/**
 * The UI for an active session.
 * Displays the current book name and the "End Session" action.
 */
@Composable
fun ActiveSessionView(sessionName: String, onEnd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Current Flow", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            sessionName,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onEnd,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
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
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "Your Library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailView(onBack: () -> Unit, words: List<Word>, quotes: List<Quote>) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Session Highlights") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        )

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (words.isNotEmpty()) {
                item { Text("Words", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                items(words) { word ->
                    ListItem(
                        headlineContent = { Text(word.term) },
                        supportingContent = { Text(word.definition) },
                        leadingContent = { Icon(Icons.Default.Translate, null) }
                    )
                }
            }

            if (quotes.isNotEmpty()) {
                item { Text("Quotes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                items(quotes) { quote ->
                    ListItem(
                        headlineContent = { Text("\"${quote.content}\"") },
                        leadingContent = { Icon(Icons.Default.FormatQuote, null) }
                    )
                }
            }

            if (words.isEmpty() && quotes.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No captures in this session.", color = Color.Gray)
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