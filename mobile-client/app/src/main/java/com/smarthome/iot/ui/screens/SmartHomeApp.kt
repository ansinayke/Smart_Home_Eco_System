package com.smarthome.iot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.iot.data.model.Device
import com.smarthome.iot.ui.components.DeviceDetailPanel
import com.smarthome.iot.ui.components.FloorGrid
import com.smarthome.iot.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeApp(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showAddFloor by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Home")
                        Text(
                            if (state.connected) "Live · Firebase connected"
                            else "Offline · reconnecting…",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.connected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Floors") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    label = { Text("Usage") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Alerts") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { showAddFloor = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add floor")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            0 -> DashboardScreen(
                state = state,
                padding = padding,
                onSelectFloor = viewModel::selectFloor,
                onSelectDevice = viewModel::selectDevice,
                onToggle = viewModel::toggleDevice,
                onToggleSwitch = viewModel::toggleSwitch,
                onSaveSchedule = viewModel::updateLightingSchedule,
                onSaveCutoff = viewModel::updateSmartSwitchCutoff,
                onDeleteFloor = viewModel::deleteFloor,
                onAddDevice = viewModel::addDevice,
                onDeleteDevice = viewModel::deleteDevice,
            )
            1 -> UsageScreen(state.usageLogs, state.floors.flatMap { it.devices }, padding)
            2 -> AlertsScreen(state.alerts, padding, viewModel::acknowledgeAlert)
        }
    }

    if (showAddFloor) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFloor = false },
            title = { Text("Add floor plan") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Floor name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFloor(name)
                        showAddFloor = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFloor = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DashboardScreen(
    state: com.smarthome.iot.viewmodel.HomeUiState,
    padding: PaddingValues,
    onSelectFloor: (String) -> Unit,
    onSelectDevice: (String?) -> Unit,
    onToggle: (Device) -> Unit,
    onToggleSwitch: (com.smarthome.iot.data.model.Device.MultiSwitchUnit, String, Boolean) -> Unit,
    onSaveSchedule: (Device.Lighting, Boolean, String, String) -> Unit,
    onSaveCutoff: (Device.SmartSwitch, Boolean, Long) -> Unit,
    onDeleteFloor: (String) -> Unit,
    onAddDevice: (String, String, String, Int, Int, Int, Boolean, Boolean) -> Unit,
    onDeleteDevice: (String, String) -> Unit,
) {
    val floor = state.floors.firstOrNull { it.id == state.selectedFloorId }
    val selected = floor?.devices?.firstOrNull { it.id == state.selectedDeviceId }
    var addDeviceParams by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    if (addDeviceParams != null && floor != null) {
        AddDeviceDialog(
            onDismiss = { addDeviceParams = null },
            onConfirm = { name, type, maxDuration, enableSchedule, autoCutoff -> 
                onAddDevice(floor.id, name, type, addDeviceParams!!.first, addDeviceParams!!.second, maxDuration, enableSchedule, autoCutoff)
                addDeviceParams = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Multi-floor dashboard", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap a device on the grid to control it. State syncs live with the cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.floors, key = { it.id }) { f ->
                    FilterChip(
                        selected = f.id == state.selectedFloorId,
                        onClick = { onSelectFloor(f.id) },
                        label = { Text(f.name) },
                    )
                }
            }
        }

        if (state.loading) {
            item { Text("Loading house…") }
        }

        if (floor != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${floor.name} · ${floor.gridCols}×${floor.gridRows} grid",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { onDeleteFloor(floor.id) }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Floor", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FloorGrid(
                    floor = floor,
                    selectedDeviceId = state.selectedDeviceId,
                    onDeviceClick = { onSelectDevice(it.id) },
                    onAddDeviceClick = { x, y -> addDeviceParams = Pair(x, y) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
            }
        } else if (!state.loading) {
            item { Text("No floors yet. Add one with +.") }
        }

        if (selected != null) {
            item {
                DeviceDetailPanel(
                    device = selected,
                    onToggle = onToggle,
                    onToggleSwitch = onToggleSwitch,
                    onSaveSchedule = onSaveSchedule,
                    onSaveCutoff = onSaveCutoff,
                    onClose = { onSelectDevice(null) },
                    onDelete = { onDeleteDevice(floor!!.id, selected.id); onSelectDevice(null) }
                )
            }
        }
    }
}

@Composable
private fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Boolean, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("LIGHTING") }
    var maxDuration by remember { mutableStateOf("1800") }
    var enableCutoff by remember { mutableStateOf(false) }
    var enableSchedule by remember { mutableStateOf(false) }
    val types = listOf(
        "LIGHTING" to "Lighting",
        "SMART_SWITCH" to "Smart Switch",
        "MULTI_SWITCH" to "Multi-Switch Gang",
        "SECURITY_CAMERA" to "Security Camera"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Device") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                
                Text("Type", style = MaterialTheme.typography.labelMedium)
                types.forEach { (tValue, tName) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { type = tValue }) {
                        RadioButton(selected = type == tValue, onClick = { type = tValue })
                        Text(tName)
                    }
                }
                
                if (type == "SMART_SWITCH") {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableCutoff, onCheckedChange = { enableCutoff = it })
                        Text("Enable Auto-Cutoff Timer")
                    }
                    if (enableCutoff) {
                        OutlinedTextField(
                            value = maxDuration,
                            onValueChange = { maxDuration = it },
                            label = { Text("Max Duration (s)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (type == "LIGHTING") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableSchedule, onCheckedChange = { enableSchedule = it })
                        Text("Enable Default Schedule")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onConfirm(name, type, maxDuration.toIntOrNull() ?: 1800, enableSchedule, enableCutoff) 
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UsageScreen(
    logs: List<com.smarthome.iot.data.model.UsageLog>,
    devices: List<Device>,
    padding: PaddingValues,
) {
    val names = devices.associate { it.id to it.name }
    val fmt = remember {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Usage reporting", style = MaterialTheme.typography.titleLarge)
            Text(
                "Session history from the usage_logs node.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val totalSeconds = logs.sumOf { it.durationSeconds }
            Text(
                "Total tracked on-time: ${formatDuration(totalSeconds)} across ${logs.size} sessions",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (logs.isEmpty()) {
            item { Text("No usage logs yet.") }
        }

        items(logs, key = { "${it.deviceId}-${it.id}" }) { log ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    names[log.deviceId] ?: log.deviceId,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${fmt.format(Date(log.turnedOnAt))} → ${fmt.format(Date(log.turnedOffAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatDuration(log.durationSeconds))
                    if (log.autoCutoff) {
                        Text(
                            "Auto cutoff",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsScreen(
    alerts: List<com.smarthome.iot.data.model.SafetyAlert>,
    padding: PaddingValues,
    onAcknowledge: (String) -> Unit,
) {
    val fmt = remember {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Safety alerts", style = MaterialTheme.typography.titleLarge)
            Text(
                "Written by the Safety Worker when a hazard device is force-cut.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (alerts.isEmpty()) {
            item { Text("No alerts.") }
        }

        items(alerts, key = { it.id }) { alert ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(alert.message, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${alert.deviceId} · ${fmt.format(Date(alert.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!alert.acknowledged) {
                    TextButton(onClick = { onAcknowledge(alert.id) }) {
                        Text("Acknowledge")
                    }
                } else {
                    Text(
                        "Acknowledged",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
