package com.vibereader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat.MediaStyle
import com.vibereader.data.db.AppDatabase
import com.vibereader.ui.SpeechCaptureActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.widget.Toast

class ReadingSessionService : LifecycleService() {

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var notificationManager: NotificationManager

    // ADDED: Get a reference to the database
    private val db by lazy { AppDatabase.getDatabase(this) }

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
                Log.d("Service", "Starting session for: $currentBookTitle")
                startForeground(NOTIFICATION_ID, buildNotification())
                // We no longer need to insert to Room here, ViewModel does it
            }
            ACTION_STOP_SESSION -> {
                Log.d("Service", "Stopping session")
                // We no longer need to update Room here, ViewModel does it
                stopService()
            }
            ACTION_DEFINE_WORD -> {
                Log.d("Service", "Define Word Tapped!")
                // TODO: Launch SpeechCaptureActivity with "DEFINE" mode
                Toast.makeText(this, "Define Word: Not Implemented", Toast.LENGTH_SHORT).show()
            }
            ACTION_SAVE_QUOTE -> {
                Log.d("Service", "Save Quote Tapped!")
                // UPDATED: Launch our new activity
                launchSpeechCapture()
            }
        }
        return START_STICKY
    }

    // ADDED: New function to launch the capture activity
    private fun launchSpeechCapture() {
        // We must find the active session ID from the database
        lifecycleScope.launch {
            val activeSession = db.vibeReaderDao().getActiveSession().firstOrNull()
            if (activeSession == null) {
                Log.e("Service", "Save Quote tapped, but no active session found in DB.")
                Toast.makeText(applicationContext, "Error: No Active Session", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Launch the transparent activity over the lock screen
            val intent = Intent(applicationContext, SpeechCaptureActivity::class.java).apply {
                // We MUST add this flag to start an Activity from a Service
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(SpeechCaptureActivity.EXTRA_SESSION_ID, activeSession.sessionId)
            }
            startActivity(intent)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val defineWordIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_DEFINE_WORD
        }
        val defineWordPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DEFINE,
            defineWordIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val defineAction = NotificationCompat.Action(
            R.drawable.ic_define_word,
            "Define Word",
            defineWordPendingIntent
        )

        val saveQuoteIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_SAVE_QUOTE
        }
        val saveQuotePendingIntent = PendingIntent.getService(
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
        const val ACTION_DEFINE_WORD = "com.vibereader.ACTION_DEFINE_WORD"
        const val ACTION_SAVE_QUOTE = "com.vibereader.ACTION_SAVE_QUOTE"
        const val EXTRA_BOOK_TITLE = "com.vibereader.EXTRA_BOOK_TITLE"
        const val REQUEST_CODE_DEFINE = 101
        const val REQUEST_CODE_SAVE = 102
        const val REQUEST_CODE_STOP = 103
    }
}