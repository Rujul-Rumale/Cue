package dev.context.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.context.app.model.Automation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val automations by vm.automations.collectAsState()
    var deleteTarget by remember { mutableStateOf<Automation?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cue") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = "New automation")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (automations.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "No automations yet",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Tap + to create \"when something happens, if conditions match, then do something\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(automations, key = { it.id }) { automation ->
                        AutomationCard(vm, automation, onDelete = { deleteTarget = automation })
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete automation?") },
            text = { Text("\"${target.name}\" will be removed. This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.deleteAutomation(target)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun AutomationCard(vm: MainViewModel, automation: Automation, onDelete: () -> Unit) {
    val locations by vm.locations.collectAsState()
    val locationName = automation.trigger.params[dev.context.app.model.Param.LOCATION_ID]?.toLongOrNull()
        ?.let { id -> locations.find { it.id == id }?.name }
    Card(
        onClick = { vm.openEditor(automation.id) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    automation.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("when ")
                        append(automation.trigger.type.shortLabel())
                        if (locationName != null) append(" · $locationName")
                        if (automation.conditions.isNotEmpty()) {
                            append(" · if ")
                            append(automation.conditions.joinToString(", ") { it.type.label() })
                            if (automation.matchAny) append(" (any)")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
            Switch(checked = automation.enabled, onCheckedChange = { vm.toggleAutomation(automation) })
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}