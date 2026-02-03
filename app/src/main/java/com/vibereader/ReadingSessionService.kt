package com.vibereader

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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

    private val CHANNEL_ID = "vibe_reader_media_v1"
    private val NOTIFICATION_ID = 1001

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SMART_CAPTURE = "ACTION_SMART_CAPTURE"
        const val EXTRA_BOOK_NAME = "EXTRA_BOOK_NAME"
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
            // Handle the PLAY button press → triggers Smart Capture
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    launchSmartCapture()
                }

                override fun onPause() {
                    // Do nothing - we're not actually playing audio
                }

                override fun onStop() {
                    endActiveSession()
                }
            })

            // Enable transport controls (play/pause/stop buttons)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val bookName = intent.getStringExtra(EXTRA_BOOK_NAME) ?: "New Book"
                showMediaNotification(bookName)
            }
            ACTION_STOP -> endActiveSession()
            ACTION_SMART_CAPTURE -> launchSmartCapture()
        }
        return START_STICKY
    }

    /**
     * Launch the SpeechCaptureActivity in SMART mode.
     * The activity will auto-detect word vs quote based on input length.
     */
    private fun launchSmartCapture() {
        val intent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    /**
     * Build and show the MediaStyle notification.
     * This is the "Trojan Horse" - it looks like a media player but triggers voice capture.
     */
    private fun showMediaNotification(bookName: String) {
        createNotificationChannel()

        // Set metadata to make Android think we're playing "content"
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Tap ▶ to Capture")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, bookName)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Vibe Reader")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L) // Unknown duration
            .putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
            )
            .build()
        mediaSession.setMetadata(metadata)

        // Set playback state to PAUSED so the PLAY button shows
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        // PendingIntents
        val captureIntent = Intent(this, ReadingSessionService::class.java).apply {
            action = ACTION_SMART_CAPTURE
        }
        val capturePendingIntent = PendingIntent.getService(
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
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build MediaStyle notification
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
            // The key: MediaStyle with the session token
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1) // Show first 2 actions in compact view
            )
            // Actions: Capture (play icon) and End (stop icon)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Capture",
                    capturePendingIntent
                ).build()
            )
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
            // Release media session before stopping
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
                NotificationManager.IMPORTANCE_LOW // LOW = no sound, but still visible
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
