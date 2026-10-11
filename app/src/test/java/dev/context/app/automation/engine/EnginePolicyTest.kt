package dev.context.app.automation.engine

import dev.context.app.model.Param
import dev.context.app.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePolicyTest {

    @Test
    fun transitionalClassification() {
        assertTrue(EnginePolicy.isTransitional(TriggerType.LOCATION_ENTER))
        assertTrue(EnginePolicy.isTransitional(TriggerType.LOCATION_EXIT))
        assertTrue(EnginePolicy.isTransitional(TriggerType.LOCATION_DWELL))
        assertTrue(EnginePolicy.isTransitional(TriggerType.BATTERY_BELOW))
        assertTrue(EnginePolicy.isTransitional(TriggerType.BLUETOOTH_CONNECTED))
        // BATTERY_CHANGED is a recurring level event, not a latch-once situation.
        assertFalse(EnginePolicy.isTransitional(TriggerType.BATTERY_CHANGED))
        assertFalse(EnginePolicy.isTransitional(TriggerType.TIME_AT))
        assertFalse(EnginePolicy.isTransitional(TriggerType.BOOT_COMPLETED))
    }

    @Test
    fun resetEventsAreSymmetric() {
        assertEquals(TriggerType.LOCATION_EXIT, EnginePolicy.resetEvent(TriggerType.LOCATION_ENTER))
        assertEquals(TriggerType.LOCATION_ENTER, EnginePolicy.resetEvent(TriggerType.LOCATION_EXIT))
        assertEquals(TriggerType.LOCATION_EXIT, EnginePolicy.resetEvent(TriggerType.LOCATION_DWELL))
        assertEquals(TriggerType.CHARGING_STOP, EnginePolicy.resetEvent(TriggerType.CHARGING_START))
        assertEquals(TriggerType.CHARGING_START, EnginePolicy.resetEvent(TriggerType.CHARGING_STOP))
        assertEquals(TriggerType.WIFI_DISCONNECTED, EnginePolicy.resetEvent(TriggerType.WIFI_CONNECTED))
        assertEquals(TriggerType.WIFI_CONNECTED, EnginePolicy.resetEvent(TriggerType.WIFI_DISCONNECTED))
        assertEquals(TriggerType.SCREEN_OFF, EnginePolicy.resetEvent(TriggerType.SCREEN_ON))
        assertEquals(TriggerType.SCREEN_ON, EnginePolicy.resetEvent(TriggerType.SCREEN_OFF))
        // Non-transitional and one-way triggers have no reset counterpart
        assertNull(EnginePolicy.resetEvent(TriggerType.BATTERY_CHANGED))
        assertNull(EnginePolicy.resetEvent(TriggerType.TIME_AT))
        assertNull(EnginePolicy.resetEvent(TriggerType.BOOT_COMPLETED))
        assertNull(EnginePolicy.resetEvent(TriggerType.BATTERY_BELOW))
        assertNull(EnginePolicy.resetEvent(TriggerType.BATTERY_ABOVE))
        assertEquals(TriggerType.SCREEN_ON, EnginePolicy.resetEvent(TriggerType.SCREEN_UNLOCKED))
    }

    @Test
    fun paramsMatchForWatchTypes() {
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.LOCATION_ENTER,
                mapOf(Param.LOCATION_ID to "5"),
                mapOf(Param.LOCATION_ID to "5"),
            ),
        )
        assertFalse(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.WIFI_CONNECTED,
                mapOf(Param.SSID to "Home"),
                mapOf(Param.SSID to "Office"),
            ),
        )
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.WIFI_CONNECTED,
                mapOf(Param.SSID to "home"),
                mapOf(Param.SSID to "HOME"),
            ),
        )
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.BLUETOOTH_CONNECTED,
                mapOf(Param.DEVICE_NAME to "buds"),
                mapOf(Param.DEVICE_NAME to "BUDS"),
            ),
        )
    }

    @Test
    fun paramsMatch_specWithoutTargetMatchesAnyEvent() {
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.LOCATION_DWELL,
                emptyMap(),
                mapOf(Param.LOCATION_ID to "42"),
            ),
        )
    }

    @Test
    fun locationFamilies() {
        assertEquals(0, EnginePolicy.locationFamily(TriggerType.LOCATION_ENTER))
        assertEquals(0, EnginePolicy.locationFamily(TriggerType.CELL_TOWER_ENTER))
        assertEquals(1, EnginePolicy.locationFamily(TriggerType.LOCATION_EXIT))
        assertEquals(1, EnginePolicy.locationFamily(TriggerType.CELL_TOWER_EXIT))
        assertEquals(2, EnginePolicy.locationFamily(TriggerType.LOCATION_DWELL))
        assertEquals(2, EnginePolicy.locationFamily(TriggerType.CELL_TOWER_DWELL))
        assertEquals(-1, EnginePolicy.locationFamily(TriggerType.TIME_AT))
        assertTrue(EnginePolicy.isTransitional(TriggerType.CELL_TOWER_ENTER))
        assertTrue(EnginePolicy.isTransitional(TriggerType.USB_CONNECTED))
        assertFalse(EnginePolicy.isTransitional(TriggerType.SIGNIFICANT_MOTION))
        assertFalse(EnginePolicy.isTransitional(TriggerType.NOTIFICATION_RECEIVED))
        assertFalse(EnginePolicy.isTransitional(TriggerType.SMS_RECEIVED))
        assertEquals(TriggerType.CELL_TOWER_EXIT, EnginePolicy.resetEvent(TriggerType.CELL_TOWER_ENTER))
        assertEquals(TriggerType.USB_DISCONNECTED, EnginePolicy.resetEvent(TriggerType.USB_CONNECTED))
    }

    @Test
    fun paramsMatchForNewTypes() {
        // Notification: package + keyword + category filters
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.NOTIFICATION_RECEIVED,
                mapOf(Param.PACKAGE to "com.chat", Param.TITLE_CONTAINS to "hi"),
                mapOf(Param.PACKAGE to "com.chat", Param.NOTIF_TITLE to "Hi there", Param.NOTIF_TEXT to ""),
            ),
        )
        assertFalse(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.NOTIFICATION_RECEIVED,
                mapOf(Param.PACKAGE to "com.chat"),
                mapOf(Param.PACKAGE to "com.other", Param.NOTIF_TITLE to "Hi"),
            ),
        )
        // SMS sender/body contains
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.SMS_RECEIVED,
                mapOf(Param.SENDER to "bank"),
                mapOf(Param.SENDER to "+91-BANK", Param.BODY to "otp"),
            ),
        )
        // Cell place id must match
        assertFalse(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.CELL_TOWER_ENTER,
                mapOf(Param.LOCATION_ID to "5"),
                mapOf(Param.LOCATION_ID to "6"),
            ),
        )
        // Foreground app package match is case-insensitive, blank matches any
        assertTrue(
            EnginePolicy.paramsMatchAutomation(
                TriggerType.FOREGROUND_APP_CHANGED,
                emptyMap(),
                mapOf(Param.PACKAGE to "com.anything"),
            ),
        )
    }

    @Test
    fun triggersWithNoConcreteTargetDoNotNeedParamsMatch() {
        assertFalse(EnginePolicy.triggerNeedsParamsMatch(TriggerType.TIME_AT))
        assertTrue(EnginePolicy.triggerNeedsParamsMatch(TriggerType.ACTIVITY_START))
        assertNull(EnginePolicy.resetEvent(TriggerType.TIME_AT))
    }
}