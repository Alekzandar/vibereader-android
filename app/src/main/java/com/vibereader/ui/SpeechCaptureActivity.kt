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
import com.vibereader.data.db.Word
import com.vibereader.data.network.RetrofitClient
import com.vibereader.ui.theme.VibeReaderTheme
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechCaptureActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var activeSessionId: Long = -1
    private var captureMode: CaptureMode = CaptureMode.SAVE_QUOTE // Default

    // State for the UI
    private val uiState = mutableStateOf(CaptureState.LISTENING)
    private val spokenText = mutableStateOf("")
    private val definitionText = mutableStateOf("") // For define mode

    private enum class CaptureState { LISTENING, VERIFYING, SAVING, DEFINING, ERROR }
    enum class CaptureMode { SAVE_QUOTE, DEFINE_WORD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Get Intent Extras ---
        activeSessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        captureMode = intent.getSerializableExtra(EXTRA_CAPTURE_MODE) as? CaptureMode ?: CaptureMode.SAVE_QUOTE

        if (activeSessionId == -1L) {
            Log.e("SpeechCapture", "No active session ID provided. Closing.")
            finish()
            return
        }

        // --- Initialize Speech and TTS ---
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechRecognitionListener)

        setContent {
            VibeReaderTheme {
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

        startListening() // Start listening on launch
    }

    @Composable
    private fun CaptureScreen() {
        val state by remember { uiState }
        val sText by remember { spokenText }
        val dText by remember { definitionText }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                CaptureState.LISTENING, CaptureState.SAVING -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (state == CaptureState.LISTENING) "Listening..." else "Saving...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
                CaptureState.VERIFYING -> { // Quote Mode
                    Text("I heard:", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"$sText\"",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { startListening() }) { Text("Retry") }
                        Button(onClick = { saveQuote() }) { Text("Confirm") }
                    }
                }
                CaptureState.DEFINING -> { // Define Mode
                    if (dText.isEmpty()) {
                        // API call is in progress
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Looking up '$sText'...", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    } else {
                        // API call is complete
                        Text(sText, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(dText, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { finish() }) {
                            Text("Done")
                        }
                    }
                }
                CaptureState.ERROR -> {
                    Text(
                        "Error. Please try again.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { finish() }) {
                        Text("Close")
                    }
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

    // --- New Function: Handle Speech Result ---
    private fun handleSpeechResult(text: String) {
        spokenText.value = text
        when (captureMode) {
            CaptureMode.SAVE_QUOTE -> {
                uiState.value = CaptureState.VERIFYING
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            CaptureMode.DEFINE_WORD -> {
                uiState.value = CaptureState.DEFINING
                // Launch coroutine to call API
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.getDefinition(text)
                        // Get first definition from the API response
                        val firstMeaning = response.firstOrNull()?.meanings?.firstOrNull()
                        val firstDefinition = firstMeaning?.definitions?.firstOrNull()?.definition ?: "No definition found."

                        definitionText.value = "(${firstMeaning?.partOfSpeech}) $firstDefinition"

                        // Save the word to the database
                        saveWord(text, definitionText.value)

                    } catch (e: Exception) {
                        Log.e("SpeechCapture", "API Error: ${e.message}")
                        definitionText.value = "Error: Could not find definition."
                    }
                }
            }
        }
    }

    // --- New Function: Save Word ---
    private fun saveWord(term: String, definition: String) {
        lifecycleScope.launch {
            val word = Word(
                sessionId = activeSessionId,
                term = term,
                definition = definition,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(applicationContext).vibeReaderDao().insertWord(word)
            Log.d("SpeechCapture", "Word saved: $term")
        }
    }

    // --- Updated Function: Save Quote ---
    private fun saveQuote() {
        uiState.value = CaptureState.SAVING
        lifecycleScope.launch {
            val quote = Quote(
                sessionId = activeSessionId,
                content = spokenText.value,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(applicationContext).vibeReaderDao().insertQuote(quote)
            Toast.makeText(applicationContext, "Quote Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- SpeechRecognizer Listener ---
    private val speechRecognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // Handle the result in our new function
                handleSpeechResult(matches[0])
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
        const val EXTRA_CAPTURE_MODE = "com.vibereader.EXTRA_CAPTURE_MODE"
    }
}