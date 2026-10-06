package com.bluetoothquicktile.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
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
 *    - Tap to set up, shown only until BLUETOOTH_CONNECT is granted
 *    - Unavailable, shown only on a device with no Bluetooth radio
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

        /**
         * Window used to collapse a burst of broadcasts into a single refresh.
         *
         * One ACL connect arrives as `ACL_CONNECTED` plus an A2DP and a headset
         * `CONNECTION_STATE_CHANGED`, and a disconnect as the same three again. Every refresh
         * that reaches `STATE_ON` opens fresh A2DP and Headset profile proxies over binder, so
         * without a coalescing window a single physical connection cost several proxy round
         * trips, each able to leave a 1.5 s query outstanding.
         */
        private const val REFRESH_COALESCE_MS = 150L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var reconcileRunnable: Runnable? = null

    /**
     * Refresh entry point, debounced so a burst of broadcasts costs one profile-proxy round
     * trip instead of one per action.
     */
    private val pendingRefresh = Runnable { doRefreshTile() }

    override fun onTileAdded() {
        super.onTileAdded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile added to Quick Settings panel")
        refreshTile()
    }

    /**
     * The tile refreshes itself in response to Bluetooth state through the manifest-declared
     * [BluetoothStateReceiver], which calls `requestListeningState` for every adapter and profile
     * change. Registering a second receiver here would receive those same broadcasts and refresh
     * again, so the two paths together doubled the proxy traffic on every connection event.
     */
    override fun onStartListening() {
        super.onStartListening()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile started listening")
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (BuildConfig.DEBUG) Log.d(TAG, "Tile stopped listening")
        cancelPendingWork()
    }

    override fun onTileRemoved() {
        cancelPendingWork()
        super.onTileRemoved()
    }

    override fun onDestroy() {
        // onStopListening is not guaranteed to run before the service is torn down, so anything
        // queued is cleaned up here as well.
        cancelPendingWork()
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
     * Requests a tile refresh, coalescing any that are already queued.
     */
    private fun refreshTile() {
        handler.removeCallbacks(pendingRefresh)
        handler.postDelayed(pendingRefresh, REFRESH_COALESCE_MS)
    }

    /**
     * Queries Bluetooth state and updates Tile label, subtitle, state, and icon.
     */
    private fun doRefreshTile() {
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

    private fun cancelPendingWork() {
        cancelPendingReconcile()
        handler.removeCallbacks(pendingRefresh)
    }
}