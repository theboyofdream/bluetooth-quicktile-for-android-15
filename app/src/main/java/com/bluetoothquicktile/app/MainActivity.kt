package com.bluetoothquicktile.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
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
import java.util.concurrent.Executors

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

    private var isBtOn by mutableStateOf(false)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var isConnecting by mutableStateOf(false)
    private var permState by mutableStateOf(PermissionUiState.GRANTED)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateState()
        notifyTileUpdate()
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateState()
            notifyTileUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothQuickTileTheme(dynamicColor = true) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                MainScreen(
                    isBtOn = isBtOn,
                    connectedDeviceName = connectedDeviceName,
                    isConnecting = isConnecting,
                    systemPermissionState = permState,
                    onToggleBluetooth = {
                        val toggled = BluetoothHelper.toggleBluetooth(this)
                        if (toggled) {
                            coroutineScope.launch {
                                val msg = if (isBtOn) "Bluetooth turned off" else "Bluetooth turned on"
                                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                            }
                        } else {
                            val adapter = BluetoothHelper.getAdapter(this)
                            if (adapter != null && !adapter.isEnabled) {
                                startActivity(BluetoothHelper.createEnableIntent())
                            }
                        }
                        updateState()
                        notifyTileUpdate()
                    },
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            getSharedPreferences("perms", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("bt_connect_requested", true)
                                .apply()
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Permission already available on this Android version",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onOpenBluetoothSettings = {
                        openBluetoothSettings()
                    },
                    onOpenAppSettings = {
                        openAppSettings()
                    },
                    onAddTileToQuickSettings = {
                        addTileToQuickSettings { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                            }
                        }
                    },
                    onOpenGitHub = {
                        openGitHubRepo()
                    },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateState()
        registerStateReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterStateReceiver()
    }

    private fun updateState() {
        permState = resolvePermissionState()

        val adapter = BluetoothHelper.getAdapter(this)
        isBtOn = adapter?.isEnabled == true

        BluetoothHelper.resolveTileInfo(this) { info ->
            runOnUiThread {
                val offLabel = getString(R.string.tile_state_off)
                val onLabel = getString(R.string.tile_state_on)
                val connectingLabel = getString(R.string.tile_state_connecting)

                when (info.subtitle) {
                    offLabel -> {
                        isBtOn = false
                        connectedDeviceName = null
                        isConnecting = false
                    }
                    onLabel -> {
                        isBtOn = true
                        connectedDeviceName = null
                        isConnecting = false
                    }
                    connectingLabel -> {
                        isBtOn = true
                        connectedDeviceName = null
                        isConnecting = true
                    }
                    else -> {
                        isBtOn = true
                        connectedDeviceName = info.subtitle
                        isConnecting = false
                    }
                }
            }
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

        val prefs = getSharedPreferences("perms", Context.MODE_PRIVATE)
        val hasRequestedBefore = prefs.getBoolean("bt_connect_requested", false)
        val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        return if (hasRequestedBefore && !canShowRationale) {
            PermissionUiState.BLOCKED
        } else {
            PermissionUiState.DENIED
        }
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun openGitHubRepo() {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/theboyofdream/bluetooth-quicktile-for-android-15.git")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun addTileToQuickSettings(onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            val component = ComponentName(this, BluetoothTileService::class.java)
            val icon = Icon.createWithResource(this, R.drawable.ic_qs_bluetooth)

            statusBarManager?.requestAddTileService(
                component,
                getString(R.string.tile_label),
                icon,
                Executors.newSingleThreadExecutor()
            ) { resultCode ->
                runOnUiThread {
                    when (resultCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                            onResult("Tile added to Quick Settings!")
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                            onResult("Tile is already in Quick Settings")
                        else ->
                            onResult("Tile request completed")
                    }
                }
            }
        } else {
            onResult("Swipe down twice from status bar and tap the pencil icon to add the tile.")
        }
    }

    private fun notifyTileUpdate() {
        try {
            val component = ComponentName(this, BluetoothTileService::class.java)
            TileService.requestListeningState(this, component)
        } catch (ignored: Exception) {
        }
    }

    private fun registerStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }

    private fun unregisterStateReceiver() {
        try {
            unregisterReceiver(stateReceiver)
        } catch (ignored: Exception) {
        }
    }
}
