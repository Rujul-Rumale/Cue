package dev.context.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dev.context.app.model.LocationMarker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val locations by vm.locations.collectAsState()
    var editing by remember { mutableStateOf<LocationMarker?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var seedPlace by remember { mutableStateOf<LocationMarker?>(null) }

    fun startNewPlace() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            seedPlace = null
            showNew = true
            return
        }
        runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    seedPlace = loc?.let {
                        LocationMarker(
                            id = 0L,
                            name = "",
                            latitude = it.latitude,
                            longitude = it.longitude,
                            radiusMeters = 500,
                            createdAt = System.currentTimeMillis(),
                        )
                    }
                    showNew = true
                }
                .addOnFailureListener {
                    seedPlace = null
                    showNew = true
                }
        }.onFailure {
            seedPlace = null
            showNew = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Saved places") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { startNewPlace() }) {
                Icon(Icons.Filled.Add, contentDescription = "New place")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Places power enter / leave / stay automations and \"if I'm in a place\" conditions. New places start at your current GPS position; the map needs internet for tiles unless you imported an offline archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (locations.isEmpty()) {
                item {
                    Text(
                        "No places yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(locations, key = { it.id }) { location ->
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(location.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                String.format("%.6f, %.6f · radius %dm", location.latitude, location.longitude, location.radiusMeters),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { editing = location }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { vm.deleteLocation(location) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        PlaceDialog(
            initial = seedPlace,
            onDismiss = { showNew = false; seedPlace = null },
            onSave = { vm.saveLocation(it); showNew = false; seedPlace = null },
        )
    }
    editing?.let { location ->
        PlaceDialog(
            initial = location,
            onDismiss = { editing = null },
            onSave = { vm.saveLocation(it); editing = null },
        )
    }
}

private val RECORD_DURATIONS = listOf(10, 30, 60, 300)

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> "${seconds / 60}m${seconds % 60}s"
}

@Composable
fun PlaceDialog(initial: LocationMarker?, onDismiss: () -> Unit, onSave: (LocationMarker) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var lat by remember { mutableStateOf((initial?.latitude ?: 37.7749).toString()) }
    var lng by remember { mutableStateOf((initial?.longitude ?: -122.4194).toString()) }
    var radiusMeters by remember { mutableStateOf(initial?.radiusMeters ?: 500) }
    var cellIds by remember { mutableStateOf(initial?.cellIds ?: emptyList()) }
    var cellStatus by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    // Timed fingerprint recording: walk around the area for the chosen duration
    // while the app captures every visible cell tower.
    var recordDuration by remember { mutableStateOf(30) }
    var recording by remember { mutableStateOf(false) }
    var recordRemaining by remember { mutableStateOf(0) }
    var recordSeen by remember { mutableStateOf(setOf<String>()) }
    var recordJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun finishRecording(cancelled: Boolean) {
        recordJob?.cancel()
        recordJob = null
        recording = false
        cellIds = dev.context.app.triggers.celltower.CellFingerprint.merge(cellIds, recordSeen.toList())
        cellStatus = when {
            recordSeen.isEmpty() -> "No cells captured - check location permission and mobile signal."
            cancelled -> "Saved partial capture. Fingerprint: ${cellIds.size} cell(s)."
            else -> "Recording done. Fingerprint: ${cellIds.size} cell(s)."
        }
        recordSeen = emptySet()
    }

    fun startRecording() {
        if (recording) return
        recording = true
        recordRemaining = recordDuration
        recordSeen = emptySet()
        cellStatus = null
        recordJob = scope.launch {
            val provider = dev.context.app.ContextApp.container.cellTowerProvider
            while (recordRemaining > 0) {
                val cells = provider.readCellsOnce()
                if (cells.isNotEmpty()) recordSeen = recordSeen + cells
                kotlinx.coroutines.delay(1_000)
                recordRemaining--
            }
            finishRecording(cancelled = false)
        }
    }

    if (showMap) {
        Box(Modifier.fillMaxSize()) {
            MapPlacePicker(
                initialLat = lat.toDoubleOrNull() ?: 37.7749,
                initialLng = lng.toDoubleOrNull() ?: -122.4194,
                initialRadiusMeters = radiusMeters,
                onBack = { showMap = false },
                onDone = { newLat, newLng, newRadius ->
                    lat = "%.6f".format(newLat)
                    lng = "%.6f".format(newLng)
                    radiusMeters = newRadius
                    showMap = false
                },
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if ((initial?.id ?: 0L) == 0L) "New place" else "Edit place") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LabeledField("Name", name, { name = it }, placeholder = "e.g. Home, Work, Gym")
                LabeledField("Latitude", lat, { lat = it }, keyboardType = KeyboardType.Decimal)
                LabeledField("Longitude", lng, { lng = it }, keyboardType = KeyboardType.Decimal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Radius", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${radiusMeters}m", color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = radiusMeters.toFloat(),
                    onValueChange = { radiusMeters = it.toInt() },
                    valueRange = 50f..2000f,
                )
                Button(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Text("  Pick on map")
                }
                Text(
                    "Low-power fingerprint: ${cellIds.size} cell(s) learned.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!recording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RECORD_DURATIONS.forEach { seconds ->
                            FilterChip(
                                selected = recordDuration == seconds,
                                onClick = { recordDuration = seconds },
                                label = { Text(formatDuration(seconds)) },
                            )
                        }
                    }
                    Button(onClick = { startRecording() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Record cells (${formatDuration(recordDuration)})")
                    }
                    Text(
                        "Walk around the area while recording so every nearby tower is captured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Recording… ${recordRemaining}s left · ${recordSeen.size} tower(s) seen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = { finishRecording(cancelled = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Stop & save")
                    }
                }
                if (cellStatus != null) {
                    Text(
                        cellStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showPicker = !showPicker }) {
                    Text(if (showPicker) "Hide canvas" else "Fine-tune")
                }
                if (showPicker) {
                    CoordinatePicker(
                        centerLat = lat.toDoubleOrNull() ?: 0.0,
                        centerLng = lng.toDoubleOrNull() ?: 0.0,
                        onPicked = { newLat, newLng ->
                            lat = "%.6f".format(newLat)
                            lng = "%.6f".format(newLng)
                            showPicker = false
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nLat = lat.toDoubleOrNull()
                val nLng = lng.toDoubleOrNull()
                if (name.isNotBlank() && nLat != null && nLng != null && nLat in -90.0..90.0 && nLng in -180.0..180.0) {
                    onSave(
                        LocationMarker(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            latitude = nLat,
                            longitude = nLng,
                            radiusMeters = radiusMeters,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            cellIds = cellIds,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CoordinatePicker(centerLat: Double, centerLng: Double, onPicked: (Double, Double) -> Unit) {
    val span = 0.05
    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val accentColor = MaterialTheme.colorScheme.primary
    val accentTransparent = accentColor.copy(alpha = 0.2f)

    Box {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        dx = (dx + drag.x / size.width).coerceIn(-1f, 1f)
                        dy = (dy + drag.y / size.height).coerceIn(-1f, 1f)
                        change.consume()
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val steps = 10f
            for (i in 0..steps.toInt()) {
                val x = w * i / steps
                val y = h * i / steps
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            drawLine(accentColor, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
            drawLine(accentColor, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)
            val sx = w / 2 + dx * w / 2
            val sy = h / 2 + dy * h / 2
            drawCircle(accentTransparent, radius = 12.dp.toPx(), center = Offset(sx, sy))
            drawCircle(accentColor, radius = 4.dp.toPx(), center = Offset(sx, sy))
        }
        Text(
            String.format(
                "%.5f, %.5f",
                centerLat + dy * span / 2,
                centerLng + dx * span / 2,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
            onPicked(centerLat + dy * span / 2, centerLng + dx * span / 2)
        }, modifier = Modifier.align(Alignment.BottomEnd)) {
            Text("Use this spot")
        }
    }
}