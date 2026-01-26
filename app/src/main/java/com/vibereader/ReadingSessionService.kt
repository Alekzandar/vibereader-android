package com.vibereader

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.vibereader.data.db.AppDatabase
import com.vibereader.data.db.VibeReaderDao
import com.vibereader.ui.SpeechCaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReadingSessionService : LifecycleService() {

    private lateinit var dao: VibeReaderDao
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val CHANNEL_ID = "vibe_reader_notifications"
    private val NOTIFICATION_ID = 1001

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_DEFINE = "ACTION_DEFINE"
        const val ACTION_QUOTE = "ACTION_QUOTE"
        const val EXTRA_BOOK_NAME = "EXTRA_BOOK_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        dao = AppDatabase.getDatabase(this).vibeReaderDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val bookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "New Book"
                showNotification(bookName)
            }
            ACTION_STOP -> endActiveSession()
        }
        return START_STICKY
    }

    private fun showNotification(bookName: String) {
        createNotificationChannel()

        // Intents for capture actions
        val defineIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_DEFINE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val definePendingIntent = PendingIntent.getActivity(this, 1, defineIntent, PendingIntent.FLAG_IMMUTABLE)

        val quoteIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_QUOTE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val quotePendingIntent = PendingIntent.getActivity(this, 2, quoteIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ReadingSessionService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        // STANDARD NOTIFICATION (No MediaStyle)
        // Uses standard icons to avoid "Unresolved reference" errors
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vibe Reader")
            .setContentText("Reading: $bookName")
            // Changed to a safe system icon (looks like a book/ledger)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Actions
            .addAction(android.R.drawable.ic_menu_search, "Define", definePendingIntent)
            .addAction(android.R.drawable.ic_menu_add, "Quote", quotePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun endActiveSession() {
        serviceScope.launch {
            val active = dao.getActiveSession().first()
            if (active != null) {
                dao.updateSession(active.copy(status = "inactive", endTime = System.currentTimeMillis()))
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Active Session", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}