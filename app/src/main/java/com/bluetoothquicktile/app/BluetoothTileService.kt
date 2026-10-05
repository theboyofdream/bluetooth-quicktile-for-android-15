package com.bluetoothquicktile.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Dedicated Bluetooth Quick Settings Tile for Android 15.
 *
 * Implements:
 * 1. Exact subtitles:
 *    - Off
 *    - On
 *    - Connecting…
 *    - <Device Name>
 * 2. Instant single-tap direct toggle without multi-step UI or popup sheets.
 * 3. Real-time updates on adapter and device connection state changes.
 */
class BluetoothTileService : TileService() {

    companion object {
        private const val TAG = "BluetoothTileService"

        /**
         * Delay before re-reading the adapter after a toggle. `enable()` and `disable()` report
         * whether the request was accepted, not whether the radio finished changing, and
         * `ACTION_STATE_CHANGED` can arrive a moment later. Half a second is long enough for a
         * normal transition and short enough that the correction is not visible as a flicker.
         */
        private const val TOGGLE_RECONCILE_DELAY_MS = 500L
    }

    private var isReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())
    private var reconcileRunnable: Runnable? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Broadcast received: ${intent?.action}")
            refreshTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile added to Quick Settings panel")
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile started listening")
        registerReceiverIfNeeded()
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile stopped listening")
        cancelPendingReconcile()
        unregisterReceiverIfNeeded()
    }

    override fun onTileRemoved() {
        cancelPendingReconcile()
        unregisterReceiverIfNeeded()
        super.onTileRemoved()
    }

    override fun onDestroy() {
        // onStopListening is not guaranteed to run before the service is torn down, so the
        // receiver and any queued reconcile are cleaned up here as well.
        cancelPendingReconcile()
        unregisterReceiverIfNeeded()
        super.onDestroy()
    }

    /**
     * Single tap directly executes the Bluetooth action without opening the multi-step sheet.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile tapped")

        val tile = qsTile ?: return

        // 1. Permission Check: On Android 12+, prompt user to grant permission if missing
        if (!BluetoothHelper.hasConnectPermission(this)) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(mainIntent)
            }
            return
        }

        // readIsEnabled returns null when the adapter is missing or its state cannot be read.
        val wasEnabled = BluetoothHelper.readIsEnabled(this)

        if (wasEnabled == null) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(mainIntent)
            }
            return
        }

        // 2. Perform direct single-tap toggle
        if (wasEnabled) {
            // Turning OFF: Immediate optimistic UI update
            applyTile(
                Tile.STATE_INACTIVE,
                getString(R.string.tile_state_off),
                R.drawable.ic_qs_bluetooth_off
            )

            val success = BluetoothHelper.disableBluetooth(this)
            if (BuildConfig.DEBUG) Log.d(TAG, "Direct disableBluetooth result: $success")

            if (!success) {
                // The radio refused the request. Re-read shortly so the tile does not keep the
                // optimistic value that never became true.
                scheduleReconcile()
            }
        } else {
            // Turning ON: Immediate optimistic UI update
            applyTile(
                Tile.STATE_ACTIVE,
                getString(R.string.tile_state_connecting),
                R.drawable.ic_qs_bluetooth
            )

            val success = BluetoothHelper.enableBluetooth(this)
            if (BuildConfig.DEBUG) Log.d(TAG, "Direct enableBluetooth result: $success")

            if (!success) {
                // Fallback in case platform policy blocks enable()
                val enableIntent = BluetoothHelper.createEnableIntent()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        1,
                        enableIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(enableIntent)
                }
            }

            // Whether or not the request was accepted, re-read the adapter shortly so the tile
            // reflects what the radio actually did.
            scheduleReconcile()
        }
    }

    /**
     * Queries Bluetooth state and updates Tile label, subtitle, state, and icon.
     */
    fun refreshTile() {
        val tile = qsTile ?: return

        BluetoothHelper.resolveTileInfo(this, getString(R.string.tile_label)) { info ->
            // resolveTileInfo always completes on the main thread, but a queued reconcile can
            // land after the tile stopped listening. Guard against updating a detached tile.
            if (qsTile == null) return@resolveTileInfo

            tile.label = info.label
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = info.subtitle
            }
            tile.state = info.state
            tile.icon = Icon.createWithResource(this, info.iconRes)
            tile.contentDescription = getString(
                R.string.tile_content_description,
                info.label,
                info.subtitle
            )
            tile.updateTile()
            if (BuildConfig.DEBUG) Log.d(TAG, "Tile updated: label='${info.label}', subtitle='${info.subtitle}', state=${info.state}")
        }
    }

    private fun applyTile(state: Int, subtitle: String, iconRes: Int) {
        val tile = qsTile ?: return
        tile.state = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.contentDescription = getString(
            R.string.tile_content_description,
            getString(R.string.tile_label),
            subtitle
        )
        tile.updateTile()
    }

    /**
     * Re-reads the adapter shortly after a toggle, so a refused request cannot leave the tile
     * showing an optimistic value that never became true.
     */
    private fun scheduleReconcile() {
        cancelPendingReconcile()
        val runnable = Runnable {
            reconcileRunnable = null
            refreshTile()
        }
        reconcileRunnable = runnable
        handler.postDelayed(runnable, TOGGLE_RECONCILE_DELAY_MS)
    }

    private fun cancelPendingReconcile() {
        reconcileRunnable?.let { handler.removeCallbacks(it) }
        reconcileRunnable = null
    }

    private fun registerReceiverIfNeeded() {
        if (isReceiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                bluetoothReceiver,
                buildBluetoothFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: SecurityException) {
            // Registering for protected system broadcasts can be refused. Without the receiver
            // the tile still updates on onStartListening and after every tap.
            Log.w(TAG, "Could not register Bluetooth receiver: ${e.message}")
        }
    }

    private fun unregisterReceiverIfNeeded() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        } finally {
            isReceiverRegistered = false
        }
    }

    private fun buildBluetoothFilter(): IntentFilter = BluetoothStateReceiver.newFilter()
}