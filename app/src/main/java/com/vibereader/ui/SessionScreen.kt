package com.vibereader.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel) {

    // --- Permission Handling ---
    // We need Notification (Android 13+) and Record Audio permissions
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    // --- State Collection ---
    val context = LocalContext.current
    val activeSession by viewModel.activeSession.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()

    // This state will hold the book title for the dialog
    var showStartDialog by remember { mutableStateOf(false) }

    // --- UI Logic ---
    Surface(modifier = Modifier.fillMaxSize()) {
        if (!permissionState.allPermissionsGranted) {
            // Show a screen to request permissions first
            PermissionRequestScreen(permissionState)
        } else {
            // Permissions are granted, show the main UI
            val currentSession = activeSession
            if (currentSession == null) {
                // Screen 1: No Active Session
                NoSessionScreen(
                    onStartClick = { showStartDialog = true },
                    recentSessions = recentSessions
                )
            } else {
                // Screen 2: Active Session
                ActiveSessionScreen(
                    bookTitle = currentSession.bookTitle,
                    startTime = currentSession.startTime,
                    onStopClick = { viewModel.stopSession(context) }
                )
            }

            // --- Start Session Dialog ---
            if (showStartDialog) {
                StartSessionDialog(
                    onDismiss = { showStartDialog = false },
                    onStart = { bookTitle ->
                        viewModel.startSession(context, bookTitle)
                        showStartDialog = false
                    }
                )
            }
        }
    }
}

// --- Wireframe Screen 1 ---
@Composable
fun NoSessionScreen(
    onStartClick: () -> Unit,
    recentSessions: List<com.vibereader.data.db.Session>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Text("Start New Session", fontSize = 18.sp)
        }

        Text(
            "Recent Sessions:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(recentSessions.filter { it.status == "inactive" }) { session ->
                OutlinedCard(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = session.bookTitle,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Wireframe Screen 2 ---
@Composable
fun ActiveSessionScreen(
    bookTitle: String,
    startTime: Long,
    onStopClick: () -> Unit
) {
    // Timer Logic
    var timeElapsed by remember { mutableStateOf(System.currentTimeMillis() - startTime) }
    LaunchedEffect(Unit) {
        while (true) {
            timeElapsed = System.currentTimeMillis() - startTime
            kotlinx.coroutines.delay(1000)
        }
    }
    val hours = TimeUnit.MILLISECONDS.toHours(timeElapsed)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeElapsed) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeElapsed) % 60
    val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Now Reading:", style = MaterialTheme.typography.titleMedium)
        Text(bookTitle, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(timerText, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStopClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("End Session", fontSize = 18.sp)
        }
    }
}

// --- Dialog for starting a session ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionDialog(
    onDismiss: () -> Unit,
    onStart: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start new session") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter book title") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onStart(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- Composable for handling permissions ---
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionState: com.google.accompanist.permissions.MultiplePermissionsState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Vibe Reader needs a few permissions to work:\n\n" +
                    "• Notifications: To show the lock screen controls.\n" +
                    "• Microphone: To capture words and quotes.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
            Text("Grant Permissions")
        }
    }
}