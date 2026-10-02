package com.bluetoothquicktile.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetoothquicktile.app.R
import com.bluetoothquicktile.app.ui.theme.WarnContainerDark
import com.bluetoothquicktile.app.ui.theme.WarnContainerLight
import com.bluetoothquicktile.app.ui.theme.WarnTextDark
import com.bluetoothquicktile.app.ui.theme.WarnTextLight

@Composable
fun MainScreen(
    isBtOn: Boolean,
    connectedDeviceName: String?,
    isConnecting: Boolean,
    systemPermissionState: PermissionUiState,
    onToggleBluetooth: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAddTileToQuickSettings: () -> Unit,
    onOpenGitHub: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val dark = isSystemInDarkTheme()
    val isPermitted = systemPermissionState == PermissionUiState.GRANTED
    val effectiveBtOn = isBtOn && isPermitted

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Bluetooth Quicktile",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "Toggle Bluetooth from your Quick Settings",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // 2. Hero Card
            val heroBg by animateColorAsState(
                if (effectiveBtOn && isPermitted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = tween(300),
                label = "heroBg"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(heroBg)
                    .padding(horizontal = 20.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Orb & Pulses
                Box(
                    modifier = Modifier.size(148.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (effectiveBtOn && isPermitted) {
                        val transition = rememberInfiniteTransition(label = "pulseTransition")
                        val pulse1Scale by transition.animateFloat(
                            initialValue = 0.70f,
                            targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "pulse1Scale"
                        )
                        val pulse1Alpha by transition.animateFloat(
                            initialValue = 0.32f,
                            targetValue = 0.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "pulse1Alpha"
                        )

                        val pulse2Scale by transition.animateFloat(
                            initialValue = 0.70f,
                            targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, delayMillis = 1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "pulse2Scale"
                        )
                        val pulse2Alpha by transition.animateFloat(
                            initialValue = 0.32f,
                            targetValue = 0.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, delayMillis = 1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "pulse2Alpha"
                        )

                        // Pulse 1
                        Box(
                            modifier = Modifier
                                .size(148.dp)
                                .scale(pulse1Scale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulse1Alpha))
                        )
                        // Pulse 2
                        Box(
                            modifier = Modifier
                                .size(148.dp)
                                .scale(pulse2Scale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulse2Alpha))
                        )
                    }

                    // Central Bluetooth Orb
                    val cornerRadius by animateDpAsState(
                        if (effectiveBtOn && isPermitted) 56.dp else 36.dp,
                        animationSpec = tween(350),
                        label = "orbCornerRadius"
                    )
                    val orbBg by animateColorAsState(
                        if (effectiveBtOn && isPermitted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(300),
                        label = "orbBg"
                    )
                    val orbIconTint by animateColorAsState(
                        if (effectiveBtOn && isPermitted) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.outline,
                        animationSpec = tween(300),
                        label = "orbIconTint"
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val orbScale by animateFloatAsState(
                        if (isPressed) 0.94f else 1.0f,
                        animationSpec = tween(150),
                        label = "orbPressScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .scale(orbScale)
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(orbBg)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (!isPermitted) {
                                    onRequestPermission()
                                } else {
                                    onToggleBluetooth()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = if (!connectedDeviceName.isNullOrEmpty() && effectiveBtOn) {
                            Icons.Rounded.BluetoothConnected
                        } else {
                            Icons.Rounded.Bluetooth
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Bluetooth",
                            tint = orbIconTint,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    // Lock badge if not permitted
                    if (!isPermitted) {
                        val badgeBg = if (dark) WarnContainerDark else WarnContainerLight
                        val badgeTint = if (dark) WarnTextDark else WarnTextLight

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(badgeBg)
                                .border(3.dp, heroBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Permission Needed",
                                tint = badgeTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Status Headlines
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val stateTitle = if (effectiveBtOn && isPermitted) "Bluetooth is on" else "Bluetooth is off"
                    val subTitle = when {
                        !isPermitted -> "Allow nearby devices to control it"
                        effectiveBtOn && !connectedDeviceName.isNullOrEmpty() -> "Connected to $connectedDeviceName"
                        effectiveBtOn && isConnecting -> "Connecting to device…"
                        effectiveBtOn -> "No device connected"
                        else -> "Turn on to connect devices"
                    }

                    Text(
                        text = stateTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (effectiveBtOn && isPermitted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = if (effectiveBtOn && isPermitted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Split Action Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Main Split (Turn On / Turn Off / Allow)
                    Button(
                        onClick = {
                            if (!isPermitted) onRequestPermission() else onToggleBluetooth()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                            topEnd = 6.dp,
                            bottomEnd = 6.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val buttonIcon = when {
                                !isPermitted -> Icons.Rounded.NearMe
                                effectiveBtOn -> Icons.Rounded.PowerSettingsNew
                                else -> Icons.Rounded.Bluetooth
                            }
                            val buttonLabel = when {
                                !isPermitted -> "Allow nearby devices"
                                effectiveBtOn -> "Turn off"
                                else -> "Turn on"
                            }

                            Icon(
                                imageVector = buttonIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = buttonLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Side Split (Settings Gear)
                    Button(
                        onClick = onOpenBluetoothSettings,
                        modifier = Modifier
                            .width(64.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(
                            topStart = 6.dp,
                            bottomStart = 6.dp,
                            topEnd = 28.dp,
                            bottomEnd = 28.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Bluetooth Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 4. Permission Card (Material 3 State Machine from Artifact 2)
            PermissionCard(
                state = systemPermissionState,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings
            )

            // 5. Quick Settings Tile Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Settings tile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Toggle Bluetooth from your shade without opening the app.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Interactive QS Tiles Preview (Bluetooth + Ghost Wi-Fi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bluetooth Quick Tile
                    val tileActive = effectiveBtOn && isPermitted
                    val tileBg by animateColorAsState(
                        if (tileActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(300),
                        label = "tileBg"
                    )
                    val tileTextColor = if (tileActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(tileBg)
                            .clickable {
                                if (!isPermitted) onRequestPermission() else onToggleBluetooth()
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(tileTextColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (!connectedDeviceName.isNullOrEmpty() && tileActive) {
                                    Icons.Rounded.BluetoothConnected
                                } else {
                                    Icons.Rounded.Bluetooth
                                },
                                contentDescription = null,
                                tint = tileTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tileTextColor
                                ),
                                maxLines = 1
                            )
                            val qsSub = when {
                                !isPermitted -> "Needs permission"
                                tileActive && !connectedDeviceName.isNullOrEmpty() -> connectedDeviceName
                                tileActive && isConnecting -> "Connecting…"
                                tileActive -> "On"
                                else -> "Off"
                            }
                            Text(
                                text = qsSub,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = tileTextColor.copy(alpha = 0.80f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Ghost Wi-Fi Tile
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi‑Fi",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                )
                            )
                            Text(
                                text = "Off",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }
                }

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddTileToQuickSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add tile to Quick Settings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenBluetoothSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Open Bluetooth settings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Gestures Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Tap toggles",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Long-press opens settings",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. Educational Footnote
            Text(
                text = "Android 13+ normally blocks apps from switching Bluetooth directly and shows a multi-step prompt. This app targets SDK 32 for its compatibility layer while compiling against SDK 35, so the tile can toggle instantly.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 7. GitHub Link (Bottom Middle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onOpenGitHub,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repository",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "GitHub",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
