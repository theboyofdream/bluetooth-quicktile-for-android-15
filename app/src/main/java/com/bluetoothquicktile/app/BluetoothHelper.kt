package com.bluetoothquicktile.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Technical helper providing direct Bluetooth control and profile state resolution on Android 15.
 *
 * Implements the minimum required mechanism as referenced from MacroDroid Connectivity Helper:
 * - Direct enable() and disable() execution via Android's compatibility layer (targetSdk <= 32).
 * - Profile proxy queries for A2DP / Headset to discover connected audio/peripheral device names.
 * - Single-tap toggle avoiding multi-step dialogs.
 */
object BluetoothHelper {

    private const val TAG = "BluetoothHelper"

    data class TileDisplayInfo(
        val label: String = "Bluetooth",
        val subtitle: String,
        val state: Int,
        val iconRes: Int
    )

    /**
     * Retrieves the default BluetoothAdapter.
     */
    fun getAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
        return bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }

    /**
     * Checks if BLUETOOTH_CONNECT permission is granted (required on Android 12+ / API 31+).
     */
    fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Directly enables Bluetooth.
     * Uses BluetoothAdapter.enable(), which functions directly without user prompt when
     * running in backward compatibility mode.
     */
    @SuppressLint("MissingPermission")
    fun enableBluetooth(context: Context): Boolean {
        val adapter = getAdapter(context) ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.enable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while enabling Bluetooth: ${e.message}")
            false
        }
    }

    /**
     * Directly disables Bluetooth.
     * Uses BluetoothAdapter.disable(), which turns off the radio immediately.
     */
    @SuppressLint("MissingPermission")
    fun disableBluetooth(context: Context): Boolean {
        val adapter = getAdapter(context) ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.disable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while disabling Bluetooth: ${e.message}")
            false
        }
    }

    /**
     * Toggles Bluetooth state directly.
     * Single tap action:
     * - If OFF or TURNING_OFF: enables Bluetooth.
     * - If ON or TURNING_ON or CONNECTED: disables Bluetooth.
     */
    @SuppressLint("MissingPermission")
    fun toggleBluetooth(context: Context): Boolean {
        val adapter = getAdapter(context) ?: return false
        return if (adapter.isEnabled) {
            disableBluetooth(context)
        } else {
            enableBluetooth(context)
        }
    }

    /**
     * Resolves the current tile state, label, subtitle, and icon.
     *
     * The subtitle strictly matches the requirement:
     * - "Off"
     * - "On"
     * - "Connecting…"
     * - "<Device Name>"
     */
    @SuppressLint("MissingPermission")
    fun resolveTileInfo(
        context: Context,
        onResolved: (TileDisplayInfo) -> Unit
    ) {
        val adapter = getAdapter(context)

        if (adapter == null) {
            onResolved(
                TileDisplayInfo(
                    subtitle = context.getString(R.string.tile_state_off),
                    state = Tile.STATE_UNAVAILABLE,
                    iconRes = R.drawable.ic_qs_bluetooth_off
                )
            )
            return
        }

        val hasPermission = hasConnectPermission(context)
        val state = try {
            adapter.state
        } catch (e: SecurityException) {
            BluetoothAdapter.STATE_OFF
        }

        when (state) {
            BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                onResolved(
                    TileDisplayInfo(
                        subtitle = context.getString(R.string.tile_state_off),
                        state = Tile.STATE_INACTIVE,
                        iconRes = R.drawable.ic_qs_bluetooth_off
                    )
                )
            }

            BluetoothAdapter.STATE_TURNING_ON -> {
                onResolved(
                    TileDisplayInfo(
                        subtitle = context.getString(R.string.tile_state_connecting),
                        state = Tile.STATE_ACTIVE,
                        iconRes = R.drawable.ic_qs_bluetooth
                    )
                )
            }

            BluetoothAdapter.STATE_ON -> {
                if (!hasPermission) {
                    // Without BLUETOOTH_CONNECT on Android 12+, we can only report "On"
                    onResolved(
                        TileDisplayInfo(
                            subtitle = context.getString(R.string.tile_state_on),
                            state = Tile.STATE_ACTIVE,
                            iconRes = R.drawable.ic_qs_bluetooth
                        )
                    )
                    return
                }

                // Query profile connection states
                queryConnectedDevice(context, adapter) { deviceName, isConnecting ->
                    val subtitle = when {
                        !deviceName.isNullOrEmpty() -> deviceName
                        isConnecting -> context.getString(R.string.tile_state_connecting)
                        else -> context.getString(R.string.tile_state_on)
                    }

                    val icon = if (!deviceName.isNullOrEmpty()) {
                        R.drawable.ic_qs_bluetooth_connected
                    } else {
                        R.drawable.ic_qs_bluetooth
                    }

                    onResolved(
                        TileDisplayInfo(
                            subtitle = subtitle,
                            state = Tile.STATE_ACTIVE,
                            iconRes = icon
                        )
                    )
                }
            }

            else -> {
                onResolved(
                    TileDisplayInfo(
                        subtitle = context.getString(R.string.tile_state_off),
                        state = Tile.STATE_INACTIVE,
                        iconRes = R.drawable.ic_qs_bluetooth_off
                    )
                )
            }
        }
    }

    /**
     * Queries connected devices using A2DP and Headset profile proxies.
     * Discovers active device names or whether any profile is currently connecting.
     */
    @SuppressLint("MissingPermission")
    private fun queryConnectedDevice(
        context: Context,
        adapter: BluetoothAdapter,
        callback: (deviceName: String?, isConnecting: Boolean) -> Unit
    ) {
        val a2dpState = try {
            adapter.getProfileConnectionState(BluetoothProfile.A2DP)
        } catch (e: Exception) {
            BluetoothProfile.STATE_DISCONNECTED
        }

        val headsetState = try {
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            BluetoothProfile.STATE_DISCONNECTED
        }

        val isConnecting = a2dpState == BluetoothProfile.STATE_CONNECTING ||
                headsetState == BluetoothProfile.STATE_CONNECTING

        val hasConnectedProfile = a2dpState == BluetoothProfile.STATE_CONNECTED ||
                headsetState == BluetoothProfile.STATE_CONNECTED

        if (!hasConnectedProfile) {
            callback(null, isConnecting)
            return
        }

        // Connect to A2DP profile proxy to extract the connected device name
        var resolved = false
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (resolved) return
                try {
                    val connectedDevices = proxy.connectedDevices
                    if (!connectedDevices.isNullOrEmpty()) {
                        val device = connectedDevices.first()
                        val name = getDeviceDisplayName(device)
                        resolved = true
                        callback(name, false)
                    } else if (profile == BluetoothProfile.A2DP) {
                        // Try querying HEADSET profile if A2DP had no device list
                        adapter.getProfileProxy(context, this, BluetoothProfile.HEADSET)
                        return
                    } else {
                        resolved = true
                        callback(null, isConnecting)
                    }
                } catch (e: SecurityException) {
                    resolved = true
                    callback(null, isConnecting)
                } finally {
                    try {
                        adapter.closeProfileProxy(profile, proxy)
                    } catch (ignored: Exception) {
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (!resolved) {
                    resolved = true
                    callback(null, isConnecting)
                }
            }
        }

        val requested = try {
            adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            false
        }

        if (!requested) {
            callback(null, isConnecting)
        }
    }

    /**
     * Resolves the human-readable name of a BluetoothDevice.
     */
    @SuppressLint("MissingPermission")
    fun getDeviceDisplayName(device: BluetoothDevice): String {
        return try {
            val name = device.name
            if (!name.isNullOrBlank()) {
                name
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    device.alias ?: device.address
                } else {
                    device.address
                }
            }
        } catch (e: SecurityException) {
            device.address
        }
    }

    /**
     * Create fallback intent for ACTION_REQUEST_ENABLE in case a system prompt is needed.
     */
    fun createEnableIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
