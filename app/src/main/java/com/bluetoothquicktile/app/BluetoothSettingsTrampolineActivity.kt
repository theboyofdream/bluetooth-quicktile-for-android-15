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

        /**
         * Tried in order. ACTION_BLUETOOTH_SETTINGS is absent on some devices, and it moved on
         * Android 15, so the broader wireless and top-level settings pages act as fallbacks.
         */
        private val SETTINGS_ACTIONS = listOf(
            Settings.ACTION_BLUETOOTH_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var launched = false
        for (action in SETTINGS_ACTIONS) {
            try {
                startActivity(Intent(action).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                launched = true
                break
            } catch (e: Exception) {
                Log.w(TAG, "Settings action $action unavailable: ${e.message}")
            }
        }

        if (!launched) {
            // Nothing resolved. Close immediately rather than leaving a blank translucent
            // activity on screen.
            Log.e(TAG, "No Bluetooth settings screen available on this device")
        }

        // Finish immediately so this trampoline activity leaves no trace in back stack
        finish()
    }
}