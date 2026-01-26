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
import com.vibereader.ui.MainScreen
import com.vibereader.ui.SessionViewModel
import com.vibereader.ui.session.SessionViewModelFactory
import com.vibereader.ui.theme.VibeReaderTheme

/**
 * MainActivity serves as the entry point for Vibe Reader.
 * It initializes the Room database and uses a custom Factory to inject the
 * database dependency into the SessionViewModel.
 */
class MainActivity : ComponentActivity() {

    // Lazy initialization of the database to ensure it's created only when needed
    private val database by lazy { AppDatabase.getDatabase(this) }

    /**
     * Initialize the ViewModel using our custom Factory.
     * This follows manual dependency injection best practices, ensuring the
     * ViewModel has access to the relational database DAO.
     */
    private val viewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(database)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VibeReaderTheme {
                // Surface provides the background color based on the system theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the injected ViewModel to the MainScreen, which handles navigation
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}