package com.smarthome.iot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarthome.iot.data.model.Device
import com.smarthome.iot.data.model.DeviceStatus
import com.smarthome.iot.data.model.FloorPlan

@Composable
fun FloorGrid(
    floor: FloorPlan,
    selectedDeviceId: String?,
    onDeviceClick: (Device) -> Unit,
    onAddDeviceClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridLine = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val floorFill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(floorFill, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        val cellW = maxWidth / floor.gridCols
        val cellH = maxHeight.coerceAtLeast(280.dp) / floor.gridRows

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = maxWidth, height = cellH * floor.gridRows),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                for (c in 0..floor.gridCols) {
                    val x = size.width * c / floor.gridCols
                    drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), 2f, pathEffect = dash)
                }
                for (r in 0..floor.gridRows) {
                    val y = size.height * r / floor.gridRows
                    drawLine(gridLine, Offset(0f, y), Offset(size.width, y), 2f, pathEffect = dash)
                }
            }

            for (x in 0 until floor.gridCols) {
                for (y in 0 until floor.gridRows) {
                    val device = floor.devices.find { it.gridPosition.x == x && it.gridPosition.y == y }
                    if (device != null) {
                        DeviceMarker(
                            device = device,
                            selected = device.id == selectedDeviceId,
                            onClick = { onDeviceClick(device) },
                            modifier = Modifier
                                .offset(x = cellW * x + cellW / 2 - 36.dp, y = cellH * y + cellH / 2 - 36.dp)
                                .size(72.dp),
                        )
                    } else {
                        EmptySlotMarker(
                            onClick = { onAddDeviceClick(x, y) },
                            modifier = Modifier
                                .offset(x = cellW * x + cellW / 2 - 24.dp, y = cellH * y + cellH / 2 - 24.dp)
                                .size(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlotMarker(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DeviceMarker(
    device: Device,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = statusColor(device.status)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(statusColor.copy(alpha = 0.2f), CircleShape)
                .border(
                    width = if (selected) 3.dp else 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else statusColor,
                    shape = CircleShape,
                ),
        ) {
            Text(
                text = typeShort(device),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun typeShort(device: Device): String = when (device) {
    is Device.Lighting -> "LT"
    is Device.MultiSwitchUnit -> "MS"
    is Device.SmartSwitch -> "SW"
    is Device.SecurityCamera -> "CAM"
    is Device.Unknown -> "?"
}

@Composable
fun statusColor(status: DeviceStatus): Color = when (status) {
    DeviceStatus.ON -> Color(0xFF059669)
    DeviceStatus.OFF -> Color(0xFF64748B)
    DeviceStatus.ERROR -> Color(0xFFDC2626)
    DeviceStatus.DISCONNECTED -> Color(0xFFD97706)
}
