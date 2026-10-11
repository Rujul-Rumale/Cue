package dev.context.app.automation.repository

import dev.context.app.core.Clock
import dev.context.app.model.Automation
import dev.context.app.model.LocationMarker
import dev.context.app.model.Variable
import dev.context.app.storage.room.JsonCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Local JSON backup format. Contains no device identifiers and no secrets - only the
 * automations, saved places and variables the user created. Inspectable by design.
 */
@Serializable
data class Backup(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val automations: List<Automation> = emptyList(),
    val locations: List<LocationMarker> = emptyList(),
    val variables: List<Variable> = emptyList(),
) {
    fun isEmpty(): Boolean = automations.isEmpty() && locations.isEmpty() && variables.isEmpty()
}

object BackupCodec {
    const val FORMAT_VERSION = 1

    fun encode(backup: Backup): String = JsonCodec.json.encodeToString(
        Backup.serializer(),
        backup.copy(version = FORMAT_VERSION, exportedAt = Clock.SYSTEM.nowMillis()),
    )

    /**
     * Decodes and sanitizes an import. Returns null for unparsable or structurally invalid
     * content; a sanitized backup otherwise (blank names and broken entries are dropped).
     */
    fun decode(raw: String): Backup? {
        val parsed = try {
            JsonCodec.json.decodeFromString(Backup.serializer(), raw)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (parsed.version != FORMAT_VERSION) return null
        // Keep action-less automations: the editor allows saving them and the
        // engine logs them honestly as "no actions configured".
        val automations = parsed.automations.filter { it.name.isNotBlank() }
        val locations = parsed.locations.filter {
            it.name.isNotBlank() &&
                it.latitude in -90.0..90.0 &&
                it.longitude in -180.0..180.0 &&
                it.radiusMeters in 1..20_000
        }
        return parsed.copy(automations = automations, locations = locations)
    }

    fun defaultBackup(): Backup = Backup(
        version = FORMAT_VERSION,
        exportedAt = Clock.SYSTEM.nowMillis(),
    )
}