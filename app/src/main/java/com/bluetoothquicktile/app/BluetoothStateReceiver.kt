package com.bluetoothquicktile.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        try {
            val componentName = ComponentName(context, BluetoothTileService::class.java)
            TileService.requestListeningState(context, componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request tile listening state: ${e.message}")
        }
    }
}
