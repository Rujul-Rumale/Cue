package dev.context.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.context.app.permissions.PermissionStatus
import dev.context.app.permissions.PermissionStatusReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val reader = remember { PermissionStatusReader(context) }
    var statuses by remember { mutableStateOf<List<PermissionStatus>>(reader.all()) }

    // re-read when returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            statuses = reader.all()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        statuses = reader.all()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Access & permissions") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Cue only ever runs on this device. These are the capabilities each feature needs; nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            statuses.forEach { status ->
                PermissionRow(
                    status = status,
                    onGrant = {
                        when (status.id) {
                            "location" -> permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                            "notifications" -> permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                            "activity_recognition" -> permissionLauncher.launch(arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION))
                            "bluetooth" -> permissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
                            "dnd" -> context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            "battery_optimization" -> context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            "exact_alarms" -> context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            "secure_settings" -> context.startActivity(
                                Intent(context, dev.context.app.setup.SetupWizardActivity::class.java),
                            )
                            "notification_listener" -> context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            "accessibility" -> context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            "usage_stats" -> context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        statuses = reader.all()
                    },
                )
            }
            Text(
                "Location note: background location can only be granted after fine location, via the location system settings.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Open app settings")
            }
            Button(onClick = {
                context.startActivity(
                    Intent(
                        context,
                        dev.context.app.setup.SetupWizardActivity::class.java,
                    ),
                )
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                Text("Advanced (ADB) setup")
            }
        }
    }
}

@Composable
fun PermissionRow(status: PermissionStatus, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.granted) Icons.Filled.Check else Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = if (status.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(status.label, style = MaterialTheme.typography.titleSmall)
                if (status.explanation.isNotEmpty()) {
                    Text(status.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!status.granted || status.id == "dnd" || status.id == "battery_optimization") {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                Button(onClick = onGrant, modifier = Modifier.padding(start = 4.dp)) {
                    Text(if (status.granted) "Manage" else "Grant")
                }
            } else {
                Text("OK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}