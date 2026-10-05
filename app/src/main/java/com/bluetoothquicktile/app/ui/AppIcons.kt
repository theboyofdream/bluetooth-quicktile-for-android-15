package com.bluetoothquicktile.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.bluetoothquicktile.app.R

/**
 * The icons this app draws, resolved from one place.
 *
 * The UI depends on `material-icons-core` (49 glyphs) plus a handful of local vector drawables,
 * rather than `material-icons-extended`, whose AAR is roughly 34 MB unpacked. That library shipped
 * around two thousand glyphs, of which this app used eleven.
 *
 * Everything is exposed as [Painter] so callers do not need to know which source a glyph came from,
 * and do not have to choose between `Icon(imageVector = ...)` and `Icon(painter = ...)`.
 *
 * Material Design path data for the local vectors is Apache License 2.0.
 */
object AppIcons {

    // From material-icons-core.
    val Add: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Add)

    val Check: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Check)

    val Info: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Info)

    val Lock: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Lock)

    val Settings: Painter
        @Composable get() = rememberVectorPainter(Icons.Rounded.Settings)

    // Local vectors, for the glyphs material-icons-core does not ship.
    val Bluetooth: Painter
        @Composable get() = painterResource(R.drawable.ic_qs_bluetooth)

    val BluetoothConnected: Painter
        @Composable get() = painterResource(R.drawable.ic_qs_bluetooth_connected)

    val Headphones: Painter
        @Composable get() = painterResource(R.drawable.ic_headphones)

    val NearMe: Painter
        @Composable get() = painterResource(R.drawable.ic_near_me)

    val PowerSettingsNew: Painter
        @Composable get() = painterResource(R.drawable.ic_power_settings_new)

    val Wifi: Painter
        @Composable get() = painterResource(R.drawable.ic_wifi)
}