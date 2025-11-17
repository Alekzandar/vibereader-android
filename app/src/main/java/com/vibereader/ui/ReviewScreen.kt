package com.vibereader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibereader.data.db.Quote
import com.vibereader.data.db.Word
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(viewModel: SessionViewModel) {
    // Collect the lists from the ViewModel
    val allWords by viewModel.allWords.collectAsState()
    val allQuotes by viewModel.allQuotes.collectAsState()

    // Combine and sort all items by timestamp
    val allItems = (allWords + allQuotes).sortedByDescending {
        when (it) {
            is Word -> it.timestamp
            is Quote -> it.timestamp
            else -> 0L
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No saved items yet.", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allItems) { item ->
                    when (item) {
                        is Word -> WordCard(word = item)
                        is Quote -> QuoteCard(quote = item)
                    }
                }
            }
        }
    }
}

// Composable for a single Word card (Wireframe 3)
@Composable
fun WordCard(word: Word) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = word.term,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = word.definition,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Defined on ${formatTimestamp(word.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

// Composable for a single Quote card (Wireframe 3)
@Composable
fun QuoteCard(quote: Quote) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "\"${quote.content}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Saved on ${formatTimestamp(quote.timestamp)}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// Helper function to format the timestamp
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(date)
}