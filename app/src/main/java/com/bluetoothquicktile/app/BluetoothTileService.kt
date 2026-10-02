package com.bluetoothquicktile.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

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
    }

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast received: ${intent?.action}")
            refreshTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to Quick Settings panel")
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile started listening")
        registerReceiverIfNeeded()
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile stopped listening")
        unregisterReceiverIfNeeded()
    }

    /**
     * Single tap directly executes the Bluetooth action without opening the multi-step sheet.
     */
    @SuppressLint("MissingPermission", "StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile tapped")

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

        val adapter = BluetoothHelper.getAdapter(this)
        if (adapter == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = getString(R.string.tile_state_off)
            tile.updateTile()
            return
        }

        // 2. Perform direct single-tap toggle
        if (adapter.isEnabled) {
            // Turning OFF: Immediate optimistic UI update
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = getString(R.string.tile_state_off)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bluetooth_off)
            tile.updateTile()

            val success = BluetoothHelper.disableBluetooth(this)
            Log.d(TAG, "Direct disableBluetooth result: $success")
        } else {
            // Turning ON: Immediate optimistic UI update
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = getString(R.string.tile_state_connecting)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bluetooth)
            tile.updateTile()

            val success = BluetoothHelper.enableBluetooth(this)
            Log.d(TAG, "Direct enableBluetooth result: $success")

            // Fallback in case platform policy blocks enable()
            if (!success) {
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
        }

        // Re-query and confirm state shortly after toggle
        tile.updateTile()
    }

    /**
     * Queries Bluetooth state and updates Tile label, subtitle, state, and icon.
     */
    fun refreshTile() {
        val tile = qsTile ?: return

        BluetoothHelper.resolveTileInfo(this) { info ->
            tile.label = info.label
            tile.subtitle = info.subtitle
            tile.state = info.state
            tile.icon = Icon.createWithResource(this, info.iconRes)
            tile.contentDescription = "${info.label}, ${info.subtitle}"
            tile.updateTile()
            Log.d(TAG, "Tile updated: label='${info.label}', subtitle='${info.subtitle}', state=${info.state}")
        }
    }

    private fun registerReceiverIfNeeded() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(bluetoothReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiverIfNeeded() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }
}
