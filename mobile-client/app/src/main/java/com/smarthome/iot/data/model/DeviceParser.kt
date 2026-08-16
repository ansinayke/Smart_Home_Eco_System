package com.smarthome.iot.data.model

import com.google.firebase.database.DataSnapshot

object DeviceParser {

    fun parseFloors(snapshot: DataSnapshot): List<FloorPlan> {
        if (!snapshot.exists()) return emptyList()
        return snapshot.children.mapNotNull { floorSnap ->
            val floorId = floorSnap.key ?: return@mapNotNull null
            val name = floorSnap.child("name").getValue(String::class.java) ?: floorId
            val layout = floorSnap.child("grid_layout").getValue(String::class.java) ?: "3x3"
            val (cols, rows) = parseGridLayout(layout)
            val devices = floorSnap.child("devices").children.mapNotNull { deviceSnap ->
                parseDevice(deviceSnap, floorId)
            }
            FloorPlan(
                id = floorSnap.child("id").getValue(String::class.java) ?: floorId,
                name = name,
                gridCols = cols,
                gridRows = rows,
                devices = devices,
            )
        }.sortedBy { it.id }
    }

    fun parseUsageLogs(snapshot: DataSnapshot): List<UsageLog> {
        if (!snapshot.exists()) return emptyList()
        val logs = mutableListOf<UsageLog>()
        snapshot.children.forEach { deviceNode ->
            val deviceId = deviceNode.key ?: return@forEach
            deviceNode.children.forEach { logSnap ->
                val logId = logSnap.key ?: return@forEach
                logs += UsageLog(
                    id = logId,
                    deviceId = deviceId,
                    turnedOnAt = logSnap.child("turned_on_at").asLong(),
                    turnedOffAt = logSnap.child("turned_off_at").asLong(),
                    durationSeconds = logSnap.child("duration_seconds").asLong(),
                    autoCutoff = logSnap.child("auto_cutoff").getValue(Boolean::class.java) ?: false,
                )
            }
        }
        return logs.sortedByDescending { it.turnedOffAt }
    }

    fun parseAlerts(snapshot: DataSnapshot): List<SafetyAlert> {
        if (!snapshot.exists()) return emptyList()
        return snapshot.children.mapNotNull { alertSnap ->
            val id = alertSnap.key ?: return@mapNotNull null
            SafetyAlert(
                id = id,
                deviceId = alertSnap.child("device_id").getValue(String::class.java).orEmpty(),
                message = alertSnap.child("message").getValue(String::class.java).orEmpty(),
                timestamp = alertSnap.child("timestamp").asLong(),
                acknowledged = alertSnap.child("acknowledged").getValue(Boolean::class.java) ?: false,
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun parseDevice(snap: DataSnapshot, floorId: String): Device? {
        val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
        val name = snap.child("name").getValue(String::class.java) ?: id
        val type = snap.child("type").getValue(String::class.java).orEmpty()
        val status = DeviceStatus.from(snap.child("status").getValue(String::class.java))
        val x = snap.child("grid_position").child("x").getValue(Int::class.java) ?: 0
        val y = snap.child("grid_position").child("y").getValue(Int::class.java) ?: 0
        val position = GridPosition(x, y)

        return when (type) {
            "LIGHTING" -> {
                val scheduled = snap.child("is_scheduled").getValue(Boolean::class.java) ?: false
                val scheduleSnap = snap.child("schedule")
                val schedule = if (scheduleSnap.exists()) {
                    LightSchedule(
                        turnOnTime = scheduleSnap.child("turn_on_time").getValue(String::class.java).orEmpty(),
                        turnOffTime = scheduleSnap.child("turn_off_time").getValue(String::class.java).orEmpty(),
                    )
                } else null
                Device.Lighting(id, name, status, position, floorId, scheduled, schedule)
            }
            "MULTI_SWITCH" -> {
                val switches = snap.child("switches").children.associate { sw ->
                    val swId = sw.key ?: return@associate Pair("", SwitchNode("", "", DeviceStatus.OFF))
                    swId to SwitchNode(
                        id = swId,
                        name = sw.child("name").getValue(String::class.java) ?: swId,
                        status = DeviceStatus.from(sw.child("status").getValue(String::class.java)),
                    )
                }.filterKeys { it.isNotEmpty() }
                Device.MultiSwitchUnit(id, name, status, position, floorId, switches)
            }
            "SMART_SWITCH" -> Device.SmartSwitch(
                id = id,
                name = name,
                status = status,
                gridPosition = position,
                floorId = floorId,
                autoCutoffEnabled = snap.child("auto_cutoff_enabled").getValue(Boolean::class.java) ?: false,
                maxOnDurationSeconds = snap.child("max_on_duration_seconds").getValue(Long::class.java),
                lastTurnedOnTimestamp = snap.child("last_turned_on_timestamp").getValue(Long::class.java),
            )
            "SECURITY_CAMERA" -> Device.SecurityCamera(
                id = id,
                name = name,
                status = status,
                gridPosition = position,
                floorId = floorId,
                mockStreamUri = snap.child("mock_stream_uri").getValue(String::class.java),
                lastSnapshotTimestamp = snap.child("last_snapshot_timestamp").getValue(Long::class.java),
            )
            else -> Device.Unknown(id, name, status, position, floorId, type)
        }
    }

    private fun parseGridLayout(layout: String): Pair<Int, Int> {
        val parts = layout.lowercase().split("x")
        val cols = parts.getOrNull(0)?.toIntOrNull() ?: 3
        val rows = parts.getOrNull(1)?.toIntOrNull() ?: 3
        return cols to rows
    }

    private fun DataSnapshot.asLong(): Long {
        val number = getValue(Long::class.java)
        if (number != null) return number
        val intVal = getValue(Int::class.java)
        if (intVal != null) return intVal.toLong()
        return getValue(String::class.java)?.toLongOrNull() ?: 0L
    }
}
