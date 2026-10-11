package dev.context.app.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val triggerJson: String,
    val conditionsJson: String,
    val matchAny: Boolean = false,
    val actionsJson: String,
    val mode: String,
    val cooldownMinutes: Int = 0,
    val createdAt: Long = 0L,
    val lastFiredAt: Long = 0L,
    val lastSignature: String = "",
    val currentState: Boolean = false,
    val useGpsForLocation: Boolean = false,
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 500,
    val createdAt: Long = 0L,
    val cellIds: String = "[]",
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: Long = 0L,
    val automationName: String,
    val timestamp: Long = 0L,
    val result: String,
    val triggerSummary: String = "",
    val evaluationsJson: String = "",
    val executionsJson: String = "",
    val detail: String = "",
)

@Entity(tableName = "variables")
data class VariableEntity(
    @PrimaryKey val key: String,
    val type: String,
    val value: String,
    val updatedAt: Long = 0L,
)