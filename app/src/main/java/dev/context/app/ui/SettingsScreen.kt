package dev.context.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.context.app.automation.repository.BackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Throws a short toast; used instead of a Snackbar host for simplicity. */
@Composable
fun rememberToast(): (String) -> Unit {
    val context = LocalContext.current
    return { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val devMode by vm.devMode.collectAsState()
    val hyst by vm.hysteresisMinutes.collectAsState()
    val lowPower by vm.lowPowerMode.collectAsState()
    val fgNotif by vm.fgServiceNotification.collectAsState()
    val variables by vm.variables.collectAsState()
    var showDanger by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(BackupCodec.encode(vm.buildBackup()).toByteArray())
                        }
                    }
                }
                toast("Backup exported")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull()
                }
                val backup = raw?.let { BackupCodec.decode(it) }
                if (backup == null) {
                    toast("Import failed: not a valid backup")
                } else {
                    vm.applyBackup(backup, replace = false)
                    toast("Imported ${backup.automations.size} automation(s)")
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Behaviour")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        text = "Developer mode",
                        subtitle = "Reveals the automation simulator.",
                        checked = devMode,
                        onCheckedChange = { vm.setSettings(devMode = it) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Geofence exit delay (min)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("$hyst", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = hyst.toFloat(),
                        onValueChange = { vm.setSettings(hysteresisMinutes = it.toInt()) },
                        valueRange = 0f..60f,
                    )
                    OutlinedButton(
                        onClick = { vm.push(Screen.Debug) },
                        enabled = devMode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null)
                        Text("  Open simulator")
                    }
                }
            }

            SectionTitle("Power & persistence")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        text = "Low-power mode",
                        subtitle = "Use cell-tower fingerprints instead of GPS for places. Per-automation override is in the editor.",
                        checked = lowPower,
                        onCheckedChange = { vm.setSettings(lowPowerMode = it) },
                    )
                    SwitchRow(
                        text = "Show persistent notification",
                        subtitle = "Keeps Android from killing automations. Recommended on.",
                        checked = fgNotif,
                        onCheckedChange = { vm.setSettings(fgServiceNotification = it) },
                    )
                    Text(
                        "Cell-tower fingerprints arrive in the next update; enabling low-power now is recorded and existing GPS places keep working.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    dev.context.app.setup.SetupWizardActivity::class.java,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Advanced (ADB) setup")
                    }
                }
            }

            SectionTitle("Variable store")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    variables.forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(v.value, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.deleteVariable(v.key) }) { Text("delete") }
                        }
                    }
                    if (variables.isEmpty()) {
                        Text(
                            "No variables yet. SET_VARIABLE actions create them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Backup")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Everything lives on this device. You can still export a JSON file to move to a new phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { exportLauncher.launch("context-backup-${System.currentTimeMillis()}.json") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  Export JSON")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  Import JSON")
                    }
                }
            }

            SectionTitle("Danger zone")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Deletes every automation, place and variable permanently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showDanger = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Text("Clear all data")
                    }
                }
            }

            Text(
                "Cue · 0.1.0 · local-first · internet used only for map tiles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (showDanger) {
        AlertDialog(
            onDismissRequest = { showDanger = false },
            title = { Text("Erase everything?") },
            text = { Text("All automations, places and variables will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearAllData()
                        showDanger = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Erase") }
            },
            dismissButton = { TextButton(onClick = { showDanger = false }) { Text("Cancel") } },
        )
    }
}