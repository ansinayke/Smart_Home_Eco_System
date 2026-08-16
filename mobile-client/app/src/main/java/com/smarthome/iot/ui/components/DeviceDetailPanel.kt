package com.smarthome.iot.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.smarthome.iot.data.model.Device
import com.smarthome.iot.data.model.DeviceStatus

@Composable
fun DeviceDetailPanel(
    device: Device,
    onToggle: (Device) -> Unit,
    onToggleSwitch: (Device.MultiSwitchUnit, String, Boolean) -> Unit,
    onSaveSchedule: (Device.Lighting, Boolean, String, String) -> Unit,
    onSaveCutoff: (Device.SmartSwitch, Boolean, Long) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name, 
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${device::class.simpleName} · ${device.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = onClose) { 
                        Text("Close", color = MaterialTheme.colorScheme.primary) 
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            StatusBadge(device.status)
            Spacer(Modifier.height(12.dp))

            when (device) {
                is Device.Lighting,
                is Device.SmartSwitch,
                is Device.MultiSwitchUnit,
                -> {
                    Button(
                        onClick = { onToggle(device) },
                        enabled = device.status == DeviceStatus.ON || device.status == DeviceStatus.OFF,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (device.status == DeviceStatus.ON) "Turn OFF" else "Turn ON")
                    }
                }
                is Device.SecurityCamera -> {
                    Button(
                        onClick = { onToggle(device) },
                        enabled = device.status == DeviceStatus.ON || device.status == DeviceStatus.OFF,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (device.status) {
                                DeviceStatus.ON -> "Stop stream"
                                DeviceStatus.OFF -> "Start stream"
                                else -> device.status.name
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    
                    if (device.status == DeviceStatus.ON && device.mockStreamUri != null) {
                        VideoPlayer(uri = device.mockStreamUri)
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text(
                            "Mock stream: ${device.mockStreamUri ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        device.lastSnapshotTimestamp?.let {
                            Text(
                                "Last snapshot: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is Device.Unknown -> {
                    Text("Unsupported type: ${device.rawType}", color = MaterialTheme.colorScheme.error)
                }
            }

            if (device is Device.MultiSwitchUnit) {
                Spacer(Modifier.height(16.dp))
                Text("Individual Switches", style = MaterialTheme.typography.titleSmall)
                device.switches.values.forEach { sw ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sw.name, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = sw.status == DeviceStatus.ON,
                            onCheckedChange = {
                                onToggleSwitch(device, sw.id, sw.status == DeviceStatus.ON)
                            },
                        )
                    }
                }
            }

            if (device is Device.Lighting) {
                Spacer(Modifier.height(16.dp))
                LightingScheduleEditor(device, onSaveSchedule)
            }

            if (device is Device.SmartSwitch) {
                Spacer(Modifier.height(16.dp))
                SmartSwitchCutoffEditor(device, onSaveCutoff)
                Spacer(Modifier.height(12.dp))
                if (device.autoCutoffEnabled && device.maxOnDurationSeconds != null) {
                    if (device.status == DeviceStatus.ON && (device.lastTurnedOnTimestamp ?: 0L) > 0) {
                        val elapsed = ((System.currentTimeMillis() - device.lastTurnedOnTimestamp!!) / 1000)
                            .coerceAtLeast(0)
                        Text(
                            "Elapsed: ${elapsed}s / ${device.maxOnDurationSeconds}s", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // Force black background on the WebView itself to prevent white flashes
                    setBackgroundColor(android.graphics.Color.BLACK)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <style>
                                body { margin: 0; padding: 0; background: black; overflow: hidden; height: 100vh; display: flex; align-items: center; justify-content: center; }
                                video { width: 100%; height: 100%; object-fit: contain; background: black; }
                            </style>
                        </head>
                        <body>
                            <video id="v" autoplay muted playsinline loop>
                                <source src="$uri" type="application/x-mpegURL">
                                <source src="$uri" type="video/mp4">
                            </video>
                            <script>
                                var v = document.getElementById('v');
                                function play() { v.play().catch(function(e) { console.log(e); }); }
                                v.addEventListener('loadedmetadata', play);
                                play();
                                document.body.onclick = play;
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://local.camera", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun StatusBadge(status: DeviceStatus) {
    FilterChip(
        selected = true,
        onClick = {},
        enabled = false,
        label = { Text(status.name) },
    )
}

@Composable
private fun LightingScheduleEditor(
    device: Device.Lighting,
    onSave: (Device.Lighting, Boolean, String, String) -> Unit,
) {
    var enabled by remember(device.id, device.isScheduled) { mutableStateOf(device.isScheduled) }
    var onTime by remember(device.id, device.schedule) {
        mutableStateOf(device.schedule?.turnOnTime ?: "18:00")
    }
    var offTime by remember(device.id, device.schedule) {
        mutableStateOf(device.schedule?.turnOffTime ?: "22:00")
    }

    Text("Lighting schedule", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enable schedule", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = onTime,
            onValueChange = { onTime = it },
            label = { Text("On (HH:mm)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = offTime,
            onValueChange = { offTime = it },
            label = { Text("Off (HH:mm)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSave(device, enabled, onTime.trim(), offTime.trim()) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save schedule")
    }
}

@Composable
private fun SmartSwitchCutoffEditor(
    device: Device.SmartSwitch,
    onSave: (Device.SmartSwitch, Boolean, Long) -> Unit,
) {
    var enabled by remember(device.id, device.autoCutoffEnabled) { mutableStateOf(device.autoCutoffEnabled) }
    var duration by remember(device.id, device.maxOnDurationSeconds) {
        mutableStateOf(device.maxOnDurationSeconds?.toString() ?: "1800")
    }

    Text("Auto-cutoff settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enable auto-cutoff", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
    if (enabled) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("Max Duration (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSave(device, enabled, duration.toLongOrNull() ?: 1800L) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save settings")
    }
}
