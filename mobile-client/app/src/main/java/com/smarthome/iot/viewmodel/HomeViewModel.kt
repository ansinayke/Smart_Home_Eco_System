package com.smarthome.iot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.iot.data.model.Device
import com.smarthome.iot.data.model.DeviceStatus
import com.smarthome.iot.data.model.FloorPlan
import com.smarthome.iot.data.model.LightSchedule
import com.smarthome.iot.data.model.SafetyAlert
import com.smarthome.iot.data.model.UsageLog
import com.smarthome.iot.data.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HomeUiState(
    val floors: List<FloorPlan> = emptyList(),
    val selectedFloorId: String? = null,
    val usageLogs: List<UsageLog> = emptyList(),
    val alerts: List<SafetyAlert> = emptyList(),
    val connected: Boolean = true,
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val selectedDeviceId: String? = null,
)

class HomeViewModel(
    private val repository: FirebaseRepository = FirebaseRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var scheduleJob: Job? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        observeAll()
        startScheduleTicker()
    }

    private fun observeAll() {
        viewModelScope.launch {
            repository.observeConnected().collect { connected ->
                _uiState.update { it.copy(connected = connected) }
            }
        }
        viewModelScope.launch {
            repository.observeFloors().collect { result ->
                result
                    .onSuccess { floors ->
                        _uiState.update { state ->
                            val selected = state.selectedFloorId
                                ?.takeIf { id -> floors.any { it.id == id } }
                                ?: floors.firstOrNull()?.id
                            state.copy(
                                floors = floors,
                                selectedFloorId = selected,
                                loading = false,
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                errorMessage = err.message ?: "Failed to load house data",
                            )
                        }
                    }
            }
        }
        viewModelScope.launch {
            repository.observeUsageLogs().collect { result ->
                result.onSuccess { logs ->
                    _uiState.update { it.copy(usageLogs = logs) }
                }
            }
        }
        viewModelScope.launch {
            repository.observeAlerts().collect { result ->
                result.onSuccess { alerts ->
                    _uiState.update { it.copy(alerts = alerts) }
                }
            }
        }
    }

    private fun startScheduleTicker() {
        scheduleJob?.cancel()
        scheduleJob = viewModelScope.launch {
            while (isActive) {
                val floors = _uiState.value.floors
                if (floors.isNotEmpty()) {
                    val now = LocalTime.now().format(timeFormatter)
                    runCatching { repository.applyLightingSchedules(floors, now) }
                }
                delay(30_000L)
            }
        }
    }

    fun selectFloor(floorId: String) {
        _uiState.update { it.copy(selectedFloorId = floorId, selectedDeviceId = null) }
    }

    fun selectDevice(deviceId: String?) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun toggleDevice(device: Device) {
        if (device is Device.SecurityCamera && device.status == DeviceStatus.DISCONNECTED) return
        val currentlyOn = device.status == DeviceStatus.ON
        viewModelScope.launch {
            runCatching {
                repository.toggleBinaryDevice(device.floorId, device.id, currentlyOn)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun toggleSwitch(device: Device.MultiSwitchUnit, switchId: String, currentlyOn: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.toggleSwitch(device.floorId, device.id, switchId, currentlyOn)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateLightingSchedule(device: Device.Lighting, enabled: Boolean, on: String, off: String) {
        viewModelScope.launch {
            runCatching {
                repository.setLightingSchedule(
                    floorId = device.floorId,
                    deviceId = device.id,
                    enabled = enabled,
                    schedule = LightSchedule(on, off),
                )
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun updateSmartSwitchCutoff(device: Device.SmartSwitch, enabled: Boolean, maxDuration: Long) {
        viewModelScope.launch {
            runCatching {
                repository.setSmartSwitchCutoff(
                    floorId = device.floorId,
                    deviceId = device.id,
                    enabled = enabled,
                    maxDuration = maxDuration,
                )
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            runCatching { repository.acknowledgeAlert(alertId) }
        }
    }

    fun addFloor(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addFloor(name.trim()) }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun deleteFloor(floorId: String) {
        viewModelScope.launch {
            runCatching { repository.removeFloor(floorId) }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun addDevice(floorId: String, name: String, type: String, x: Int, y: Int, maxDuration: Int, enableSchedule: Boolean, autoCutoffEnabled: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addDevice(floorId, name.trim(), type, x, y, maxDuration, enableSchedule, autoCutoffEnabled) }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun deleteDevice(floorId: String, deviceId: String) {
        viewModelScope.launch {
            runCatching { repository.removeDevice(floorId, deviceId) }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
