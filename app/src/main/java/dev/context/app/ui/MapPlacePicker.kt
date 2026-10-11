package dev.context.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.ArrayList

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPlacePicker(
    initialLat: Double,
    initialLng: Double,
    initialRadiusMeters: Int,
    onDone: (Double, Double, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by remember { mutableStateOf(initialLat) }
    var lng by remember { mutableStateOf(initialLng) }
    var radiusMeters by remember { mutableStateOf(initialRadiusMeters) }
    var offlineArchiveName by remember { mutableStateOf<String?>(null) }
    var offlineStatus by remember { mutableStateOf("Online tiles (needs internet)") }

    val mapRef = remember { mutableStateOf<MapView?>(null) }
    val markerRef = remember { mutableStateOf<Marker?>(null) }
    val circleRef = remember { mutableStateOf<Polygon?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapRef.value?.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        offlineArchiveName = findOfflineArchive(context)?.name
        offlineStatus = offlineArchiveName?.let { "Offline archive ready: $it" }
            ?: "Online tiles (needs internet)"
    }

    // Keep the pin + circle glued to the chosen point.
    LaunchedEffect(lat, lng, radiusMeters) {
        markerRef.value?.let { marker ->
            val target = GeoPoint(lat, lng)
            if (marker.position.latitude != lat || marker.position.longitude != lng) {
                marker.position = target
            }
        }
        circleRef.value?.let { circle ->
            circle.points = circlePoints(lat, lng, radiusMeters.toDouble())
            mapRef.value?.invalidate()
        }
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val name = importOfflineArchive(context, uri)
                withContext(Dispatchers.Main) {
                    if (name != null) {
                        offlineArchiveName = name
                        offlineStatus = "Offline archive ready: $name"
                    } else {
                        offlineStatus = "Import failed - not a readable archive"
                    }
                    mapRef.value?.invalidate()
                }
            }
        }
    }

    fun goToMyLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            offlineStatus = "Location permission missing - grant it in Access tab"
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        lat = loc.latitude
                        lng = loc.longitude
                        mapRef.value?.controller?.animateTo(GeoPoint(lat, lng))
                    } else {
                        offlineStatus = "No last location yet - tap the map to place the pin"
                    }
                }
                .addOnFailureListener {
                    offlineStatus = "Could not read location"
                }
        } catch (t: SecurityException) {
            offlineStatus = "Location permission missing - grant it in Access tab"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick a place on the map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "%.6f, %.6f".format(lat, lng),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trigger radius", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$radiusMeters m", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = radiusMeters.toFloat(),
                        onValueChange = { radiusMeters = it.toInt() },
                        valueRange = 50f..2000f,
                    )
                    Text(
                        "Tap the map to place the pin, drag the pin to fine-tune, drag anywhere else to move around.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        offlineStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { archivePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Import offline archive")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val removed = removeOfflineArchive(context)
                                    withContext(Dispatchers.Main) {
                                        offlineArchiveName = null
                                        offlineStatus = if (removed) "Online tiles (needs internet)"
                                        else "No offline archive found"
                                        mapRef.value?.invalidate()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Remove offline")
                        }
                    }
                    Text(
                        "No download button on purpose: OSM tile policy forbids bulk download / offline prefetch. Import an archive you already have rights to use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { onDone(lat, lng, radiusMeters) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use this location")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val config = Configuration.getInstance()
                    val base = File(ctx.getExternalFilesDir(null), "osmdroid").apply { mkdirs() }
                    File(base, "tiles").apply { mkdirs() }
                    config.osmdroidBasePath = base
                    config.osmdroidTileCache = File(base, "tiles")
                    config.userAgentValue = cueUserAgent(ctx)
                    config.load(ctx, android.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
                    val map = MapView(ctx).apply {
                        setMultiTouchControls(true)
                        setTileSource(TileSourceFactory.MAPNIK)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        setTilesScaledToDpi(true)
                    }
                    val circle = Polygon(map).apply {
                        points = circlePoints(lat, lng, radiusMeters.toDouble())
                        outlinePaint.color = Color.rgb(0x17, 0x18, 0x17)
                        outlinePaint.strokeWidth = 4f
                        fillPaint.color = Color.argb(70, 0xA8, 0xE6, 0x6B)
                    }
                    map.overlays.add(circle)
                    circleRef.value = circle
                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lng)
                        isDraggable = true
                        icon = androidx.core.content.ContextCompat.getDrawable(ctx, dev.context.app.R.drawable.ic_map_pin)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDragStart(marker: Marker?) {}
                            override fun onMarkerDrag(marker: Marker?) {
                                marker?.position?.let {
                                    lat = it.latitude
                                    lng = it.longitude
                                }
                            }
                            override fun onMarkerDragEnd(marker: Marker?) {
                                marker?.position?.let {
                                    lat = it.latitude
                                    lng = it.longitude
                                }
                            }
                        })
                    }
                    map.overlays.add(marker)
                    markerRef.value = marker
                    // Tap anywhere on the map to place the pin.
                    map.overlays.add(object : Overlay() {
                        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                            val point = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                            if (point is GeoPoint) {
                                lat = point.latitude
                                lng = point.longitude
                                return true
                            }
                            return false
                        }
                    })
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(GeoPoint(lat, lng))
                    mapRef.value = map
                    map
                },
            )
            // Zoom + locate controls.
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = { mapRef.value?.controller?.zoomIn() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                }
                IconButton(onClick = { mapRef.value?.controller?.zoomOut() }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                }
                IconButton(onClick = { goToMyLocation() }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Go to my location")
                }
            }
            // Required OSM attribution, visible on the map itself.
            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        MaterialTheme.shapes.small,
                    )
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OSM_COPYRIGHT_URL)))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun cueUserAgent(ctx: android.content.Context): String {
    val version = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageInfo(
                ctx.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } ?: "0.1.0"
    } catch (t: Exception) {
        "0.1.0"
    }
    return "Cue/$version (${ctx.packageName})"
}

private fun osmdroidBase(context: android.content.Context): File =
    File(context.getExternalFilesDir(null), "osmdroid").apply { mkdirs() }

private fun findOfflineArchive(context: android.content.Context): File? {
    val base = osmdroidBase(context)
    return base.listFiles()
        ?.firstOrNull { it.isFile && (it.extension.lowercase() in setOf("zip", "sqlite", "mbtiles")) }
}

private fun importOfflineArchive(context: android.content.Context, uri: Uri): String? {
    return try {
        val name = queryDisplayName(context, uri) ?: "offline-map.zip"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeIf { it.isNotBlank() } ?: "offline-map.zip"
        val dest = File(osmdroidBase(context), safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            return null
        }
        dest.name
    } catch (t: Exception) {
        null
    }
}

private fun removeOfflineArchive(context: android.content.Context): Boolean {
    val archive = findOfflineArchive(context) ?: return false
    return try {
        archive.delete()
    } catch (t: Exception) {
        false
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (t: Exception) {
        null
    }
}

private fun circlePoints(lat: Double, lng: Double, radiusMeters: Double): ArrayList<GeoPoint> {
    val points = ArrayList<GeoPoint>()
    val metersPerDegLat = 111_320.0
    val metersPerDegLng = metersPerDegLat * Math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
    for (i in 0 until 72) {
        val angle = Math.toRadians(i * 5.0)
        points.add(
            GeoPoint(
                lat + radiusMeters * Math.cos(angle) / metersPerDegLat,
                lng + radiusMeters * Math.sin(angle) / metersPerDegLng,
            ),
        )
    }
    return points
}
