package com.vibereader.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vibereader.data.db.AppDatabase
import com.vibereader.ui.SessionViewModel

/**
 * Factory for SessionViewModel located in the ui.session package.
 * It correctly takes the AppDatabase and provides it to the ViewModel.
 */
class SessionViewModelFactory(private val database: AppDatabase) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Resolve "AppDatabase expected" error by passing the db instance
            return SessionViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}