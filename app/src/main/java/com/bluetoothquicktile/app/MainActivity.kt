package com.bluetoothquicktile.app

import android.Manifest
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bluetoothquicktile.app.ui.MainScreen
import com.bluetoothquicktile.app.ui.PermissionUiState
import com.bluetoothquicktile.app.ui.theme.BluetoothQuickTileTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/**
 * Setup and Diagnostics Activity for Bluetooth Quicktile.
 *
 * Implements the Material 3 UX designed in the HTML artifacts:
 * - Dynamic color based on wallpaper (Monet / Material You) on Android 12+.
 * - Hero Card with animated Bluetooth pulse rings and morphing orb.
 * - Interactive split button with quick toggle & Bluetooth settings.
 * - 3-State Permission Card (Granted, Denied, Blocked).
 * - Live interactive Quick Settings tile preview & one-tap system tile addition.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "perms"
        private const val KEY_BT_CONNECT_REQUESTED = "bt_connect_requested"
    }

    private var condition by mutableStateOf(BluetoothHelper.BluetoothCondition.UNAVAILABLE)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var permState by mutableStateOf(PermissionUiState.DENIED)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Re-derive the permission state from the system rather than trusting the callback, and
        // reflect a grant the user made in Settings rather than through the dialog.
        if (!granted) Log.i(TAG, "BLUETOOTH_CONNECT request was denied")
        updateState()
        notifyTileUpdate()
    }

    /**
     * Reused across "Add tile" taps. Allocating a fresh single-thread executor per tap leaked a
     * thread and its pool for the life of the process.
     */
    private val tileAddExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateState()
            notifyTileUpdate()
        }
    }

    private var isStateReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Resolve the real state before the first composition so the UI never renders a
        // placeholder "Bluetooth is off" that it has to correct a frame later.
        updateState()

        setContent {
            BluetoothQuickTileTheme(dynamicColor = true) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                fun showMessage(message: String) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                    }
                }

                MainScreen(
                    condition = condition,
                    connectedDeviceName = connectedDeviceName,
                    systemPermissionState = permState,
                    onToggleBluetooth = {
                        when (val result = performToggle()) {
                            ToggleResult.OFF -> showMessage(getString(R.string.snackbar_bluetooth_off))
                            ToggleResult.ON -> showMessage(getString(R.string.snackbar_bluetooth_on))
                            ToggleResult.UNKNOWN ->
                                showMessage(getString(R.string.snackbar_bluetooth_unavailable))
                            ToggleResult.PERMISSION_REQUIRED ->
                                requestBluetoothPermission(::showMessage)
                            ToggleResult.NEEDS_SYSTEM_PROMPT -> offerSystemEnablePrompt()
                        }
                        updateState()
                        notifyTileUpdate()
                    },
                    onRequestPermission = {
                        requestBluetoothPermission(::showMessage)
                        updateState()
                    },
                    onOpenBluetoothSettings = { openBluetoothSettings(::showMessage) },
                    onOpenAppSettings = { openAppSettings(::showMessage) },
                    onAddTileToQuickSettings = {
                        addTileToQuickSettings { message -> showMessage(message) }
                    },
                    onOpenGitHub = { openGitHubRepo(::showMessage) },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also covers the user granting or revoking the permission in system Settings.
        updateState()
        registerStateReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterStateReceiver()
    }

    private enum class ToggleResult { ON, OFF, UNKNOWN, PERMISSION_REQUIRED, NEEDS_SYSTEM_PROMPT }

    private fun performToggle(): ToggleResult {
        if (!BluetoothHelper.hasConnectPermission(this)) return ToggleResult.PERMISSION_REQUIRED

        val wasEnabled = BluetoothHelper.readIsEnabled(this)
            ?: return ToggleResult.UNKNOWN

        val succeeded = BluetoothHelper.toggleBluetooth(this)
        return when {
            succeeded && wasEnabled -> ToggleResult.OFF
            succeeded -> ToggleResult.ON
            // The direct call was refused. Offer the system dialog rather than failing silently.
            !wasEnabled -> ToggleResult.NEEDS_SYSTEM_PROMPT
            // Turning off was refused and there is no system prompt for it, so say nothing
            // succeeded instead of claiming either direction.
            else -> ToggleResult.UNKNOWN
        }
    }

    private fun requestBluetoothPermission(onMessage: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Record the attempt before launching, because the system dialog may auto-deny.
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BT_CONNECT_REQUESTED, true)
                .apply()
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            onMessage(getString(R.string.snackbar_permission_unavailable))
        }
    }

    private fun offerSystemEnablePrompt() {
        try {
            startActivity(BluetoothHelper.createEnableIntent())
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the Bluetooth enable prompt: ${e.message}")
        }
    }

    private fun updateState() {
        permState = resolvePermissionState()

        BluetoothHelper.resolveTileInfo(this, getString(R.string.tile_label)) { info ->
            condition = info.condition
            // Only a CONNECTED condition carries a device name in the subtitle. Every other
            // subtitle is a fixed state label, so the name is cleared to avoid leaking a stale
            // device into the UI after disconnecting.
            connectedDeviceName =
                if (info.condition == BluetoothHelper.BluetoothCondition.CONNECTED) info.subtitle
                else null
        }
    }

    private fun resolvePermissionState(): PermissionUiState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return PermissionUiState.GRANTED
        }

        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) return PermissionUiState.GRANTED

        val hasRequestedBefore = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BT_CONNECT_REQUESTED, false)
        val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        // shouldShowRationale is false both before the first request and after the system stops
        // showing the dialog, so the recorded attempt is what separates "Denied" from "Blocked".
        return if (hasRequestedBefore && !canShowRationale) {
            PermissionUiState.BLOCKED
        } else {
            PermissionUiState.DENIED
        }
    }

    private fun openBluetoothSettings(onMessage: (String) -> Unit) {
        val intents = listOf(
            Settings.ACTION_BLUETOOTH_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in intents) {
            try {
                startActivity(Intent(action).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Settings action $action unavailable: ${e.message}")
            }
        }
        // Nothing worked. Say so, rather than leaving the button looking broken.
        onMessage(getString(R.string.snackbar_bluetooth_settings_unavailable))
    }

    private fun openAppSettings(onMessage: (String) -> Unit) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app settings: ${e.message}")
            onMessage(getString(R.string.snackbar_bluetooth_settings_unavailable))
        }
    }

    private fun openGitHubRepo(onMessage: (String) -> Unit) {
        val url = "https://github.com/theboyofdream/bluetooth-quicktile-for-android-15"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the repository link: ${e.message}")
            onMessage(getString(R.string.snackbar_no_browser))
        }
    }

    private fun addTileToQuickSettings(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            if (statusBarManager == null) {
                onResult(getString(R.string.snackbar_tile_request_failed))
                return
            }

            val component = ComponentName(this, BluetoothTileService::class.java)
            val icon = Icon.createWithResource(this, R.drawable.ic_qs_bluetooth)

            statusBarManager.requestAddTileService(
                component,
                getString(R.string.tile_label),
                icon,
                tileAddExecutor
            ) { resultCode ->
                val message = when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        getString(R.string.snackbar_tile_added)
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        getString(R.string.snackbar_tile_already_added)
                    else -> getString(R.string.snackbar_tile_request_failed)
                }
                onResult(message)
            }
        } else {
            onResult(getString(R.string.snackbar_tile_manual_instructions))
        }
    }

    private fun notifyTileUpdate() {
        try {
            val component = ComponentName(this, BluetoothTileService::class.java)
            TileService.requestListeningState(this, component)
        } catch (e: Exception) {
            Log.w(TAG, "Could not request tile listening state: ${e.message}")
        }
    }

    private fun registerStateReceiver() {
        if (isStateReceiverRegistered) return
        val filter = BluetoothStateReceiver.newFilter()
        try {
            ContextCompat.registerReceiver(
                this,
                stateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isStateReceiverRegistered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not register Bluetooth receiver: ${e.message}")
        }
    }

    private fun unregisterStateReceiver() {
        if (!isStateReceiverRegistered) return
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        } finally {
            isStateReceiverRegistered = false
        }
    }
}