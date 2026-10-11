package dev.context.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CueTheme {
                ContextAppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-sync alarms/geofences every foreground: repairs Play Services drops,
        // revokes/re-grants, and OEM kills without waiting for the next edit.
        runCatching { dev.context.app.ContextApp.container.notifyDataChanged() }
    }
}

@Composable
fun ContextAppRoot(vm: MainViewModel = viewModel()) {
    val screen by vm.screen.collectAsState()
    val bottomVisible = screen is Screen.Home || screen is Screen.Locations || screen is Screen.Logs ||
        screen is Screen.Permissions || screen is Screen.Settings
    Scaffold(
        bottomBar = {
            if (bottomVisible) {
NavigationBar {
                        BottomDestinations.forEach { d ->
                            NavigationBarItem(
                                selected = sameDestination(screen, d.screen),
                                onClick = {
                                    if (!sameDestination(screen, d.screen)) vm.push(d.screen)
                                },
                                icon = { Icon(d.icon, contentDescription = d.label) },
                                label = { Text(d.label) },
                            )
                        }
                    }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                is Screen.Editor -> EditorScreen(vm, s.automationId)
                else -> when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Locations -> LocationsScreen(vm)
                    Screen.Logs -> LogsScreen(vm)
                    Screen.Permissions -> PermissionsScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Debug -> DebugScreen(vm)
                    is Screen.Editor -> {}
                }
            }
        }
    }
}

private data class Dest(val screen: Screen, val label: String, val icon: ImageVector)

private val BottomDestinations: List<Dest> = listOf(
    Dest(Screen.Home, "Automations", Icons.Filled.Home),
    Dest(Screen.Locations, "Places", Icons.Filled.Place),
    Dest(Screen.Logs, "Logs", Icons.AutoMirrored.Filled.List),
    Dest(Screen.Permissions, "Access", Icons.Filled.VerifiedUser),
    Dest(Screen.Settings, "Settings", Icons.Filled.Settings),
)

private fun sameDestination(current: Screen, target: Screen): Boolean =
    current::class == target::class