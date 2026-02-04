package com.vibereader.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vibereader.ReadingSessionService
import com.vibereader.data.db.AppDatabase
import com.vibereader.data.db.Quote
import com.vibereader.data.db.Word
import com.vibereader.data.network.RetrofitClient
import com.vibereader.data.network.WikipediaClient
import retrofit2.HttpException
import com.vibereader.ui.theme.VibeReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Translucent overlay activity for hands-free capture.
 *
 * Supports three modes:
 * - DEFINE_WORD: Explicitly define a word
 * - SAVE_QUOTE: Explicitly save a quote
 * - SMART_CAPTURE: Auto-detect based on input length (1 word = define, >1 = quote)
 */
class SpeechCaptureActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var captureMode: CaptureMode = CaptureMode.SMART_CAPTURE

    // UI State
    private val uiState = mutableStateOf(CaptureState.LISTENING)
    private val spokenText = mutableStateOf("")
    private val definitionText = mutableStateOf("")
    private val detectedMode = mutableStateOf<CaptureMode?>(null)

    private enum class CaptureState { LISTENING, VERIFYING, SAVING, DEFINING, SUCCESS, ERROR }
    enum class CaptureMode { SAVE_QUOTE, DEFINE_WORD, SMART_CAPTURE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Ensure the activity can show over the lock screen
        setupLockScreenVisibility()

        // 2. Determine mode from Intent Action
        captureMode = when (intent.action) {
            "ACTION_DEFINE" -> CaptureMode.DEFINE_WORD
            "ACTION_QUOTE" -> CaptureMode.SAVE_QUOTE
            ReadingSessionService.ACTION_SMART_CAPTURE -> CaptureMode.SMART_CAPTURE
            else -> CaptureMode.SMART_CAPTURE
        }

        // 3. Initialize Services
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechRecognitionListener)

        setContent {
            VibeReaderTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    CaptureScreen()
                }
            }
        }

        startListening()
    }

    /**
     * Called when activity is relaunched with FLAG_ACTIVITY_SINGLE_TOP.
     * Must reset state and restart listening.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("SpeechCapture", "onNewIntent - restarting capture")

        // Update capture mode from new intent
        captureMode = when (intent.action) {
            "ACTION_DEFINE" -> CaptureMode.DEFINE_WORD
            "ACTION_QUOTE" -> CaptureMode.SAVE_QUOTE
            ReadingSessionService.ACTION_SMART_CAPTURE -> CaptureMode.SMART_CAPTURE
            else -> CaptureMode.SMART_CAPTURE
        }

        // Reset state and start listening again
        startListening()
    }

    private fun setupLockScreenVisibility() {
        // Multiple approaches for maximum compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }

        // Also set window flags for older devices and as backup
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    @Composable
    private fun CaptureScreen() {
        val state by remember { uiState }
        val sText by remember { spokenText }
        val dText by remember { definitionText }
        val detected by remember { detectedMode }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode indicator
            val modeLabel = when (captureMode) {
                CaptureMode.DEFINE_WORD -> "Define Mode"
                CaptureMode.SAVE_QUOTE -> "Quote Mode"
                CaptureMode.SMART_CAPTURE -> "Smart Capture"
            }
            Text(
                modeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(24.dp))

            when (state) {
                CaptureState.LISTENING -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Listening...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (captureMode == CaptureMode.SMART_CAPTURE)
                            "Say a word to define, or a phrase to quote"
                        else
                            "Speak now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                CaptureState.SAVING -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Saving...", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }

                CaptureState.VERIFYING -> {
                    // Editable text field for manual correction
                    Text(
                        "I heard:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Editable text field
                    var editableText by remember(sText) { mutableStateOf(sText) }
                    BasicTextField(
                        value = editableText,
                        onValueChange = {
                            editableText = it
                            spokenText.value = it
                            // Re-detect mode based on word count
                            val wordCount = it.trim().split("\\s+".toRegex()).size
                            detectedMode.value = if (wordCount <= 2) CaptureMode.DEFINE_WORD else CaptureMode.SAVE_QUOTE
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                innerTextField()
                            }
                        }
                    )

                    Text(
                        "Tap to edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (detected != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "→ Detected as ${if (detected == CaptureMode.DEFINE_WORD) "WORD" else "QUOTE"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Primary action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        OutlinedButton(
                            onClick = { finish() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                        ) {
                            Text("Cancel")
                        }
                        OutlinedButton(
                            onClick = { startListening() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Retry")
                        }
                        Button(onClick = { confirmCapture() }) {
                            Text(if (detected == CaptureMode.SAVE_QUOTE) "Save Quote" else "Define")
                        }
                    }

                    // Secondary action: "Define This Instead" - only show for quotes
                    if (detected == CaptureMode.SAVE_QUOTE) {
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "or",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        TextButton(
                            onClick = { defineInstead() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                        ) {
                            Text("Define This Instead")
                        }
                    }
                }

                CaptureState.DEFINING -> {
                    if (dText.isEmpty()) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Defining '$sText'...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    } else {
                        Text(
                            sText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            dText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { finish() }) {
                            Text("Done")
                        }
                    }
                }

                CaptureState.SUCCESS -> {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Saved!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }

                CaptureState.ERROR -> {
                    Text(
                        "Could not capture speech",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { finish() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Close")
                        }
                        Button(onClick = { startListening() }) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }

    private fun startListening() {
        uiState.value = CaptureState.LISTENING
        spokenText.value = ""
        definitionText.value = ""
        detectedMode.value = null

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleSpeechResult(text: String) {
        spokenText.value = text

        when (captureMode) {
            CaptureMode.SAVE_QUOTE -> {
                // Explicit quote mode - go to verification
                detectedMode.value = CaptureMode.SAVE_QUOTE
                uiState.value = CaptureState.VERIFYING
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            CaptureMode.DEFINE_WORD -> {
                // Explicit define mode - go straight to defining
                detectedMode.value = CaptureMode.DEFINE_WORD
                defineWord(text)
            }
            CaptureMode.SMART_CAPTURE -> {
                // Smart mode: count words to determine intent
                val wordCount = text.trim().split("\\s+".toRegex()).size

                if (wordCount <= 2) {
                    // 1-2 words = probably a word to define
                    detectedMode.value = CaptureMode.DEFINE_WORD
                    // Still verify first in smart mode
                    uiState.value = CaptureState.VERIFYING
                } else {
                    // 3+ words = probably a quote
                    detectedMode.value = CaptureMode.SAVE_QUOTE
                    uiState.value = CaptureState.VERIFYING
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    /**
     * Called when user confirms the captured text.
     * Routes to either definition or quote saving based on detected mode.
     */
    private fun confirmCapture() {
        val text = spokenText.value
        val mode = detectedMode.value ?: return

        when (mode) {
            CaptureMode.DEFINE_WORD -> defineWord(text)
            CaptureMode.SAVE_QUOTE -> saveQuote()
            CaptureMode.SMART_CAPTURE -> {} // Should never happen
        }
    }

    /**
     * User chose to define the phrase instead of saving as quote.
     * Switches mode and triggers definition lookup.
     */
    private fun defineInstead() {
        detectedMode.value = CaptureMode.DEFINE_WORD
        defineWord(spokenText.value)
    }

    private fun defineWord(term: String) {
        uiState.value = CaptureState.DEFINING
        lifecycleScope.launch {
            val cleanTerm = term.lowercase().trim()

            // Try Dictionary API first (with and without articles)
            val termsToTry = listOf(
                cleanTerm,
                cleanTerm.removePrefix("the ").removePrefix("a ").removePrefix("an ")
            ).distinct()

            for (searchTerm in termsToTry) {
                try {
                    val response = RetrofitClient.instance.getDefinition(searchTerm)
                    val meaning = response.firstOrNull()?.meanings?.firstOrNull()
                    val def = meaning?.definitions?.firstOrNull()?.definition

                    if (def != null) {
                        val partOfSpeech = meaning.partOfSpeech ?: "unknown"
                        val formatted = "($partOfSpeech) $def"
                        definitionText.value = formatted
                        saveWord(term, formatted)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d("SpeechCapture", "Dictionary API failed for '$searchTerm': ${e.message}")
                }
            }

            // Fallback to Wikipedia API - try multiple search variations
            val wikiTermsToTry = termsToTry.map { it.replace(" ", "_") }

            for (wikiTerm in wikiTermsToTry) {
                try {
                    Log.d("SpeechCapture", "Trying Wikipedia for: $wikiTerm")
                    val wikiResponse = WikipediaClient.instance.getSummary(wikiTerm)
                    val summary = wikiResponse.description
                        ?: wikiResponse.extract.take(200) + if (wikiResponse.extract.length > 200) "..." else ""

                    definitionText.value = "(Wikipedia) $summary"
                    saveWord(term, "(Wikipedia) $summary")
                    return@launch
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        Log.d("SpeechCapture", "Wikipedia page not found for '$wikiTerm'")
                    } else {
                        Log.e("SpeechCapture", "Wikipedia API error for '$wikiTerm'", e)
                    }
                } catch (e: Exception) {
                    Log.e("SpeechCapture", "Wikipedia API failed for '$wikiTerm'", e)
                }
            }

            // All attempts failed
            definitionText.value = "No definition found for '$term'"
            saveWord(term, "No definition found.")
        }
    }

    private fun saveWord(term: String, definition: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()
            val active = dao.getActiveSession().first()
            if (active != null) {
                dao.insertWord(
                    Word(
                        bookId = active.bookId,
                        sessionId = active.sessionId,
                        term = term,
                        definition = definition,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun saveQuote() {
        uiState.value = CaptureState.SAVING
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()
            val active = dao.getActiveSession().first()
            if (active != null) {
                dao.insertQuote(
                    Quote(
                        bookId = active.bookId,
                        sessionId = active.sessionId,
                        content = spokenText.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
                uiState.value = CaptureState.SUCCESS
                Toast.makeText(applicationContext, "Quote Saved!", Toast.LENGTH_SHORT).show()

                // Auto-dismiss after brief delay
                kotlinx.coroutines.delay(800)
                finish()
            }
        }
    }

    private val speechRecognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleSpeechResult(matches[0])
            } else {
                uiState.value = CaptureState.ERROR
            }
        }

        override fun onError(error: Int) {
            Log.e("SpeechCapture", "Speech recognition error: $error")
            uiState.value = CaptureState.ERROR
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    override fun onStop() {
        super.onStop()
        // Cancel any ongoing speech recognition when activity goes to background
        try {
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
        } catch (e: Exception) {
            Log.d("SpeechCapture", "Error stopping speech recognizer: ${e.message}")
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
