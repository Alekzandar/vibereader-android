package com.vibereader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.media.app.NotificationCompat.MediaStyle
import com.vibereader.ui.SpeechCaptureActivity

class ReadingSessionService : LifecycleService() {

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var notificationManager: NotificationManager

    private var activeSessionId: Long = -1L
    private var currentBookTitle: String = "No Session"

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "ReadingSessionService").apply {
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_SESSION -> {
                currentBookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: "Reading"
                activeSessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)

                Log.d("Service", "Starting session $activeSessionId for: $currentBookTitle")

                if (activeSessionId == -1L) {
                    Log.e("Service", "Invalid session ID. Stopping.")
                    stopService()
                    return START_NOT_STICKY
                }

                // We call buildNotification() here, AFTER activeSessionId is set
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP_SESSION -> {
                Log.d("Service", "Stopping session")
                stopService()
            }
            // --- REMOVED ---
            // We no longer need to handle DEFINE_WORD or SAVE_QUOTE here.
            // The notification PendingIntents will do it directly.
        }
        return START_STICKY
    }

    // --- REMOVED ---
    // We no longer need the launchSpeechCapture function.

    private fun buildNotification(): android.app.Notification {

        // --- THIS IS THE KEY FIX ---
        // We create PendingIntents that launch the Activity directly.

        // Create the "Define Word" intent
        val defineWordIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SpeechCaptureActivity.EXTRA_SESSION_ID, activeSessionId)
            putExtra(SpeechCaptureActivity.EXTRA_CAPTURE_MODE, SpeechCaptureActivity.CaptureMode.DEFINE_WORD)
        }
        // Note: We use a different request code for each PendingIntent
        val defineWordPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_DEFINE,
            defineWordIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // UPDATE_CURRENT is critical
        )
        val defineAction = NotificationCompat.Action(
            R.drawable.ic_define_word,
            "Define Word",
            defineWordPendingIntent
        )

        // Create the "Save Quote" intent
        val saveQuoteIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SpeechCaptureActivity.EXTRA_SESSION_ID, activeSessionId)
            putExtra(SpeechCaptureActivity.EXTRA_CAPTURE_MODE, SpeechCaptureActivity.CaptureMode.SAVE_QUOTE)
        }
        val saveQuotePendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_SAVE,
            saveQuoteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val saveQuoteAction = NotificationCompat.Action(
            R.drawable.ic_save_quote,
            "Save Quote",
            saveQuotePendingIntent
        )
        // --- END OF FIX ---

        val stopIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vibe Reader: $currentBookTitle")
            .setContentText("Session in progress...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(defineAction)
            .addAction(saveQuoteAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setDeleteIntent(stopPendingIntent)
            .build()
    }

    private fun stopService() {
        mediaSession?.release()
        mediaSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vibe Reader Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for the active Vibe Reader session"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ReadingSessionChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SESSION = "com.vibereader.ACTION_START_SESSION"
        const val ACTION_STOP_SESSION = "com.vibereader.ACTION_STOP_SESSION"

        // We no longer need the other actions

        const val EXTRA_BOOK_TITLE = "com.vibereader.EXTRA_BOOK_TITLE"
        const val EXTRA_SESSION_ID = "com.vibereader.EXTRA_SESSION_ID"

        const val REQUEST_CODE_DEFINE = 101
        const val REQUEST_CODE_SAVE = 102
        const val REQUEST_CODE_STOP = 103
    }
}