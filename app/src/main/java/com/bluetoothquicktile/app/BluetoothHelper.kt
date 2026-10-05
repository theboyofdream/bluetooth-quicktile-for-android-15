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
import android.os.Handler
import android.os.Looper
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

    /** How long to wait for a Bluetooth profile proxy before giving up and reporting no device. */
    private const val PROFILE_PROXY_TIMEOUT_MS = 1_500L

    /** Handler token for the profile-proxy timeout, so it can be cancelled. */
    private val TIMEOUT_TOKEN = Any()

    /**
     * The Bluetooth condition this app is reporting, as a value rather than a display string.
     *
     * Callers switch on this instead of comparing subtitles, so a paired device named "On" or
     * "Connecting..." cannot be mistaken for a fixed state, and translating the subtitles cannot
     * desynchronise the UI from the adapter.
     */
    enum class BluetoothCondition {
        /** No adapter on this device, or its state could not be read at all. */
        UNAVAILABLE,

        /** Adapter is off, or is turning off. */
        OFF,

        /** Adapter is on but no profile is connected and none is connecting. */
        ON,

        /** Adapter is turning on, or a profile is currently connecting. */
        CONNECTING,

        /** A profile is connected; [TileDisplayInfo.subtitle] holds the device name. */
        CONNECTED
    }

    data class TileDisplayInfo(
        val label: String,
        val subtitle: String,
        val state: Int,
        val iconRes: Int,
        val condition: BluetoothCondition
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
     * Reads [BluetoothAdapter.getState], or returns null when it cannot be read.
     *
     * `getState()` needs BLUETOOTH_CONNECT on API 31+, so this never throws. Callers use null to
     * mean "unknown" and must not treat it as STATE_OFF.
     */
    @SuppressLint("MissingPermission")
    fun readAdapterState(context: Context): Int? {
        if (!hasConnectPermission(context)) return null
        val adapter = getAdapter(context) ?: return null
        return try {
            adapter.state
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while reading adapter state: ${e.message}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException while reading adapter state: ${e.message}")
            null
        }
    }

    /**
     * Reads [BluetoothAdapter.isEnabled], or returns null when it cannot be read.
     *
     * `isEnabled` needs BLUETOOTH_CONNECT on API 31+, so this never throws.
     */
    @SuppressLint("MissingPermission")
    fun readIsEnabled(context: Context): Boolean? {
        if (!hasConnectPermission(context)) return null
        val adapter = getAdapter(context) ?: return null
        return try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while reading isEnabled: ${e.message}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "IllegalStateException while reading isEnabled: ${e.message}")
            null
        }
    }

    /**
     * Directly enables Bluetooth.
     * Uses BluetoothAdapter.enable(), which functions directly without user prompt when
     * running in backward compatibility mode.
     */
    @SuppressLint("MissingPermission")
    fun enableBluetooth(context: Context): Boolean {
        if (!hasConnectPermission(context)) return false
        val adapter = getAdapter(context) ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.enable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while enabling Bluetooth: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException while enabling Bluetooth: ${e.message}")
            false
        }
    }

    /**
     * Directly disables Bluetooth.
     * Uses BluetoothAdapter.disable(), which turns off the radio immediately.
     */
    @SuppressLint("MissingPermission")
    fun disableBluetooth(context: Context): Boolean {
        if (!hasConnectPermission(context)) return false
        val adapter = getAdapter(context) ?: return false
        return try {
            @Suppress("DEPRECATION")
            adapter.disable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while disabling Bluetooth: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException while disabling Bluetooth: ${e.message}")
            false
        }
    }

    /**
     * Toggles Bluetooth state directly.
     * Single tap action:
     * - If OFF or TURNING_OFF: enables Bluetooth.
     * - If ON or TURNING_ON or CONNECTED: disables Bluetooth.
     *
     * Returns false when the state could not be read, so a missing permission never reads as
     * "off" and accidentally triggers an enable.
     */
    fun toggleBluetooth(context: Context): Boolean {
        return when (readIsEnabled(context)) {
            true -> disableBluetooth(context)
            false -> enableBluetooth(context)
            null -> {
                Log.w(TAG, "toggleBluetooth: adapter state unknown, not toggling")
                false
            }
        }
    }

    /**
     * Resolves the current tile state, label, subtitle, and icon.
     *
     * The subtitle is exactly one of the four values the tile spec allows: "Off", "On",
     * "Connecting…", or "<Device Name>".
     *
     * [onResolved] is always invoked exactly once, on the main thread. When the connected device
     * name cannot be determined within the proxy timeout, it fires with the best-known state
     * rather than never firing, so a tile can never be left showing a stale subtitle.
     */
    fun resolveTileInfo(
        context: Context,
        label: String,
        onResolved: (TileDisplayInfo) -> Unit
    ) {
        val adapter = getAdapter(context)

        if (adapter == null) {
            onResolved(buildDisplayInfo(context, label, BluetoothCondition.UNAVAILABLE, null))
            return
        }

        val state = readAdapterState(context)

        if (state == null) {
            // State is unreadable. Report UNAVAILABLE rather than claiming the radio is off,
            // which would be a specific false statement about the user's device.
            onResolved(buildDisplayInfo(context, label, BluetoothCondition.UNAVAILABLE, null))
            return
        }

        when (state) {
            BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF ->
                onResolved(buildDisplayInfo(context, label, BluetoothCondition.OFF, null))

            BluetoothAdapter.STATE_TURNING_ON ->
                onResolved(buildDisplayInfo(context, label, BluetoothCondition.CONNECTING, null))

            BluetoothAdapter.STATE_ON -> {
                if (!hasConnectPermission(context)) {
                    onResolved(buildDisplayInfo(context, label, BluetoothCondition.ON, null))
                    return
                }
                queryConnectedDevice(context, adapter) { deviceName, isConnecting ->
                    val condition = when {
                        deviceName != null -> BluetoothCondition.CONNECTED
                        isConnecting -> BluetoothCondition.CONNECTING
                        else -> BluetoothCondition.ON
                    }
                    onResolved(buildDisplayInfo(context, label, condition, deviceName))
                }
            }

            else -> onResolved(buildDisplayInfo(context, label, BluetoothCondition.OFF, null))
        }
    }

    /**
     * Resolves the subtitle, Tile state, and icon for a known [condition].
     */
    private fun buildDisplayInfo(
        context: Context,
        label: String,
        condition: BluetoothCondition,
        deviceName: String?
    ): TileDisplayInfo = when (condition) {
        BluetoothCondition.UNAVAILABLE -> TileDisplayInfo(
            label = label,
            subtitle = context.getString(R.string.tile_state_unavailable),
            state = Tile.STATE_UNAVAILABLE,
            iconRes = R.drawable.ic_qs_bluetooth_off,
            condition = condition
        )

        BluetoothCondition.OFF -> TileDisplayInfo(
            label = label,
            subtitle = context.getString(R.string.tile_state_off),
            state = Tile.STATE_INACTIVE,
            iconRes = R.drawable.ic_qs_bluetooth_off,
            condition = condition
        )

        BluetoothCondition.ON -> TileDisplayInfo(
            label = label,
            subtitle = context.getString(R.string.tile_state_on),
            state = Tile.STATE_ACTIVE,
            iconRes = R.drawable.ic_qs_bluetooth,
            condition = condition
        )

        BluetoothCondition.CONNECTING -> TileDisplayInfo(
            label = label,
            subtitle = context.getString(R.string.tile_state_connecting),
            state = Tile.STATE_ACTIVE,
            iconRes = R.drawable.ic_qs_bluetooth,
            condition = condition
        )

        BluetoothCondition.CONNECTED -> TileDisplayInfo(
            label = label,
            subtitle = deviceName ?: context.getString(R.string.tile_state_on),
            state = Tile.STATE_ACTIVE,
            iconRes = R.drawable.ic_qs_bluetooth_connected,
            condition = condition
        )
    }

    /**
     * Queries connected devices using A2DP and Headset profile proxies.
     *
     * Always invokes [callback] exactly once, within [PROFILE_PROXY_TIMEOUT_MS] at the latest.
     *
     * The two proxies are tracked separately. Closing the A2DP proxy fires
     * `onServiceDisconnected` for it, and treating that as an answer would discard the HEADSET
     * result and leave every headset-only device showing "On" instead of its name.
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
            Log.w(TAG, "A2DP connection state unavailable: ${e.message}")
            BluetoothProfile.STATE_DISCONNECTED
        }

        val headsetState = try {
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.w(TAG, "Headset connection state unavailable: ${e.message}")
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

        val handler = Handler(Looper.getMainLooper())

        // Delivered once. The timeout guarantees the callback fires even when a proxy request is
        // refused or the profile service never responds.
        var delivered = false

        fun deliver(deviceName: String?) {
            if (delivered) return
            delivered = true
            handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            callback(deviceName, isConnecting)
        }

        // True while the HEADSET fallback is outstanding. An A2DP disconnect is expected and
        // must not count as a result, because we caused it by closing that proxy.
        var awaitingHeadsetFallback = false

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val deviceName = try {
                    proxy.connectedDevices?.firstOrNull()?.let { getDeviceDisplayName(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading connectedDevices for profile $profile: ${e.message}")
                    null
                }

                when {
                    deviceName != null -> deliver(deviceName)

                    profile == BluetoothProfile.A2DP -> {
                        // A2DP is connected but reports no device. Ask HEADSET before answering.
                        awaitingHeadsetFallback = true
                        val requested = try {
                            adapter.getProfileProxy(context, this, BluetoothProfile.HEADSET)
                        } catch (e: Exception) {
                            Log.w(TAG, "HEADSET proxy request failed: ${e.message}")
                            false
                        }
                        if (!requested) deliver(null)
                    }

                    else -> deliver(null)
                }

                closeProxyQuietly(adapter, profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {
                // The A2DP disconnect here is self-inflicted, since we just closed that proxy.
                if (profile == BluetoothProfile.HEADSET && awaitingHeadsetFallback) {
                    deliver(null)
                }
            }
        }

        handler.postAtTime({
            if (!delivered) Log.w(TAG, "Profile proxy timed out, reporting no connected device")
            deliver(null)
        }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + PROFILE_PROXY_TIMEOUT_MS)

        val requested = try {
            adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.w(TAG, "A2DP proxy request failed: ${e.message}")
            false
        }

        if (!requested) deliver(null)
    }

    private fun closeProxyQuietly(adapter: BluetoothAdapter, profile: Int, proxy: BluetoothProfile) {
        try {
            adapter.closeProfileProxy(profile, proxy)
        } catch (e: Exception) {
            Log.w(TAG, "Error closing profile proxy $profile: ${e.message}")
        }
    }

    /**
     * Resolves the human-readable name of a BluetoothDevice.
     *
     * Returns null when neither the name, the alias, nor the address can be read. `getName` and
     * `getAddress` both need BLUETOOTH_CONNECT on API 31+, so every fallback is guarded rather
     * than rethrowing the exception being handled.
     */
    @SuppressLint("MissingPermission")
    fun getDeviceDisplayName(device: BluetoothDevice): String? {
        val name = try {
            device.name
        } catch (e: Exception) {
            Log.w(TAG, "Could not read device name: ${e.message}")
            null
        }

        if (!name.isNullOrBlank()) return name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val alias = try {
                device.alias
            } catch (e: Exception) {
                Log.w(TAG, "Could not read device alias: ${e.message}")
                null
            }
            if (!alias.isNullOrBlank()) return alias
        }

        return try {
            device.address
        } catch (e: Exception) {
            Log.w(TAG, "Could not read device address: ${e.message}")
            null
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