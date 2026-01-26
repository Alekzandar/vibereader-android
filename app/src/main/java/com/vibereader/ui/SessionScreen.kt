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
            PermissionRequestScreen(permissionState)
        } else {
            // --- 3. Relational UI Logic ---
            if (activeSession == null) {
                StartSessionView(
                    // UPDATE: No context needed here anymore!
                    onStart = { title -> viewModel.startSession(title) },
                    knownTitles = knownTitles
                )
            } else {
                ActiveSessionView(
                    sessionName = activeSession!!.displayName,
                    // UPDATE: No context needed here anymore!
                    onEnd = { viewModel.endSession() }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionState: com.google.accompanist.permissions.MultiplePermissionsState
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Permissions Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Vibe Reader needs notifications for the lock screen and microphone access to capture quotes.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
            Text("Grant Permissions")
        }
    }
}