package dev.context.app.automation.repository

import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.Automation
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ConditionType
import dev.context.app.model.LocationMarker
import dev.context.app.model.Param
import dev.context.app.model.TriggerSpec
import dev.context.app.model.TriggerType
import dev.context.app.model.Variable
import dev.context.app.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    @Test
    fun encodeDecode_roundTrips() {
        val backup = Backup(
            automations = listOf(
                Automation(
                    name = "Low battery alert",
                    trigger = TriggerSpec(TriggerType.BATTERY_BELOW, params = mapOf(Param.THRESHOLD to "20")),
                    conditions = listOf(ConditionSpec(ConditionType.DAY_OF_WEEK, params = mapOf(Param.DAYS to "127"))),
                    actions = listOf(
                        ActionSpec(ActionType.SHOW_NOTIFICATION, params = mapOf(Param.TITLE to "Low battery", Param.TEXT to "Charge me")),
                        ActionSpec(ActionType.INCREMENT_VARIABLE, params = mapOf(Param.VARIABLE to "triggerCount")),
                    ),
                ),
            ),
            locations = listOf(
                LocationMarker(name = "Home", latitude = 47.60, longitude = -122.33, radiusMeters = 500),
            ),
            variables = listOf(
                Variable(key = "triggerCount", type = VariableType.INTEGER, value = "3"),
            ),
        )

        val encoded = BackupCodec.encode(backup)
        val decoded = BackupCodec.decode(encoded)
        assertNotNull(decoded)
        decoded!!
        assertEquals(1, decoded.version)
        assertEquals(backup.automations, decoded.automations)
        assertEquals(backup.locations, decoded.locations)
        assertEquals(backup.variables, decoded.variables)
        assertEquals("Low battery alert", decoded.automations[0].name)
    }

    @Test
    fun decode_invalidJsonReturnsNull() {
        assertNull(BackupCodec.decode("{not json"))
        assertNull(BackupCodec.decode(""))
    }

    @Test
    fun decode_dropsStructurallyBrokenEntries() {
        val encoded = BackupCodec.encode(
            Backup(
                automations = listOf(
                    Automation(name = "", trigger = TriggerSpec(TriggerType.BOOT_COMPLETED), actions = listOf(ActionSpec(ActionType.LOG_MESSAGE))),
                    Automation(name = "Good", trigger = TriggerSpec(TriggerType.BOOT_COMPLETED), actions = listOf(ActionSpec(ActionType.LOG_MESSAGE))),
                ),
                locations = listOf(
                    LocationMarker(name = "Bad", latitude = 999.0, longitude = 0.0, radiusMeters = 500),
                    LocationMarker(name = "Ok", latitude = 10.0, longitude = 20.0, radiusMeters = 500),
                ),
            ),
        )
        val decoded = BackupCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(listOf("Good"), decoded!!.automations.map { it.name })
        assertEquals(listOf("Ok"), decoded.locations.map { it.name })
    }

    @Test
    fun decode_keepsAutomationsWithoutActions() {
        val encoded = BackupCodec.encode(
            Backup(
                automations = listOf(
                    Automation(name = "Empty", trigger = TriggerSpec(TriggerType.BOOT_COMPLETED)),
                ),
            ),
        )
        val decoded = BackupCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(listOf("Empty"), decoded!!.automations.map { it.name })
    }

    @Test
    fun wrongVersionRejected() {
        val encoded = BackupCodec.encode(Backup()).replace("\"version\":1", "\"version\":999")
        assertNull(BackupCodec.decode(encoded))
    }

    @Test
    fun defaultBackup_isEmpty() {
        assertTrue(BackupCodec.defaultBackup().isEmpty())
    }
}