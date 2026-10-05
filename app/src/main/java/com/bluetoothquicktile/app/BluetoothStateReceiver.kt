package com.bluetoothquicktile.app

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Manifest-registered BroadcastReceiver that triggers TileService.requestListeningState()
 * whenever Bluetooth adapter state or ACL device connections change, ensuring the QS tile
 * remains synchronized in real time.
 */
class BluetoothStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothStateReceiver"

        /**
         * The single definition of the Bluetooth actions this app observes. The manifest filter,
         * the TileService receiver and the MainActivity receiver all use this, so the three
         * registrations cannot drift apart.
         *
         * All of these are protected system broadcasts, so they arrive only from the platform and
         * cannot be forged by another app.
         */
        fun newFilter(): IntentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "Received broadcast action: $action")

        try {
            val componentName = ComponentName(context, BluetoothTileService::class.java)
            TileService.requestListeningState(context, componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request tile listening state: ${e.message}")
        }
    }
}
