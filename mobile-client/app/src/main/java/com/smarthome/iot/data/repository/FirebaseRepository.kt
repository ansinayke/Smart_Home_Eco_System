package com.smarthome.iot.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.smarthome.iot.data.model.Device
import com.smarthome.iot.data.model.DeviceParser
import com.smarthome.iot.data.model.DeviceStatus
import com.smarthome.iot.data.model.FloorPlan
import com.smarthome.iot.data.model.LightSchedule
import com.smarthome.iot.data.model.SafetyAlert
import com.smarthome.iot.data.model.UsageLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth: Firebase Realtime Database.
 * Mobile publishes/subscribes only — no direct peer communication.
 */
class FirebaseRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
) {

    fun observeFloors(): Flow<Result<List<FloorPlan>>> = callbackFlow {
        val ref = database.getReference("house/floors")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(DeviceParser.parseFloors(snapshot)))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeUsageLogs(): Flow<Result<List<UsageLog>>> = callbackFlow {
        val ref = database.getReference("usage_logs")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(DeviceParser.parseUsageLogs(snapshot)))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeAlerts(): Flow<Result<List<SafetyAlert>>> = callbackFlow {
        val ref = database.getReference("alerts")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(DeviceParser.parseAlerts(snapshot)))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeConnected(): Flow<Boolean> = callbackFlow {
        val ref = database.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) == true)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun toggleBinaryDevice(floorId: String, deviceId: String, currentlyOn: Boolean) {
        val deviceRef = database.getReference("house/floors/$floorId/devices/$deviceId")
        val next = if (currentlyOn) DeviceStatus.OFF.name else DeviceStatus.ON.name

        if (currentlyOn) {
            val snap = deviceRef.get().await()
            val turnedOnAt = snap.child("last_turned_on_timestamp").getValue(Long::class.java)
            deviceRef.updateChildren(mapOf("status" to next)).await()
            if (turnedOnAt != null && turnedOnAt > 0L) {
                val now = System.currentTimeMillis()
                val duration = ((now - turnedOnAt) / 1000L).coerceAtLeast(0L)
                database.getReference("usage_logs/$deviceId").push().setValue(
                    mapOf(
                        "turned_on_at" to turnedOnAt,
                        "turned_off_at" to now,
                        "duration_seconds" to duration,
                        "auto_cutoff" to false,
                    ),
                ).await()
            }
        } else {
            deviceRef.updateChildren(
                mapOf(
                    "status" to next,
                    "last_turned_on_timestamp" to ServerValue.TIMESTAMP,
                ),
            ).await()
        }
    }

    suspend fun toggleSwitch(
        floorId: String,
        deviceId: String,
        switchId: String,
        currentlyOn: Boolean,
    ) {
        val next = if (currentlyOn) DeviceStatus.OFF.name else DeviceStatus.ON.name
        database.getReference("house/floors/$floorId/devices/$deviceId/switches/$switchId/status")
            .setValue(next)
            .await()
    }

    suspend fun setLightingSchedule(
        floorId: String,
        deviceId: String,
        enabled: Boolean,
        schedule: LightSchedule?,
    ) {
        val ref = database.getReference("house/floors/$floorId/devices/$deviceId")
        val updates = mutableMapOf<String, Any?>("is_scheduled" to enabled)
        if (enabled && schedule != null) {
            updates["schedule"] = mapOf(
                "turn_on_time" to schedule.turnOnTime,
                "turn_off_time" to schedule.turnOffTime,
            )
        }
        ref.updateChildren(updates).await()
    }

    suspend fun setSmartSwitchCutoff(
        floorId: String,
        deviceId: String,
        enabled: Boolean,
        maxDuration: Long,
    ) {
        val ref = database.getReference("house/floors/$floorId/devices/$deviceId")
        val updates = mutableMapOf<String, Any?>("auto_cutoff_enabled" to enabled)
        if (enabled) {
            updates["max_on_duration_seconds"] = maxDuration
        }
        ref.updateChildren(updates).await()
    }
    suspend fun acknowledgeAlert(alertId: String) {
        database.getReference("alerts/$alertId/acknowledged").setValue(true).await()
    }

    suspend fun removeFloor(floorId: String) {
        database.getReference("house/floors/$floorId").removeValue().await()
    }

    suspend fun addDevice(floorId: String, name: String, type: String, x: Int, y: Int, maxDuration: Int, enableSchedule: Boolean, autoCutoffEnabled: Boolean) {
        val devicesRef = database.getReference("house/floors/$floorId/devices")
        val newRef = devicesRef.push()
        val id = newRef.key ?: return
        
        val deviceMap = mutableMapOf<String, Any>(
            "id" to id,
            "name" to name,
            "type" to type,
            "status" to "OFF",
            "grid_position" to mapOf("x" to x, "y" to y)
        )

        when (type) {
            "SMART_SWITCH" -> {
                deviceMap["auto_cutoff_enabled"] = autoCutoffEnabled
                if (autoCutoffEnabled) {
                    deviceMap["max_on_duration_seconds"] = maxDuration
                }
            }
            "LIGHTING" -> {
                deviceMap["is_scheduled"] = enableSchedule
                if (enableSchedule) {
                    deviceMap["schedule"] = mapOf("turn_on_time" to "18:00", "turn_off_time" to "22:00")
                }
            }
            "MULTI_SWITCH" -> {
                deviceMap["switches"] = mapOf(
                    "sw1" to mapOf("name" to "Switch 1", "status" to "OFF"),
                    "sw2" to mapOf("name" to "Switch 2", "status" to "OFF")
                )
            }
        }

        newRef.setValue(deviceMap).await()
    }

    suspend fun removeDevice(floorId: String, deviceId: String) {
        database.getReference("house/floors/$floorId/devices/$deviceId").removeValue().await()
    }

    suspend fun addFloor(name: String, gridLayout: String = "3x3") {
        val floorsRef = database.getReference("house/floors")
        val newRef = floorsRef.push()
        val id = newRef.key ?: return
        newRef.setValue(
            mapOf(
                "id" to id,
                "name" to name,
                "grid_layout" to gridLayout,
                "devices" to emptyMap<String, Any>(),
            ),
        ).await()
    }

    /**
     * Apply lighting schedules based on local clock (HH:mm).
     * Called reactively when floors update and periodically from the ViewModel.
     */
    suspend fun applyLightingSchedules(floors: List<FloorPlan>, nowHhMm: String) {
        floors.forEach { floor ->
            floor.devices.filterIsInstance<Device.Lighting>().forEach { light ->
                if (!light.isScheduled || light.schedule == null) return@forEach
                val desired = desiredStatusForSchedule(light.schedule, nowHhMm) ?: return@forEach
                if (light.status != desired) {
                    database.getReference("house/floors/${floor.id}/devices/${light.id}/status")
                        .setValue(desired.name)
                        .await()
                }
            }
        }
    }

    private fun desiredStatusForSchedule(schedule: LightSchedule, now: String): DeviceStatus? {
        val on = schedule.turnOnTime
        val off = schedule.turnOffTime
        if (on.isBlank() || off.isBlank()) return null
        return if (on <= off) {
            if (now >= on && now < off) DeviceStatus.ON else DeviceStatus.OFF
        } else {
            // Overnight window e.g. 22:00 → 06:00
            if (now >= on || now < off) DeviceStatus.ON else DeviceStatus.OFF
        }
    }
}
