package com.vibereader

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.media.app.NotificationCompat as MediaNotificationCompat
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
 * ReadingSessionService - The "Trojan Horse" Media Player
 *
 * This service masquerades as a media player to get prominent lock screen placement.
 * The PLAY button triggers voice capture instead of audio playback.
 *
 * Smart Capture Logic:
 * - 1 word spoken → Define mode
 * - >1 word spoken → Quote mode
 */
class ReadingSessionService : LifecycleService() {

    private lateinit var dao: VibeReaderDao
    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentBookName: String = "New Book"

    private val CHANNEL_ID = "vibe_reader_media_v2"
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
        initMediaSession()
    }

    /**
     * Initialize MediaSessionCompat - this is what makes Android treat us as a media app.
     */
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "VibeReaderSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession onPlay triggered")
                    launchSmartCapture()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession onPause triggered")
                    // Reset to paused state to show play button again
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession onStop triggered")
                    endActiveSession()
                }
            })

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, 0L, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        // Re-show notification to update the button
        showMediaNotification(currentBookName)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                currentBookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "New Book"
                showMediaNotification(currentBookName)
            }
            ACTION_STOP -> endActiveSession()
            ACTION_SMART_CAPTURE -> launchSmartCapture()
        }
        return START_STICKY
    }

    /**
     * Launch the SpeechCaptureActivity in SMART mode.
     * Uses multiple strategies to ensure it works from lock screen.
     */
    private fun launchSmartCapture() {
        Log.d(TAG, "launchSmartCapture called")

        // Wake up the screen if needed
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "Screen is off, waking up")
        }

        val intent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            // Critical flags for launching from background/lock screen
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Add extra to help debug
            putExtra("launch_source", "media_session")
        }

        try {
            startActivity(intent)
            Log.d(TAG, "Activity launch initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity", e)
            // Fallback: try with full-screen intent notification
            showCaptureNotification()
        }

        // Reset playback state back to paused so play button shows again
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    /**
     * Fallback: Show a high-priority notification with full-screen intent
     * This is guaranteed to work on lock screen (like incoming calls)
     */
    private fun showCaptureNotification() {
        Log.d(TAG, "Showing capture notification as fallback")

        val fullScreenIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 100, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val captureChannelId = "vibe_reader_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                captureChannelId,
                "Voice Capture",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers voice capture overlay"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, captureChannelId)
            .setContentTitle("Vibe Reader")
            .setContentText("Tap to capture")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9999, notification)
    }

    /**
     * Build and show the MediaStyle notification.
     * This is the "Trojan Horse" - it looks like a media player but triggers voice capture.
     */
    private fun showMediaNotification(bookName: String) {
        createNotificationChannel()

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Tap ▶ to Capture")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, bookName)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Vibe Reader")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 360000L) // 6 min fake duration
            .putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
            )
            .build()
        mediaSession.setMetadata(metadata)

        // Ensure we're in PAUSED state so play button shows
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        // Full-screen intent for lock screen launch
        val fullScreenIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Regular capture intent
        val captureIntent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val capturePendingIntent = PendingIntent.getActivity(
            this, 1, captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 3, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(bookName)
            .setContentText("Tap ▶ to capture a word or quote")
            .setSubText("Reading Session Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            // Full-screen intent for lock screen
            .setFullScreenIntent(fullScreenPendingIntent, false)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            // Action 0: Capture (using Activity PendingIntent now!)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Capture",
                    capturePendingIntent
                ).build()
            )
            // Action 1: End Session
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End Session",
                    stopPendingIntent
                ).build()
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
            mediaSession.isActive = false
            mediaSession.release()
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
                description = "Shows reading session controls on lock screen"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
