package com.vibereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vibereader.data.db.AppDatabase
import com.vibereader.ui.SessionViewModel
import com.vibereader.ui.SessionViewModelFactory
import com.vibereader.ui.theme.VibeReaderTheme

// ADD THIS LINE
import com.vibereader.ui.MainScreen

class MainActivity : ComponentActivity() {

    // Get the database instance
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Create the ViewModel using our custom Factory
    private val viewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(db)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // FIX: Changed this back to VibeReaderTheme
            VibeReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show the new MainScreen, which handles navigation
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}