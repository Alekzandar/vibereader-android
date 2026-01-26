package com.vibereader.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.vibereader.ui.session.ActiveSessionView
import com.vibereader.ui.session.StartSessionView

/**
 * SessionScreen handles the "Reading" tab.
 * It coordinates permissions and observes the relational active session.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel) {

    // --- 1. Permission Handling ---
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    // --- 2. State Collection ---
    val activeSession by viewModel.activeSession.collectAsState()
    val knownTitles by viewModel.knownBookTitles.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!permissionState.allPermissionsGranted) {
            // Show a screen to request permissions first
            PermissionRequestScreen(permissionState)
        } else {
            // --- 3. Relational UI Logic ---
            if (activeSession == null) {
                // If no session is active, show the "Start Session" UI
                // This component is located in com.vibereader.ui.session
                StartSessionView(
                    onStart = { title -> viewModel.startSession(title) },
                    knownTitles = knownTitles
                )
            } else {
                // If a session is active, show the "Active" UI
                // This component is located in com.vibereader.ui.session
                ActiveSessionView(
                    sessionName = activeSession!!.displayName,
                    onEnd = { viewModel.endSession() }
                )
            }
        }
    }
}

/**
 * Permission Request Screen
 * Ensures the user grants Microphone and Notification access for the Lock Screen flows.
 */
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