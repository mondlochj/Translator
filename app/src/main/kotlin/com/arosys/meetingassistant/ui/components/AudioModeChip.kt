package com.arosys.meetingassistant.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arosys.meetingassistant.core.interfaces.AudioMode

@Composable
fun AudioModeChip(
    mode: AudioMode,
    bluetoothConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when (mode) {
        AudioMode.TEXT_ONLY     -> "Text" to Icons.Filled.NotificationsOff
        AudioMode.ALL_SPEECH    -> "All"  to Icons.Filled.VolumeUp
        AudioMode.PRIORITY_ONLY -> "Priority" to Icons.Filled.NotificationsActive
    }

    val btIcon = if (bluetoothConnected) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled
    val btTint = if (bluetoothConnected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    AssistChip(
        onClick = onClick,
        label = {
            Text(label, fontSize = 11.sp)
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        },
        trailingIcon = {
            Icon(btIcon, contentDescription = if (bluetoothConnected) "BT connected" else "BT off",
                modifier = Modifier.size(14.dp), tint = btTint)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    )
}
