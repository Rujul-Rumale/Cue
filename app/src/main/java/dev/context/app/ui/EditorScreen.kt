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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ConditionType
import dev.context.app.model.Param
import dev.context.app.model.Shared
import dev.context.app.model.TriggerCategory
import dev.context.app.model.TriggerSpec
import dev.context.app.model.TriggerType

sealed interface EditorDialog {
    data class TriggerTypeIn(val category: TriggerCategory) : EditorDialog
    data class ConditionTypeIn(val editing: ConditionSpec?, val index: Int) : EditorDialog
    data class ActionTypeIn(val editing: ActionSpec?, val index: Int) : EditorDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: MainViewModel, automationId: Long) {
    val draft = vm.draft.collectAsState().value ?: return
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val locations by vm.locations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (automationId == 0L) "New automation" else "Edit automation") },
                navigationIcon = {
                    IconButton(onClick = { dialog = null; vm.cancelEditor() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ---- WHEN --------------------------------------------------
            SectionTitle("When")
            if (draft.trigger == null) {
                Text("Choose what starts this automation:", style = MaterialTheme.typography.bodyMedium)
                TriggerCategoryButtons { cat -> dialog = EditorDialog.TriggerTypeIn(cat) }
            } else {
                val t = draft.trigger
                if (t != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.type.shortLabel(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { draft.trigger = null; vm.updateDraft(draft) }) {
                                    Text("Change")
                                }
                            }
                            TriggerConfig(t, locations) { updated ->
                                draft.trigger = updated
                                vm.updateDraft(draft)
                            }
                            if (t.type == TriggerType.LOCATION_ENTER ||
                                t.type == TriggerType.LOCATION_EXIT ||
                                t.type == TriggerType.LOCATION_DWELL ||
                                t.type == TriggerType.CELL_TOWER_ENTER ||
                                t.type == TriggerType.CELL_TOWER_EXIT ||
                                t.type == TriggerType.CELL_TOWER_DWELL
                            ) {
                                SwitchRow(
                                    text = "Use GPS for this automation",
                                    subtitle = "On = GPS geofence even when low-power mode is enabled. Off = follow the global low-power setting.",
                                    checked = draft.useGpsForLocation,
                                    onCheckedChange = { draft.useGpsForLocation = it; vm.updateDraft(draft) },
                                )
                            }
                        }
                    }
                }
            }

            // ---- IF -----------------------------------------------------
            SectionTitle("If")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Run only when these match", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("count: ${draft.conditions.size}", style = MaterialTheme.typography.bodySmall)
            }
            if (draft.conditions.isNotEmpty()) {
                SwitchRow(
                    text = "Match any condition",
                    subtitle = "Off = all conditions (AND), On = any condition (OR)",
                    checked = draft.matchAny,
                    onCheckedChange = { draft.matchAny = it; vm.updateDraft(draft) },
                )
            }
            draft.conditions.forEachIndexed { i, condition ->
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(condition.describe(), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { dialog = EditorDialog.ConditionTypeIn(condition, i) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.padding(end = 4.dp))
                            Text("Edit")
                        }
                        IconButton(onClick = {
                            draft.conditions = draft.conditions.filterIndexed { j, _ -> j != i }
                            vm.updateDraft(draft)
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
            Button(onClick = { dialog = EditorDialog.ConditionTypeIn(null, -1) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Add condition")
            }

            // ---- THEN ---------------------------------------------------
            SectionTitle("Then")
            draft.actions.forEachIndexed { i, action ->
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(action.describe(), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { dialog = EditorDialog.ActionTypeIn(action, i) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.padding(end = 4.dp))
                            Text("Edit")
                        }
                        IconButton(onClick = {
                            draft.actions = draft.actions.filterIndexed { j, _ -> j != i }
                            vm.updateDraft(draft)
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
            Button(onClick = { dialog = EditorDialog.ActionTypeIn(null, -1) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Add action")
            }

            // ---- Settings ------------------------------------------------
            SectionTitle("Automation settings")
            LabeledField(label = "Name", value = draft.name, onChange = {
                draft.name = it
                vm.updateDraft(draft)
            }, placeholder = "e.g. Silent when I arrive at work")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Run mode:", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            dev.context.app.model.ExecutionMode.entries.forEach { mode ->
                SwitchRow(
                    text = mode.label(),
                    subtitle = when (mode) {
                        dev.context.app.model.ExecutionMode.ONCE_PER_TRANSITION -> "Fire when the situation changes, then wait for the next change."
                        dev.context.app.model.ExecutionMode.EVERY_MATCH -> "Fire on every matching event. Transitional triggers (enter/leave, on/off) still fire once per change."
                        dev.context.app.model.ExecutionMode.COOLDOWN -> "Fire, then ignore repeats for the cooldown period."
                    },
                    checked = draft.mode == mode,
                    onCheckedChange = { if (it) { draft.mode = mode; vm.updateDraft(draft) } },
                )
            }
            if (draft.mode == dev.context.app.model.ExecutionMode.COOLDOWN) {
                MinutesStepper(
                    label = "Cooldown",
                    value = draft.cooldownMinutes,
                    onChange = { draft.cooldownMinutes = it; vm.updateDraft(draft) },
                    min = 1,
                    max = 1440,
                )
            }

            Button(
                onClick = { dialog = null; vm.saveDraft() },
                enabled = draft.name.isNotBlank() && draft.trigger != null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                Text("  Save automation")
            }
        }
    }

    when (val d = dialog) {
        is EditorDialog.TriggerTypeIn -> {
            val types = TriggerType.entries.filter { it.category() == d.category }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(d.category.label) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        types.forEach { type ->
                            TextButton(onClick = {
                                dialog = null
                                draft.trigger = TriggerSpec(type = type)
                                vm.updateDraft(draft)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(type.shortLabel(), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        is EditorDialog.ConditionTypeIn -> {
            if (d.editing == null) {
                ConditionTypePicker(onPick = { type ->
                    dialog = EditorDialog.ConditionTypeIn(ConditionSpec(type = type), -1)
                }, onDismiss = { dialog = null })
            } else {
                ConditionEditDialog(
                    initial = d.editing,
                    locations = locations,
                    onConfirm = { spec ->
                        if (d.index >= 0) {
                            draft.conditions = draft.conditions.toMutableList().also {
                                it[d.index] = spec
                            }
                        } else {
                            draft.conditions = draft.conditions + spec
                        }
                        vm.updateDraft(draft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
        is EditorDialog.ActionTypeIn -> {
            if (d.editing == null) {
                ActionTypePicker(onPick = { type ->
                    dialog = EditorDialog.ActionTypeIn(ActionSpec(type = type), -1)
                }, onDismiss = { dialog = null })
            } else {
                ActionEditDialog(
                    initial = d.editing,
                    onConfirm = { spec ->
                        if (d.index >= 0) {
                            draft.actions = draft.actions.toMutableList().also { it[d.index] = spec }
                        } else {
                            draft.actions = draft.actions + spec
                        }
                        vm.updateDraft(draft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
        null -> {}
    }
}

fun TriggerType.category(): TriggerCategory = when (this) {
    TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT, TriggerType.LOCATION_DWELL,
    TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT, TriggerType.CELL_TOWER_DWELL -> TriggerCategory.LOCATION
    TriggerType.TIME_AT, TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END -> TriggerCategory.TIME
    TriggerType.BATTERY_CHANGED, TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE,
    TriggerType.CHARGING_START, TriggerType.CHARGING_STOP, TriggerType.BATTERY_FULL -> TriggerCategory.BATTERY
    TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED,
    TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> TriggerCategory.CONNECTIVITY
    TriggerType.SCREEN_ON, TriggerType.SCREEN_OFF, TriggerType.SCREEN_UNLOCKED,
    TriggerType.DND_ENABLED, TriggerType.DND_DISABLED, TriggerType.BOOT_COMPLETED,
    TriggerType.FOREGROUND_APP_CHANGED, TriggerType.VOLUME_KEY_PRESSED,
    TriggerType.USB_CONNECTED, TriggerType.USB_DISCONNECTED, TriggerType.LOGCAT_MATCH -> TriggerCategory.DEVICE
    TriggerType.HEADSET_PLUGGED, TriggerType.HEADSET_UNPLUGGED -> TriggerCategory.AUDIO
    TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END,
    TriggerType.SIGNIFICANT_MOTION, TriggerType.DEVICE_PICKUP -> TriggerCategory.ACTIVITY
    TriggerType.NOTIFICATION_RECEIVED, TriggerType.SMS_RECEIVED, TriggerType.CALL_STATE_CHANGED ->
        TriggerCategory.COMMUNICATION
}

@Composable
fun TriggerCategoryButtons(onPick: (TriggerCategory) -> Unit) {
    Column {
        TriggerCategory.entries.forEach { cat ->
            Button(onClick = { onPick(cat) }, modifier = Modifier.fillMaxWidth()) {
                Text(cat.label, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ConditionTypePicker(onPick: (ConditionType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a condition") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ConditionType.entries.forEach { type ->
                    Button(onClick = { onPick(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.label(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ActionTypePicker(onPick: (ActionType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an action") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ActionType.entries.forEach { type ->
                    Button(onClick = { onPick(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.label(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun OptionsField(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val index = options.indexOfFirst { it == selected }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.getOrNull(index) ?: "choose…", modifier = Modifier.weight(1f))
        }
        if (expanded) {
            options.forEach { option ->
                TextButton(
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(option, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Inline configuration for the currently chosen trigger. */
@Composable
fun TriggerConfig(trigger: TriggerSpec, locations: List<dev.context.app.model.LocationMarker>, onChange: (TriggerSpec) -> Unit) {
    val p = trigger.params
    when (trigger.type) {
        TriggerType.LOCATION_ENTER, TriggerType.LOCATION_EXIT, TriggerType.LOCATION_DWELL,
        TriggerType.CELL_TOWER_ENTER, TriggerType.CELL_TOWER_EXIT, TriggerType.CELL_TOWER_DWELL -> {
            val current = p[Param.LOCATION_ID]?.toLongOrNull()?.let { id -> locations.find { it.id == id }?.name }
            OptionsField(
                label = "Saved place",
                options = locations.map { it.name },
                selected = current,
                onSelected = { name ->
                    val id = locations.find { it.name == name }?.id
                    val params = if (id != null) mapOf(Param.LOCATION_ID to id.toString()) else emptyMap()
                    if (trigger.type == TriggerType.LOCATION_DWELL || trigger.type == TriggerType.CELL_TOWER_DWELL) {
                        val minutes = p[Param.DWELL_MINUTES]?.toIntOrNull() ?: 10
                        onChange(trigger.copy(params = params + (Param.DWELL_MINUTES to minutes.toString())))
                    } else {
                        onChange(trigger.copy(params = params))
                    }
                },
            )
            Text("Places are managed on the Places tab.", style = MaterialTheme.typography.bodySmall)
            if (trigger.type == TriggerType.LOCATION_DWELL || trigger.type == TriggerType.CELL_TOWER_DWELL) {
                MinutesStepper(
                    label = "Stay for at least",
                    value = p[Param.DWELL_MINUTES]?.toIntOrNull() ?: 10,
                    onChange = { onChange(trigger.copy(params = p + (Param.DWELL_MINUTES to it.toString()))) },
                    min = 1,
                    max = 240,
                )
            }
        }
        TriggerType.TIME_AT -> {
            val hour = p[Param.HOUR]?.toIntOrNull() ?: 9
            val minute = p[Param.MINUTE]?.toIntOrNull() ?: 0
            val days = p[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS
            TimeField(label = "Time", minuteOfDay = hour * 60 + minute) { m ->
                onChange(trigger.copy(params = p + mapOf(Param.HOUR to (m / 60).toString(), Param.MINUTE to (m % 60).toString())))
            }
            Text("Repeat on", style = MaterialTheme.typography.bodyLarge)
            DayMaskRow(mask = days) { d ->
                onChange(trigger.copy(params = p + (Param.DAYS to d.toString())))
            }
        }
        TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE -> {
            val threshold = p[Param.THRESHOLD]?.toIntOrNull() ?: 20
            PercentSlider(label = if (trigger.type == TriggerType.BATTERY_BELOW) "Drop below" else "Rise above", percent = threshold) { tl ->
                onChange(trigger.copy(params = mapOf(Param.THRESHOLD to tl.toString())))
            }
        }
        TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED -> {
            var name by remember { mutableStateOf(p[Param.DEVICE_NAME] ?: "") }
            LabeledField(
                label = "Device name (optional)",
                value = name,
                onChange = {
                    name = it
                    val params = if (it.isBlank()) emptyMap() else mapOf(Param.DEVICE_NAME to it.trim())
                    onChange(trigger.copy(params = params))
                },
                placeholder = "empty = any device",
            )
        }
        TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED -> {
            var ssid by remember { mutableStateOf(p[Param.SSID] ?: "") }
            LabeledField(
                label = "Network name (optional)",
                value = ssid,
                onChange = {
                    ssid = it
                    val params = if (it.isBlank()) emptyMap() else mapOf(Param.SSID to it.trim())
                    onChange(trigger.copy(params = params))
                },
                placeholder = "empty = any network",
            )
        }
        TriggerType.ACTIVITY_START, TriggerType.ACTIVITY_END -> {
            val current = p[Param.ACTIVITY]
            OptionsField(
                label = "Activity",
                options = listOf("stationary", "walking", "running", "cycling", "driving"),
                selected = current,
                onSelected = { onChange(trigger.copy(params = mapOf(Param.ACTIVITY to it))) },
            )
        }
        TriggerType.SIGNIFICANT_MOTION, TriggerType.DEVICE_PICKUP -> {
            Text(
                "Fires on hardware motion sensors. No settings needed; keep the automation enabled and move the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TriggerType.FOREGROUND_APP_CHANGED -> {
            var pkg by remember { mutableStateOf(p[Param.PACKAGE] ?: "") }
            LabeledField(
                label = "App package (optional)",
                value = pkg,
                onChange = {
                    pkg = it
                    val params = if (it.isBlank()) emptyMap() else mapOf(Param.PACKAGE to it.trim())
                    onChange(trigger.copy(params = params))
                },
                placeholder = "empty = any app, e.g. com.android.chrome",
            )
            Text(
                "Needs the Cue accessibility service enabled (Access tab).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TriggerType.VOLUME_KEY_PRESSED -> {
            val current = p[Param.KEY]
            OptionsField(
                label = "Key",
                options = listOf("volume_up", "volume_down"),
                selected = current,
                onSelected = { onChange(trigger.copy(params = mapOf(Param.KEY to it))) },
            )
            Text(
                "Needs the Cue accessibility service enabled (Access tab).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TriggerType.NOTIFICATION_RECEIVED -> {
            var pkg by remember { mutableStateOf(p[Param.PACKAGE] ?: "") }
            var keyword by remember { mutableStateOf(p[Param.TITLE_CONTAINS] ?: "") }
            var category by remember { mutableStateOf(p[Param.CATEGORY] ?: "") }
            LabeledField("App package (optional)", pkg, {
                pkg = it
                onChange(trigger.copy(params = notifTriggerParams(pkg, keyword, category)))
            }, placeholder = "empty = any app")
            LabeledField("Word in title/text (optional)", keyword, {
                keyword = it
                onChange(trigger.copy(params = notifTriggerParams(pkg, keyword, category)))
            })
            LabeledField("Category (optional)", category, {
                category = it
                onChange(trigger.copy(params = notifTriggerParams(pkg, keyword, category)))
            }, placeholder = "e.g. msg, alarm, call")
            Text(
                "Needs notification access (Access tab).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TriggerType.SMS_RECEIVED -> {
            var sender by remember { mutableStateOf(p[Param.SENDER] ?: "") }
            var body by remember { mutableStateOf(p[Param.BODY] ?: "") }
            LabeledField("Sender contains (optional)", sender, {
                sender = it
                onChange(trigger.copy(params = smsTriggerParams(sender, body)))
            })
            LabeledField("Body contains (optional)", body, {
                body = it
                onChange(trigger.copy(params = smsTriggerParams(sender, body)))
            })
        }
        TriggerType.CALL_STATE_CHANGED -> {
            val current = p[Param.STATE]
            var number by remember { mutableStateOf(p[Param.NUMBER] ?: "") }
            OptionsField(
                label = "State",
                options = listOf("ringing", "active", "idle"),
                selected = current,
                onSelected = {
                    onChange(trigger.copy(params = callTriggerParams(it, number)))
                },
            )
            LabeledField("Number contains (optional)", number, {
                number = it
                onChange(trigger.copy(params = callTriggerParams(p[Param.STATE] ?: "ringing", number)))
            }, placeholder = "often hidden by the system")
        }
        TriggerType.CALENDAR_EVENT_START, TriggerType.CALENDAR_EVENT_END -> {
            var title by remember { mutableStateOf(p[Param.TITLE_CONTAINS] ?: "") }
            LabeledField("Event title contains (optional)", title, {
                title = it
                val params = if (it.isBlank()) emptyMap() else mapOf(Param.TITLE_CONTAINS to it.trim())
                onChange(trigger.copy(params = params))
            }, placeholder = "empty = any event")
        }
        TriggerType.USB_CONNECTED, TriggerType.USB_DISCONNECTED -> {
            Text(
                "Fires when a USB device is attached or detached. No settings needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TriggerType.LOGCAT_MATCH -> {
            var pattern by remember { mutableStateOf(p[Param.PATTERN] ?: "") }
            LabeledField("Regex pattern", pattern, {
                pattern = it
                onChange(trigger.copy(params = mapOf(Param.PATTERN to it)))
            }, placeholder = "e.g. NFC.*discovered")
            Text(
                "Reads the system log; needs the ADB READ_LOGS grant (Advanced setup).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text("No extra settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun notifTriggerParams(pkg: String, keyword: String, category: String): Map<String, String> =
    buildMap {
        if (pkg.isNotBlank()) put(Param.PACKAGE, pkg.trim())
        if (keyword.isNotBlank()) put(Param.TITLE_CONTAINS, keyword.trim())
        if (category.isNotBlank()) put(Param.CATEGORY, category.trim())
    }

private fun smsTriggerParams(sender: String, body: String): Map<String, String> =
    buildMap {
        if (sender.isNotBlank()) put(Param.SENDER, sender.trim())
        if (body.isNotBlank()) put(Param.BODY, body.trim())
    }

private fun callTriggerParams(state: String, number: String): Map<String, String> =
    buildMap {
        put(Param.STATE, state)
        if (number.isNotBlank()) put(Param.NUMBER, number.trim())
    }

@Composable
fun ConditionEditDialog(
    initial: ConditionSpec,
    locations: List<dev.context.app.model.LocationMarker>,
    onConfirm: (ConditionSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var negate by remember { mutableStateOf(initial.negate) }
    var startMin by remember { mutableStateOf(initial.params[Param.START_MINUTE]?.toIntOrNull() ?: 7 * 60) }
    var endMin by remember { mutableStateOf(initial.params[Param.END_MINUTE]?.toIntOrNull() ?: 19 * 60) }
    var days by remember { mutableStateOf(initial.params[Param.DAYS]?.toIntOrNull() ?: Shared.ALL_DAYS) }
    var below by remember { mutableStateOf(initial.params[Param.BELOW_PERCENT] ?: "20") }
    var above by remember { mutableStateOf(initial.params[Param.ABOVE_PERCENT] ?: "") }
    var charging by remember { mutableStateOf(initial.params[Param.CHARGING] != "false") }
    var boolOn by remember { mutableStateOf(initial.params[Param.BOOL_VALUE] != "false") }
    var ssid by remember { mutableStateOf(initial.params[Param.SSID] ?: "") }
    var device by remember { mutableStateOf(initial.params[Param.DEVICE_NAME] ?: "") }
    var screenState by remember { mutableStateOf(initial.params[Param.SCREEN_STATE] ?: "on") }
    var activity by remember { mutableStateOf(initial.params[Param.ACTIVITY] ?: "stationary") }
    var locId by remember { mutableStateOf(initial.params[Param.LOCATION_ID]) }
    var inside by remember { mutableStateOf(initial.params[Param.BOOL_VALUE] != "false") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.type.label()) },
        text = {
            Column {
                when (initial.type) {
                    ConditionType.TIME_RANGE -> {
                        TimeField("Between", startMin) { startMin = it }
                        TimeField("and", endMin) { endMin = it }
                    }
                    ConditionType.DAY_OF_WEEK -> DayMaskRow(days) { days = it }
                    ConditionType.BATTERY -> {
                        LabeledField("Below % (optional)", below, { below = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        LabeledField("Above % (optional)", above, { above = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    }
                    ConditionType.CHARGING -> SwitchRow("Charging is active", checked = charging) { charging = it }
                    ConditionType.WIFI_STATE -> SwitchRow("Connected to Wi-Fi", checked = boolOn) { boolOn = it }
                    ConditionType.WIFI_SSID -> LabeledField("Network name", ssid, { ssid = it }, placeholder = "exact SSID")
                    ConditionType.BLUETOOTH_STATE -> SwitchRow("Bluetooth is on", checked = boolOn) { boolOn = it }
                    ConditionType.BLUETOOTH_DEVICE -> LabeledField("Device name", device, { device = it })
                    ConditionType.DND_STATE -> SwitchRow("Do Not Disturb active", checked = boolOn) { boolOn = it }
                    ConditionType.SCREEN_STATE -> OptionsField(
                        "Screen state",
                        listOf("on", "off", "unlocked"), screenState, { screenState = it },
                    )
                    ConditionType.ACTIVITY_STATE -> OptionsField(
                        "Activity",
                        listOf("stationary", "walking", "running", "cycling", "driving"), activity, { activity = it },
                    )
                    ConditionType.LOCATION_STATE -> {
                        OptionsField(
                            "Saved place",
                            locations.map { it.name },
                            locId?.toLongOrNull()?.let { id -> locations.find { l -> l.id == id }?.name },
                            { name -> locId = locations.find { it.name == name }?.id?.toString() },
                        )
                        SwitchRow("Inside the place", checked = inside) { inside = it }
                    }
                }
                SwitchRow("Negate (fire when this is NOT the case)", checked = negate) { negate = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                val params = buildMap<String, String> {
                    when (initial.type) {
                        ConditionType.TIME_RANGE -> {
                            put(Param.START_MINUTE, startMin.toString())
                            put(Param.END_MINUTE, endMin.toString())
                        }
                        ConditionType.DAY_OF_WEEK -> put(Param.DAYS, days.toString())
                        ConditionType.BATTERY -> {
                            if (below.isNotBlank()) put(Param.BELOW_PERCENT, below)
                            if (above.isNotBlank()) put(Param.ABOVE_PERCENT, above)
                        }
                        ConditionType.CHARGING -> put(Param.CHARGING, charging.toString())
                        ConditionType.WIFI_STATE, ConditionType.BLUETOOTH_STATE, ConditionType.DND_STATE ->
                            put(Param.BOOL_VALUE, boolOn.toString())
                        ConditionType.WIFI_SSID -> if (ssid.isNotBlank()) put(Param.SSID, ssid.trim())
                        ConditionType.BLUETOOTH_DEVICE -> if (device.isNotBlank()) put(Param.DEVICE_NAME, device.trim())
                        ConditionType.SCREEN_STATE -> put(Param.SCREEN_STATE, screenState)
                        ConditionType.ACTIVITY_STATE -> put(Param.ACTIVITY, activity)
                        ConditionType.LOCATION_STATE -> {
                            locId?.let { put(Param.LOCATION_ID, it) }
                            put(Param.BOOL_VALUE, inside.toString())
                        }
                    }
                }
                onConfirm(ConditionSpec(type = initial.type, negate = negate, params = params))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SecureActionNote() {
    Text(
        "Needs the one-time ADB grant WRITE_SECURE_SETTINGS (Advanced setup).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun A11yActionNote() {
    Text(
        "Needs the Cue accessibility service enabled (Access tab).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ActionEditDialog(
    initial: ActionSpec,
    onConfirm: (ActionSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.params[Param.TITLE] ?: "") }
    var text by remember { mutableStateOf(initial.params[Param.TEXT] ?: "") }
    var persistent by remember { mutableStateOf(initial.params[Param.PERSISTENT] == "true") }
    var dndOn by remember { mutableStateOf(initial.params[Param.BOOL_VALUE] != "false") }
    var toggleOn by remember { mutableStateOf(initial.params[Param.BOOL_VALUE] != "false") }
    var stream by remember { mutableStateOf(initial.params[Param.STREAM] ?: "media") }
    var volume by remember { mutableStateOf(initial.params[Param.VOLUME_PERCENT]?.toIntOrNull() ?: 50) }
    var ringerMode by remember { mutableStateOf(initial.params[Param.RINGER_MODE] ?: "normal") }
    var vibrateMs by remember { mutableStateOf(initial.params[Param.DURATION_MS] ?: "500") }
    var brightness by remember { mutableStateOf(initial.params[Param.BRIGHTNESS_PERCENT]?.toIntOrNull() ?: 20) }
    var pkg by remember { mutableStateOf(initial.params[Param.PACKAGE] ?: "") }
    var url by remember { mutableStateOf(initial.params[Param.URL] ?: "") }
    var target by remember { mutableStateOf(initial.params[Param.SETTINGS_TARGET] ?: "wifi") }
    var variable by remember { mutableStateOf(initial.params[Param.VARIABLE] ?: "") }
    var value by remember { mutableStateOf(initial.params[Param.VALUE] ?: "") }
    var message by remember { mutableStateOf(initial.params[Param.MESSAGE] ?: "") }
    var secureOn by remember { mutableStateOf(initial.params[Param.BOOL_VALUE] != "false") }
    var locationMode by remember { mutableStateOf(initial.params[Param.STATE] ?: "high_accuracy") }
    var dnsMode by remember { mutableStateOf(initial.params[Param.DNS_MODE] ?: "auto") }
    var dnsHost by remember { mutableStateOf(initial.params[Param.DNS_HOST] ?: "") }
    var animScale by remember { mutableStateOf(initial.params[Param.VALUE] ?: "0") }
    var timeoutMs by remember { mutableStateOf(initial.params[Param.TIMEOUT_MS] ?: "60000") }
    var tapX by remember { mutableStateOf(initial.params[Param.X] ?: "") }
    var tapY by remember { mutableStateOf(initial.params[Param.Y] ?: "") }
    var tapText by remember { mutableStateOf(initial.params[Param.TEXT] ?: "") }
    var direction by remember { mutableStateOf(initial.params[Param.DIRECTION] ?: "right") }
    var dismissPkg by remember { mutableStateOf(initial.params[Param.PACKAGE] ?: "") }
    var dismissKey by remember { mutableStateOf(initial.params[Param.KEY] ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.type.label()) },
        text = {
            Column {
                when (initial.type) {
                    ActionType.SHOW_NOTIFICATION -> {
                        LabeledField("Title", title, { title = it })
                        LabeledField("Text", text, { text = it })
                        SwitchRow("Persistent", checked = persistent) { persistent = it }
                    }
                    ActionType.SET_DND -> SwitchRow("Enable Do Not Disturb", checked = dndOn) { dndOn = it }
                    ActionType.SET_RINGER_MODE -> OptionsField(
                        "Mode",
                        listOf("normal", "vibrate", "silent"), ringerMode, { ringerMode = it },
                    )
                    ActionType.VIBRATE -> LabeledField("Duration (ms)", vibrateMs, { vibrateMs = it }, placeholder = "500")
                    ActionType.SET_BRIGHTNESS -> PercentSlider("Screen brightness", brightness) { brightness = it }
                    ActionType.SET_NIGHT_LIGHT -> Text(
                        "Android has no direct night-light API; this opens the Night light settings page.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ActionType.SET_WIFI -> SwitchRow("Enable Wi-Fi", checked = toggleOn) { toggleOn = it }
                    ActionType.SET_MOBILE_DATA -> SwitchRow("Enable mobile data", checked = toggleOn) { toggleOn = it }
                    ActionType.SET_BLUETOOTH -> SwitchRow("Enable Bluetooth", checked = toggleOn) { toggleOn = it }
                    ActionType.SET_VOLUME -> {
                        OptionsField("Stream", listOf("media", "ring", "notification", "alarm"), stream, { stream = it })
                        PercentSlider("Volume", volume) { volume = it }
                    }
                    ActionType.OPEN_APP -> LabeledField("Package name", pkg, { pkg = it }, placeholder = "e.g. com.android.chrome")
                    ActionType.OPEN_URL -> LabeledField("Link", url, { url = it }, placeholder = "https://…")
                    ActionType.OPEN_SETTINGS -> OptionsField(
                        "Open",
                        listOf("wifi", "bluetooth", "location", "dnd", "power_save", "nfc", "data_usage", "airplane", "volume", "battery_optimization", "app_details"),
                        target, { target = it },
                    )
                    ActionType.SET_VARIABLE -> {
                        LabeledField("Variable name", variable, { variable = it }, placeholder = "{key} used in messages")
                        LabeledField("Value", value, { value = it }, placeholder = "text, a number, or \"now\"")
                    }
                    ActionType.INCREMENT_VARIABLE -> {
                        LabeledField("Variable name", variable, { variable = it })
                        LabeledField("Increment by", value, { value = it }, placeholder = "1")
                    }
                    ActionType.CLEAR_VARIABLE -> LabeledField("Variable name", variable, { variable = it })
                    ActionType.LOG_MESSAGE -> LabeledField("Message", message, { message = it })
                    ActionType.SET_AIRPLANE_MODE -> {
                        SwitchRow("Enable airplane mode", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_LOCATION_MODE -> {
                        OptionsField(
                            "Mode",
                            listOf("high_accuracy", "off"), locationMode, { locationMode = it },
                        )
                        SecureActionNote()
                    }
                    ActionType.SET_BATTERY_SAVER -> {
                        SwitchRow("Enable battery saver", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_ALWAYS_ON_DISPLAY -> {
                        SwitchRow("Enable always-on display", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_NIGHT_LIGHT_SECURE -> {
                        SwitchRow("Enable night light", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_PRIVATE_DNS -> {
                        OptionsField("Mode", listOf("auto", "off", "hostname"), dnsMode, { dnsMode = it })
                        if (dnsMode == "hostname") {
                            LabeledField("Hostname", dnsHost, { dnsHost = it }, placeholder = "e.g. dns.adguard.com")
                        }
                        SecureActionNote()
                    }
                    ActionType.SET_IMMERSIVE_MODE -> {
                        SwitchRow("Hide status + navigation bars", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_ANIMATION_SCALES -> {
                        OptionsField(
                            "Speed",
                            listOf("0", "0.5", "1", "2", "5"), animScale, { animScale = it },
                        )
                        Text(
                            "0 disables animations; 1 is normal speed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SecureActionNote()
                    }
                    ActionType.SET_STAY_ON_WHILE_CHARGING -> {
                        SwitchRow("Keep screen on while charging", checked = secureOn) { secureOn = it }
                        SecureActionNote()
                    }
                    ActionType.SET_SCREEN_TIMEOUT -> {
                        OptionsField(
                            "Timeout",
                            listOf("15000", "30000", "60000", "120000", "600000"),
                            timeoutMs, { timeoutMs = it },
                        )
                        Text(
                            "Needs 'Modify system settings' (Access tab).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionType.SET_AUTO_ROTATE -> {
                        SwitchRow("Enable auto-rotate", checked = secureOn) { secureOn = it }
                        Text(
                            "Needs 'Modify system settings' (Access tab).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionType.PLAY_TTS -> LabeledField("Text to speak", message, { message = it }, placeholder = "supports {variables}")
                    ActionType.FLASHLIGHT -> SwitchRow("Turn flashlight on", checked = secureOn) { secureOn = it }
                    ActionType.LOCK_SCREEN, ActionType.OPEN_NOTIFICATIONS,
                    ActionType.OPEN_QUICK_SETTINGS, ActionType.TAKE_SCREENSHOT -> {
                        Text(
                            "Needs the Cue accessibility service enabled (Access tab).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionType.CLICK_AT_COORDS -> {
                        LabeledField("X", tapX, { tapX = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        LabeledField("Y", tapY, { tapY = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        A11yActionNote()
                    }
                    ActionType.CLICK_BY_TEXT -> {
                        LabeledField("Visible text", tapText, { tapText = it }, placeholder = "exact text on screen")
                        A11yActionNote()
                    }
                    ActionType.SWIPE_GESTURE -> {
                        OptionsField(
                            "Direction",
                            listOf("up", "down", "left", "right"), direction, { direction = it },
                        )
                        A11yActionNote()
                    }
                    ActionType.DISMISS_NOTIFICATION -> {
                        LabeledField("App package (optional)", dismissPkg, { dismissPkg = it }, placeholder = "empty = the notification that fired this")
                        LabeledField("Notification key (optional)", dismissKey, { dismissKey = it }, placeholder = "advanced: exact key")
                        Text(
                            "Empty fields dismiss the notification that triggered this automation. Needs notification access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val params = buildMap<String, String> {
                    when (initial.type) {
                        ActionType.SHOW_NOTIFICATION -> {
                            if (title.isNotBlank()) put(Param.TITLE, title.trim())
                            if (text.isNotBlank()) put(Param.TEXT, text.trim())
                            put(Param.PERSISTENT, persistent.toString())
                        }
                        ActionType.SET_DND -> put(Param.BOOL_VALUE, dndOn.toString())
                        ActionType.SET_RINGER_MODE -> put(Param.RINGER_MODE, ringerMode)
                        ActionType.VIBRATE -> if (vibrateMs.isNotBlank()) put(Param.DURATION_MS, vibrateMs.trim())
                        ActionType.SET_BRIGHTNESS -> put(Param.BRIGHTNESS_PERCENT, brightness.toString())
                        ActionType.SET_NIGHT_LIGHT -> {}
                        ActionType.SET_WIFI, ActionType.SET_MOBILE_DATA, ActionType.SET_BLUETOOTH ->
                            put(Param.BOOL_VALUE, toggleOn.toString())
                        ActionType.SET_VOLUME -> {
                            put(Param.STREAM, stream)
                            put(Param.VOLUME_PERCENT, volume.toString())
                        }
                        ActionType.OPEN_APP -> put(Param.PACKAGE, pkg.trim())
                        ActionType.OPEN_URL -> put(Param.URL, url.trim())
                        ActionType.OPEN_SETTINGS -> put(Param.SETTINGS_TARGET, target)
                        ActionType.SET_VARIABLE, ActionType.INCREMENT_VARIABLE, ActionType.CLEAR_VARIABLE -> {
                            if (variable.isNotBlank()) put(Param.VARIABLE, variable.trim())
                            if ((initial.type == ActionType.SET_VARIABLE || initial.type == ActionType.INCREMENT_VARIABLE) && value.isNotBlank()) {
                                put(Param.VALUE, value.trim())
                            }
                        }
                        ActionType.LOG_MESSAGE -> put(Param.MESSAGE, message)
                        ActionType.SET_AIRPLANE_MODE, ActionType.SET_BATTERY_SAVER,
                        ActionType.SET_ALWAYS_ON_DISPLAY, ActionType.SET_NIGHT_LIGHT_SECURE,
                        ActionType.SET_IMMERSIVE_MODE, ActionType.SET_STAY_ON_WHILE_CHARGING,
                        ActionType.SET_AUTO_ROTATE, ActionType.FLASHLIGHT ->
                            put(Param.BOOL_VALUE, secureOn.toString())
                        ActionType.SET_LOCATION_MODE -> put(Param.STATE, locationMode)
                        ActionType.SET_PRIVATE_DNS -> {
                            put(Param.DNS_MODE, dnsMode)
                            if (dnsHost.isNotBlank()) put(Param.DNS_HOST, dnsHost.trim())
                        }
                        ActionType.SET_ANIMATION_SCALES -> put(Param.VALUE, animScale)
                        ActionType.SET_SCREEN_TIMEOUT -> put(Param.TIMEOUT_MS, timeoutMs)
                        ActionType.PLAY_TTS -> put(Param.MESSAGE, message)
                        ActionType.LOCK_SCREEN, ActionType.OPEN_NOTIFICATIONS,
                        ActionType.OPEN_QUICK_SETTINGS, ActionType.TAKE_SCREENSHOT -> {}
                        ActionType.CLICK_AT_COORDS -> {
                            if (tapX.isNotBlank()) put(Param.X, tapX.trim())
                            if (tapY.isNotBlank()) put(Param.Y, tapY.trim())
                        }
                        ActionType.CLICK_BY_TEXT -> put(Param.TEXT, tapText.trim())
                        ActionType.SWIPE_GESTURE -> put(Param.DIRECTION, direction)
                        ActionType.DISMISS_NOTIFICATION -> {
                            if (dismissPkg.isNotBlank()) put(Param.PACKAGE, dismissPkg.trim())
                            if (dismissKey.isNotBlank()) put(Param.KEY, dismissKey.trim())
                        }
                    }
                }
                onConfirm(ActionSpec(type = initial.type, params = params))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}