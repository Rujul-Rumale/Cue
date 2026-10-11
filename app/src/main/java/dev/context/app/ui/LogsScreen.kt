package dev.context.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.context.app.core.formatTimestamp
import dev.context.app.model.ExecutionLog
import dev.context.app.model.LogResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(vm: MainViewModel) {
    val logs by vm.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Execution log") },
                actions = {
                    Button(onClick = { vm.clearLogs() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Text("  Clear")
                    }
                },
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing has run yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(logs, key = { it.id }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
fun LogRow(log: ExecutionLog) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(log.automationName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    log.result.name.lowercase().replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (log.result) {
                        LogResult.SUCCESS -> MaterialTheme.colorScheme.primary
                        LogResult.PARTIAL -> MaterialTheme.colorScheme.tertiary
                        LogResult.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                log.triggerSummary.ifEmpty { log.result.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatTimestamp(log.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            log.evaluations.forEach { ev ->
                Text(
                    "${if (ev.condition.negate) "not " else ""}${ev.condition.type.label()} ${ev.detail} → ${if (ev.effectivePassed) "pass" else "fail"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ev.effectivePassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            log.executions.forEach { ex ->
                Text(
                    "${ex.action.type.label()}: ${if (ex.ok) "ok" else "failed"} ${ex.message.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ex.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}