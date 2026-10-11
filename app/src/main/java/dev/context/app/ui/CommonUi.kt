package dev.context.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Small reusable building blocks for screens. */

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ReadOnlyField(label: String, value: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (value.isBlank()) "not set" else value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SwitchRow(text: String, subtitle: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedTrackColor = CueColors.Accent,
                checkedBorderColor = CueColors.Accent,
            ),
        )
    }
}

@Composable
fun PercentSlider(label: String, percent: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$percent%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = CueColors.Accent,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayMaskRow(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        DAY_LABELS.forEachIndexed { i, label ->
            val bit = 1 shl i
            FilterChip(
                selected = mask and bit != 0,
                onClick = { onChange(mask xor bit) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minuteOfDay: Int?, onChange: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { show = true }) {
            Text(
                minuteOfDay?.let { String.format("%02d:%02d", it / 60, it % 60) } ?: "pick time",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (show) {
        TimePickerDialog(
            initialMinutes = minuteOfDay ?: 9 * 60,
            onConfirm = { onChange(it); show = false },
            onDismiss = { show = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun MinutesStepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 300) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Text("$value min", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
fun InfoCard(title: String, body: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable { onClick() } else Modifier.fillMaxWidth()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}