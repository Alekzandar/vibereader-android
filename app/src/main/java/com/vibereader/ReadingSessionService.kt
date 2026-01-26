package com.vibereader

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.media.app.NotificationCompat.MediaStyle
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
 * The core background service for Vibe Reader.
 * It manages the MediaSession for lock screen controls and ensures
 * that reading sessions are correctly linked and closed in the database.
 * * Note: Uses manual DI via AppDatabase singleton for stability and simplicity.
 */
class ReadingSessionService : LifecycleService() {

    private lateinit var dao: VibeReaderDao
    private var mediaSession: MediaSessionCompat? = null

    // SupervisorJob ensures that a failure in one DB write doesn't kill the whole scope
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
        // Initialize the DAO using your existing AppDatabase singleton
        dao = AppDatabase.getDatabase(this).vibeReaderDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val bookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "New Book"
                showNotification(bookName)
            }
            ACTION_STOP -> {
                endActiveSession()
            }
        }
        return START_STICKY
    }

    private fun showNotification(bookName: String) {
        createNotificationChannel()

        // Initialize or update the MediaSession
        val session = mediaSession ?: MediaSessionCompat(this, "VibeReaderSession").also {
            mediaSession = it
        }

        session.apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_STOP)
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onStop() {
                    // Triggered by the system media controls (Lock Screen)
                    endActiveSession()
                }
            })
            isActive = true
        }

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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vibe Reader")
            .setContentText("Reading: $bookName")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)) // Show Define, Quote, and End
            .addAction(android.R.drawable.ic_btn_speak_now, "Define", definePendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "Quote", quotePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Relational Linkage: Updates the database status before stopping the service.
     */
    private fun endActiveSession() {
        serviceScope.launch {
            // Use .first() to grab the current active session state immediately
            val active = dao.getActiveSession().first()
            if (active != null) {
                dao.updateSession(active.copy(
                    status = "inactive",
                    endTime = System.currentTimeMillis()
                ))
            }

            // Explicitly update the MediaSession state before stopping
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f)
                    .build()
            )

            // Finalize the service lifecycle
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Active Session", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        // Clean up system resources to prevent "ghost" notifications or memory leaks
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}