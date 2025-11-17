package com.vibereader.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vibereader.data.db.AppDatabase
import com.vibereader.data.db.Quote
import com.vibereader.ui.theme.VibeReaderTheme
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechCaptureActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var activeSessionId: Long = -1

    // State for the UI
    private val uiState = mutableStateOf(CaptureState.LISTENING)
    private val spokenText = mutableStateOf("")

    private enum class CaptureState { LISTENING, VERIFYING, SAVING, ERROR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the active session ID passed from the service
        activeSessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (activeSessionId == -1L) {
            Log.e("SpeechCapture", "No active session ID provided. Closing.")
            finish()
            return
        }

        // Initialize Speech and TTS
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechRecognitionListener)

        setContent {
            VibeReaderTheme {
                // Full-screen, transparent UI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CaptureScreen()
                }
            }
        }

        // Start listening as soon as the activity launches
        startListening()
    }

    @Composable
    private fun CaptureScreen() {
        val state by remember { uiState }
        val text by remember { spokenText }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                CaptureState.LISTENING -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Listening...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
                CaptureState.VERIFYING -> {
                    Text(
                        "I heard:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"$text\"",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { startListening() }) {
                            Text("Retry")
                        }
                        Button(onClick = { saveQuote() }) {
                            Text("Confirm")
                        }
                    }
                }
                CaptureState.SAVING -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Saving...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
                CaptureState.ERROR -> {
                    Text(
                        "Error. Please try again.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    private fun startListening() {
        uiState.value = CaptureState.LISTENING
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.startListening(intent)
    }

    private fun saveQuote() {
        uiState.value = CaptureState.SAVING
        lifecycleScope.launch {
            val quote = Quote(
                sessionId = activeSessionId,
                content = spokenText.value,
                timestamp = System.currentTimeMillis()
            )
            // Get the database and save the quote
            AppDatabase.getDatabase(applicationContext).vibeReaderDao().insertQuote(quote)

            // Show success and close
            Toast.makeText(applicationContext, "Quote Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- SpeechRecognizer Listener ---
    private val speechRecognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                spokenText.value = matches[0]
                uiState.value = CaptureState.VERIFYING
                // Read the quote back for verification
                tts.speak(spokenText.value, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                uiState.value = CaptureState.ERROR
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onError(error: Int) { uiState.value = CaptureState.ERROR }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        // --- ADD THIS MISSING METHOD ---
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    // --- TextToSpeech Listener ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.vibereader.EXTRA_SESSION_ID"
    }
}