package dev.context.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * Simulates real provider events so automations can be tested without leaving the couch.
 * Gated by developer mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(vm: MainViewModel) {
    val devMode by vm.devMode.collectAsState()
    val locations by vm.locations.collectAsState()
    var threshold by remember { mutableStateOf(20) }
    var placeId by remember { mutableStateOf(locations.firstOrNull()?.id) }
    var batteryEvent by remember { mutableStateOf(TriggerType.BATTERY_BELOW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simulator") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!devMode) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Enable developer mode in Settings to use the simulator.", modifier = Modifier.padding(48.dp))
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle("For all events")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Source: debug", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Fire an event as if the phone really produced it. The engine will evaluate matching automations just like the real thing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Battery")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Threshold", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("$threshold%", color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = threshold.toFloat(), onValueChange = { threshold = it.toInt() }, valueRange = 1f..100f)
            EventButton(vm, "Battery changed", TriggerEvent(TriggerType.BATTERY_CHANGED, mapOf(Param.THRESHOLD to threshold.toString())))
            Row {
                OutlinedButton(
                    onClick = { batteryEvent = TriggerType.BATTERY_BELOW },
                    modifier = Modifier.weight(1f),
                ) { Text("below") }
                OutlinedButton(
                    onClick = { batteryEvent = TriggerType.BATTERY_ABOVE },
                    modifier = Modifier.weight(1f),
                ) { Text("above") }
            }
            EventButton(
                vm,
                if (batteryEvent == TriggerType.BATTERY_BELOW) "Battery below $threshold%"
                else "Battery above $threshold%",
                TriggerEvent(batteryEvent, mapOf(Param.THRESHOLD to threshold.toString())),
            )
            // charging + full
            Row {
                EventButton(vm, "Charging starts", TriggerEvent(TriggerType.CHARGING_START), Modifier.weight(1f))
                EventButton(vm, "Charging stops", TriggerEvent(TriggerType.CHARGING_STOP), Modifier.weight(1f))
            }
            EventButton(vm, "Battery full", TriggerEvent(TriggerType.BATTERY_FULL))

            SectionTitle("Location")
            OptionsField(
                label = "Saved place",
                options = locations.map { it.name },
                selected = placeId?.let { id -> locations.find { it.id == id }?.name },
                onSelected = { name -> placeId = locations.find { it.name == name }?.id },
            )
            val locParams = placeId?.let { mapOf(Param.LOCATION_ID to it.toString()) } ?: emptyMap()
            Row {
                EventButton(vm, "Enter", TriggerEvent(TriggerType.LOCATION_ENTER, locParams), Modifier.weight(1f))
                EventButton(vm, "Leave", TriggerEvent(TriggerType.LOCATION_EXIT, locParams), Modifier.weight(1f))
            }
            EventButton(vm, "Stayed (10 min)", TriggerEvent(TriggerType.LOCATION_DWELL, locParams + (Param.DWELL_MINUTES to "10")))
            Row {
                EventButton(vm, "Cell enter", TriggerEvent(TriggerType.CELL_TOWER_ENTER, locParams), Modifier.weight(1f))
                EventButton(vm, "Cell leave", TriggerEvent(TriggerType.CELL_TOWER_EXIT, locParams), Modifier.weight(1f))
            }

            SectionTitle("Connectivity")
            Row {
                EventButton(vm, "Bluetooth connected", TriggerEvent(TriggerType.BLUETOOTH_CONNECTED), Modifier.weight(1f))
                EventButton(vm, "Bluetooth disconnected", TriggerEvent(TriggerType.BLUETOOTH_DISCONNECTED), Modifier.weight(1f))
            }
            Row {
                EventButton(vm, "Wi-Fi connected", TriggerEvent(TriggerType.WIFI_CONNECTED), Modifier.weight(1f))
                EventButton(vm, "Wi-Fi disconnected", TriggerEvent(TriggerType.WIFI_DISCONNECTED), Modifier.weight(1f))
            }

            SectionTitle("Device")
            Row {
                EventButton(vm, "Screen on", TriggerEvent(TriggerType.SCREEN_ON), Modifier.weight(1f))
                EventButton(vm, "Screen off", TriggerEvent(TriggerType.SCREEN_OFF), Modifier.weight(1f))
            }
            Row {
                EventButton(vm, "Screen unlocked", TriggerEvent(TriggerType.SCREEN_UNLOCKED), Modifier.weight(1f))
                EventButton(vm, "DND enabled", TriggerEvent(TriggerType.DND_ENABLED), Modifier.weight(1f))
            }
            Row {
                EventButton(vm, "DND disabled", TriggerEvent(TriggerType.DND_DISABLED), Modifier.weight(1f))
                EventButton(vm, "Headset plugged", TriggerEvent(TriggerType.HEADSET_PLUGGED), Modifier.weight(1f))
            }
            Row {
                EventButton(vm, "Headset unplugged", TriggerEvent(TriggerType.HEADSET_UNPLUGGED), Modifier.weight(1f))
                EventButton(vm, "Boot complete", TriggerEvent(TriggerType.BOOT_COMPLETED), Modifier.weight(1f))
            }

            SectionTitle("Motion")
            Row {
                EventButton(
                    vm,
                    "Started walking",
                    TriggerEvent(TriggerType.ACTIVITY_START, mapOf(Param.ACTIVITY to "walking")),
                    Modifier.weight(1f),
                )
                EventButton(
                    vm,
                    "Stopped",
                    TriggerEvent(TriggerType.ACTIVITY_END, mapOf(Param.ACTIVITY to "walking")),
                    Modifier.weight(1f),
                )
            }
            Row {
                EventButton(vm, "Significant motion", TriggerEvent(TriggerType.SIGNIFICANT_MOTION, mapOf(Param.AT to "1")), Modifier.weight(1f))
                EventButton(vm, "Picked up", TriggerEvent(TriggerType.DEVICE_PICKUP, mapOf(Param.AT to "1")), Modifier.weight(1f))
            }

            SectionTitle("Messages")
            EventButton(vm, "Notification", TriggerEvent(TriggerType.NOTIFICATION_RECEIVED, mapOf(Param.PACKAGE to "com.example.chat", Param.NOTIF_TITLE to "Hi", Param.AT to "1")))
            EventButton(vm, "SMS", TriggerEvent(TriggerType.SMS_RECEIVED, mapOf(Param.SENDER to "+10000000000", Param.BODY to "hello", Param.AT to "1")))
            EventButton(vm, "Call ringing", TriggerEvent(TriggerType.CALL_STATE_CHANGED, mapOf(Param.STATE to "ringing", Param.AT to "1")))

            SectionTitle("System")
            Row {
                EventButton(vm, "App opened", TriggerEvent(TriggerType.FOREGROUND_APP_CHANGED, mapOf(Param.PACKAGE to "com.android.chrome", Param.AT to "1")), Modifier.weight(1f))
                EventButton(vm, "Volume key", TriggerEvent(TriggerType.VOLUME_KEY_PRESSED, mapOf(Param.KEY to "volume_up", Param.AT to "1")), Modifier.weight(1f))
            }
            Row {
                EventButton(vm, "USB attached", TriggerEvent(TriggerType.USB_CONNECTED, mapOf(Param.AT to "1")), Modifier.weight(1f))
                EventButton(vm, "USB detached", TriggerEvent(TriggerType.USB_DISCONNECTED, mapOf(Param.AT to "1")), Modifier.weight(1f))
            }
            EventButton(vm, "Log pattern", TriggerEvent(TriggerType.LOGCAT_MATCH, mapOf(Param.PATTERN to "test", Param.LINE to "test line", Param.AT to "1")))
            EventButton(vm, "Calendar starts", TriggerEvent(TriggerType.CALENDAR_EVENT_START, mapOf(Param.EVENT_ID to "1", Param.NOTIF_TITLE to "Standup", Param.AT to "1")))

            SectionTitle("Time")
            EventButton(vm, "Now (as if TIME_AT)", TriggerEvent(TriggerType.TIME_AT, nowAsTimeAtParams()))

            Text(
                "Simulated events are recorded in the execution log with source 'debug'.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

private fun nowAsTimeAtParams(): Map<String, String> {
    val now = java.time.LocalDateTime.now()
    return mapOf(
        Param.HOUR to now.hour.toString(),
        Param.MINUTE to now.minute.toString(),
        Param.DAYS to dev.context.app.model.Shared.ALL_DAYS.toString(),
        Param.DATE to now.toLocalDate().toString(),
    )
}

@Composable
fun EventButton(vm: MainViewModel, label: String, event: TriggerEvent, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = { vm.debugEmit(event) }, modifier = modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Filled.FlashOn, contentDescription = null)
        Text("  $label")
    }
}