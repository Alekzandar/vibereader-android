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

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch

import java.util.Locale



/**

 * Translucent overlay activity for hands-free capture.

 * Handles voice-to-text for Definitions and Quotes, automatically

 * dual-tagging them to the active Book and Session in the database.

 */

class SpeechCaptureActivity : ComponentActivity(), TextToSpeech.OnInitListener {



    private lateinit var speechRecognizer: SpeechRecognizer

    private lateinit var tts: TextToSpeech

    private var captureMode: CaptureMode = CaptureMode.SAVE_QUOTE



    // UI State

    private val uiState = mutableStateOf(CaptureState.LISTENING)

    private val spokenText = mutableStateOf("")

    private val definitionText = mutableStateOf("")



    private enum class CaptureState { LISTENING, VERIFYING, SAVING, DEFINING, ERROR }

    enum class CaptureMode { SAVE_QUOTE, DEFINE_WORD }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)



        // 1. Ensure the activity can show over the lock screen

        setupLockScreenVisibility()



        // 2. Determine mode from Intent Action (sent by ReadingSessionService)

        val action = intent.action

        captureMode = if (action == "ACTION_DEFINE") CaptureMode.DEFINE_WORD else CaptureMode.SAVE_QUOTE



        // 3. Initialize Services

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



        startListening()

    }



    private fun setupLockScreenVisibility() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

            setShowWhenLocked(true)

            setTurnScreenOn(true)

            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            km.requestDismissKeyguard(this, null)

        } else {

            window.addFlags(

                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or

                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or

                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

            )

        }

    }



    @Composable

    private fun CaptureScreen() {

        val state by remember { uiState }

        val sText by remember { spokenText }

        val dText by remember { definitionText }



        Column(

            modifier = Modifier.fillMaxWidth().padding(24.dp),

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

                CaptureState.VERIFYING -> {

                    Text("I heard:", style = MaterialTheme.typography.titleMedium, color = Color.White)

                    Text("\"$sText\"", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)

                    Spacer(Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {

                        Button(onClick = { startListening() }) { Text("Retry") }

                        Button(onClick = { saveQuote() }) { Text("Confirm") }

                    }

                }

                CaptureState.DEFINING -> {

                    if (dText.isEmpty()) {

                        CircularProgressIndicator(color = Color.White)

                        Spacer(Modifier.height(16.dp))

                        Text("Defining '$sText'...", color = Color.White)

                    } else {

                        Text(sText, style = MaterialTheme.typography.headlineSmall, color = Color.White)

                        Text(dText, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)

                        Spacer(Modifier.height(24.dp))

                        Button(onClick = { finish() }) { Text("Done") }

                    }

                }

                CaptureState.ERROR -> {

                    Text("Error capturing speech", color = MaterialTheme.colorScheme.error)

                    Spacer(Modifier.height(16.dp))

                    Button(onClick = { finish() }) { Text("Close") }

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



    private fun handleSpeechResult(text: String) {

        spokenText.value = text

        when (captureMode) {

            CaptureMode.SAVE_QUOTE -> {

                uiState.value = CaptureState.VERIFYING

                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

            }

            CaptureMode.DEFINE_WORD -> {

                uiState.value = CaptureState.DEFINING

                lifecycleScope.launch {

                    try {

                        val response = RetrofitClient.instance.getDefinition(text)

                        val meaning = response.firstOrNull()?.meanings?.firstOrNull()

                        val def = meaning?.definitions?.firstOrNull()?.definition ?: "No definition found."

                        val formatted = "(${meaning?.partOfSpeech}) $def"

                        definitionText.value = formatted

                        saveWord(text, formatted)

                    } catch (e: Exception) {

                        definitionText.value = "Definition not found."

                        saveWord(text, "Definition not found.")

                    }

                }

            }

        }

    }



    private fun saveWord(term: String, definition: String) {

        lifecycleScope.launch {

            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()

            // Pull the active session to get IDs for tagging

            val active = dao.getActiveSession().first()

            if (active != null) {

                dao.insertWord(Word(

                    bookId = active.bookId, // Matches bookId in Entities.kt

                    sessionId = active.sessionId, // Matches sessionId in Entities.kt

                    term = term,

                    definition = definition,

                    timestamp = System.currentTimeMillis()

                ))

            }

        }

    }



    private fun saveQuote() {

        uiState.value = CaptureState.SAVING

        lifecycleScope.launch {

            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()

            val active = dao.getActiveSession().first()

            if (active != null) {

                dao.insertQuote(Quote(

                    bookId = active.bookId, // Matches bookId in Entities.kt

                    sessionId = active.sessionId, // Matches sessionId in Entities.kt

                    content = spokenText.value,

                    timestamp = System.currentTimeMillis()

                ))

                Toast.makeText(applicationContext, "Quote Saved!", Toast.LENGTH_SHORT).show()

                finish()

            }

        }

    }



    private val speechRecognitionListener = object : RecognitionListener {

        override fun onResults(results: Bundle?) {

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!matches.isNullOrEmpty()) handleSpeechResult(matches[0]) else uiState.value = CaptureState.ERROR

        }

        override fun onError(error: Int) { uiState.value = CaptureState.ERROR }

        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

    }



    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) tts.language = Locale.getDefault()

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
