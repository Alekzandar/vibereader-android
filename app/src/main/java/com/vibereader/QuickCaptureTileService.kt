package com.vibereader

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.vibereader.data.db.AppDatabase
import com.vibereader.ui.SpeechCaptureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile for reliable lock screen capture.
 *
 * States:
 * - STATE_ACTIVE: Session active, ready to capture
 * - STATE_INACTIVE: No session, prompts user to start one
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickCaptureTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "QuickCaptureTile"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked")

        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()
            val activeSession = dao.getActiveSession().first()

            if (activeSession != null) {
                launchCaptureActivity()
            } else {
                // No session - update tile to show inactive state
                updateTileState()
            }
        }
    }

    private fun launchCaptureActivity() {
        val intent = Intent(this, SpeechCaptureActivity::class.java).apply {
            action = ReadingSessionService.ACTION_SMART_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("launch_source", "quick_settings_tile")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires PendingIntent
            val pendingIntent = PendingIntent.getActivity(
                this, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }

        Log.d(TAG, "Launched SpeechCaptureActivity from tile")
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        serviceScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).vibeReaderDao()
            val activeSession = dao.getActiveSession().first()

            if (activeSession != null) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Capture"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = activeSession.displayName
                }
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Vibe Capture"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "No active session"
                }
            }

            tile.icon = Icon.createWithResource(
                this@QuickCaptureTileService,
                R.drawable.ic_tile_capture
            )
            tile.updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to Quick Settings")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
