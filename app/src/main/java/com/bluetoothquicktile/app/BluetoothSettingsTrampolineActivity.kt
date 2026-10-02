package com.bluetoothquicktile.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * Trampoline Activity triggered when the user long-presses the Bluetooth Quick Settings tile.
 *
 * SystemUI routes QS tile long-press events via the ACTION_QS_TILE_PREFERENCES intent.
 * This activity handles the intent transparently and immediately redirects to the system
 * Bluetooth settings page where all Bluetooth and BLE devices are listed.
 */
class BluetoothSettingsTrampolineActivity : Activity() {

    companion object {
        private const val TAG = "BluetoothSettingsTrampoline"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothSettingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        try {
            startActivity(bluetoothSettingsIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Settings.ACTION_BLUETOOTH_SETTINGS: ${e.message}")
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            } catch (ignored: Exception) {
            }
        }

        // Finish immediately so this trampoline activity leaves no trace in back stack
        finish()
    }
}
