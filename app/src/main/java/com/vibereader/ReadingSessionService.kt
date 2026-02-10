package com.vibereader

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

/**
 * ReadingSessionService - Foreground service for active reading sessions.
 *
 * Provides a persistent notification during reading sessions.
 * Voice capture is triggered via:
 * - Quick Settings Tile (primary, most reliable)
 * - Notification tap or action button
 * - Full-screen intent from lock screen
 */
class ReadingSessionService : LifecycleService() {

    private lateinit var dao: VibeReaderDao
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentBookName: String = "New Book"

    private val CHANNEL_ID = "vibe_reader_session"
    private val NOTIFICATION_ID = 1001

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SMART_CAPTURE = "ACTION_SMART_CAPTURE"
        const val EXTRA_BOOK_NAME = "EXTRA_BOOK_NAME"
        private const val TAG = "ReadingSessionService"
    }

    override fun onCreate() {
        super.onCreate()
        dao = AppDatabase.getDatabase(this).vibeReaderDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                currentBookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "New Book"
                showSessionNotification(currentBookName)
            }
            ACTION_STOP -> endActiveSession()
            ACTION_SMART_CAPTURE -> launchSmartCapture()
        }
        return START_STICKY
    }

    /**
     * Launch the SpeechCaptureActivity in SMART mode.
     */
    private fun launchSmartCapture() {
        Log.d(TAG, "launchSmartCapture called")

        val intent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("launch_source", "notification")
        }

        try {
            startActivity(intent)
            Log.d(TAG, "Activity launch initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity", e)
        }
    }

    /**
     * Show a simple foreground notification for the reading session.
     */
    private fun showSessionNotification(bookName: String) {
        createNotificationChannel()

        // Full-screen intent for lock screen launch
        val fullScreenIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Capture action intent
        val captureIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val capturePendingIntent = PendingIntent.getActivity(
            this, 1, captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End session action intent
        val stopIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app intent
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 3, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(bookName)
            .setContentText("Reading session active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            // Action: Capture
            .addAction(
                R.drawable.ic_tile_capture,
                "Capture",
                capturePendingIntent
            )
            // Action: End Session
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End",
                stopPendingIntent
            )
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reading Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when a reading session is active"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
