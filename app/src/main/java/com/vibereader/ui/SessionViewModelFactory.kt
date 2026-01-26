package com.vibereader.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vibereader.data.db.AppDatabase

class SessionViewModelFactory(
    private val application: Application,
    private val database: AppDatabase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Matches the new SessionViewModel(application, database) constructor
            return SessionViewModel(application, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}