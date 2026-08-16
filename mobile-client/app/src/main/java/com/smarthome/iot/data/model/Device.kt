package com.smarthome.iot.data.model

/**
 * Heterogeneous device profiles modeled as a sealed hierarchy
 * (docs/03-tech-stack-and-practices.md).
 */
sealed class Device {
    abstract val id: String
    abstract val name: String
    abstract val status: DeviceStatus
    abstract val gridPosition: GridPosition
    abstract val floorId: String



    data class Lighting(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus,
        override val gridPosition: GridPosition,
        override val floorId: String,
        val isScheduled: Boolean = false,
        val schedule: LightSchedule? = null,
    ) : Device()

    data class MultiSwitchUnit(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus,
        override val gridPosition: GridPosition,
        override val floorId: String,
        val switches: Map<String, SwitchNode>,
    ) : Device()

    data class SmartSwitch(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus,
        override val gridPosition: GridPosition,
        override val floorId: String,
        val autoCutoffEnabled: Boolean,
        val maxOnDurationSeconds: Long?,
        val lastTurnedOnTimestamp: Long?,
    ) : Device()

    data class SecurityCamera(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus,
        override val gridPosition: GridPosition,
        override val floorId: String,
        val mockStreamUri: String? = null,
        val lastSnapshotTimestamp: Long? = null,
    ) : Device()

    /** Fallback for unknown/future types so parsing never crashes. */
    data class Unknown(
        override val id: String,
        override val name: String,
        override val status: DeviceStatus,
        override val gridPosition: GridPosition,
        override val floorId: String,
        val rawType: String,
    ) : Device()
}

enum class DeviceStatus {
    ON,
    OFF,
    ERROR,
    DISCONNECTED;

    companion object {
        fun from(raw: String?): DeviceStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: DISCONNECTED
    }
}

data class GridPosition(val x: Int, val y: Int)

data class LightSchedule(
    val turnOnTime: String,
    val turnOffTime: String,
)

data class SwitchNode(
    val id: String,
    val name: String,
    val status: DeviceStatus,
)

data class FloorPlan(
    val id: String,
    val name: String,
    val gridCols: Int,
    val gridRows: Int,
    val devices: List<Device>,
)

data class UsageLog(
    val id: String,
    val deviceId: String,
    val turnedOnAt: Long,
    val turnedOffAt: Long,
    val durationSeconds: Long,
    val autoCutoff: Boolean,
)

data class SafetyAlert(
    val id: String,
    val deviceId: String,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean,
)
