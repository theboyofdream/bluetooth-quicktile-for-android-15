package com.bluetoothquicktile.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetoothquicktile.app.R
import com.bluetoothquicktile.app.ui.theme.ErrContainerDark
import com.bluetoothquicktile.app.ui.theme.ErrContainerLight
import com.bluetoothquicktile.app.ui.theme.ErrRed
import com.bluetoothquicktile.app.ui.theme.ErrTextDark
import com.bluetoothquicktile.app.ui.theme.ErrTextLight
import com.bluetoothquicktile.app.ui.theme.GreenOk
import com.bluetoothquicktile.app.ui.theme.GreenOkContainerDark
import com.bluetoothquicktile.app.ui.theme.GreenOkContainerLight
import com.bluetoothquicktile.app.ui.theme.GreenOkTextDark
import com.bluetoothquicktile.app.ui.theme.WarnContainerDark
import com.bluetoothquicktile.app.ui.theme.WarnContainerLight
import com.bluetoothquicktile.app.ui.theme.WarnOrange
import com.bluetoothquicktile.app.ui.theme.WarnTextDark
import com.bluetoothquicktile.app.ui.theme.WarnTextLight

enum class PermissionUiState {
    GRANTED,
    DENIED,
    BLOCKED
}

/**
 * Material 3 Permission Card faithfully implementing the UX from
 * 'Nearby devices permission · Material 3.html'.
 */
@Composable
fun PermissionCard(
    state: PermissionUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()

    // Determine colors based on state
    val targetCardBg = when (state) {
        PermissionUiState.GRANTED -> MaterialTheme.colorScheme.surfaceContainer
        PermissionUiState.DENIED -> if (dark) WarnContainerDark else WarnContainerLight
        PermissionUiState.BLOCKED -> if (dark) ErrContainerDark else ErrContainerLight
    }

    val targetContentColor = when (state) {
        PermissionUiState.GRANTED -> MaterialTheme.colorScheme.onSurface
        PermissionUiState.DENIED -> if (dark) WarnTextDark else WarnTextLight
        PermissionUiState.BLOCKED -> if (dark) ErrTextDark else ErrTextLight
    }

    val animatedBg by animateColorAsState(targetCardBg, animationSpec = tween(250), label = "permCardBg")

    // The badge background must be legible against the card background chosen above, which is
    // the *Light container in light mode and the *ContainerDark in dark mode. The container
    // colours therefore cannot be reused here: ErrContainerDark is exactly the dark card
    // background, so the badge vanished. The *Text* pair is scheme-flipped too, so each branch
    // takes the lighter of its two accents rather than one fixed colour.
    val iconBg = when (state) {
        // Already a surface tint, so it stays opaque. Dimming it would erase the badge.
        PermissionUiState.GRANTED -> if (dark) GreenOkContainerDark else GreenOkContainerLight
        PermissionUiState.DENIED -> (if (dark) WarnTextDark else WarnOrange).copy(alpha = 0.22f)
        PermissionUiState.BLOCKED -> (if (dark) ErrTextDark else ErrRed).copy(alpha = 0.22f)
    }

    val iconTint = when (state) {
        PermissionUiState.GRANTED -> if (dark) GreenOkTextDark else GreenOk
        PermissionUiState.DENIED -> if (dark) WarnTextDark else WarnOrange
        PermissionUiState.BLOCKED -> if (dark) ErrTextDark else ErrTextLight
    }

    val leadIcon = when (state) {
        PermissionUiState.GRANTED -> AppIcons.Check
        PermissionUiState.DENIED -> AppIcons.Info
        PermissionUiState.BLOCKED -> AppIcons.Lock
    }

    val title = stringResource(
        when (state) {
            PermissionUiState.GRANTED -> R.string.permission_granted_title
            PermissionUiState.DENIED -> R.string.permission_denied_title
            PermissionUiState.BLOCKED -> R.string.permission_blocked_title
        }
    )

    val description = stringResource(
        when (state) {
            PermissionUiState.GRANTED -> R.string.permission_granted_description
            PermissionUiState.DENIED -> R.string.permission_denied_description
            PermissionUiState.BLOCKED -> R.string.permission_blocked_description
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(animatedBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row: Icon + Title/Description
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Squircle Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = leadIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = targetContentColor
                    )
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (state == PermissionUiState.GRANTED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            targetContentColor.copy(alpha = 0.85f)
                        }
                    )
                )
            }
        }

        // What it unlocks (uses section)
        if (state != PermissionUiState.GRANTED) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                PermissionUseRow(
                    icon = AppIcons.PowerSettingsNew,
                    text = stringResource(R.string.permission_unlocks_toggle),
                    textColor = targetContentColor
                )
                PermissionUseRow(
                    icon = AppIcons.Headphones,
                    text = stringResource(R.string.permission_unlocks_names),
                    textColor = targetContentColor
                )
            }
        }

        // How callout (only for Blocked state)
        if (state == PermissionUiState.BLOCKED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(targetContentColor.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_blocked_how),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = targetContentColor
                    )
                )
            }
        }

        // CTA Action Button: Split Button (1 row 2 buttons, exactly matching Hero card)
        if (state != PermissionUiState.GRANTED) {
            val mainLabel = if (state == PermissionUiState.DENIED) {
                stringResource(R.string.action_allow_nearby)
            } else {
                stringResource(R.string.action_open_app_settings)
            }
            val mainAction = if (state == PermissionUiState.DENIED) onRequestPermission else onOpenAppSettings
            val mainIcon = if (state == PermissionUiState.DENIED) AppIcons.NearMe else AppIcons.Lock

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Main Button (pill-left)
                Button(
                    onClick = mainAction,
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
                        containerColor = targetContentColor,
                        contentColor = targetCardBg
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = mainIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = mainLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Side Button (gear icon, pill-right)
                Button(
                    onClick = onOpenAppSettings,
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
                        containerColor = targetContentColor,
                        contentColor = targetCardBg
                    )
                ) {
                    Icon(
                        painter = AppIcons.Settings,
                        contentDescription = stringResource(R.string.action_open_app_settings),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionUseRow(
    icon: Painter,
    text: String,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textColor
            )
        )
    }
}