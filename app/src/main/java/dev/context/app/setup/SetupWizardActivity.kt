package dev.context.app.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.context.app.ui.LabeledField

@OptIn(ExperimentalMaterial3Api::class)
class SetupWizardActivity : ComponentActivity() {

    private val viewModel: SetupWizardViewModel by viewModels {
        SetupWizardViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            dev.context.app.ui.CueTheme {
                SetupWizardScreen(
                    viewModel = viewModel,
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionStates()
    }
}

class SetupWizardViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SetupWizardViewModel(appContext) as T
    }
}

class SetupWizardViewModel(private val context: Context) : ViewModel() {

    private val _permissions = mutableStateOf<List<AdbPermissionState>>(emptyList())
    val permissions: androidx.compose.runtime.State<List<AdbPermissionState>> = _permissions

    private val _allGranted = mutableStateOf(false)
    val allGranted: androidx.compose.runtime.State<Boolean> = _allGranted

    init {
        refreshPermissionStates()
    }

    fun refreshPermissionStates() {
        val adbPerms = AdbPermissionHelper.getAllAdbPermissions(context)
        val states = adbPerms.map { perm ->
            AdbPermissionState(
                name = perm.name,
                description = perm.description,
                adbCommand = perm.adbCommand,
                granted = perm.check(context),
            )
        }
        _permissions.value = states
        _allGranted.value = states.all { it.granted }
    }

    fun copyCommand(command: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ADB Command", command)
        clipboard.setPrimaryClip(clip)
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    data class AdbPermissionState(
        val name: String,
        val description: String,
        val adbCommand: String,
        val granted: Boolean,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: SetupWizardViewModel,
    onFinish: () -> Unit,
) {
    val allGranted by viewModel.allGranted
    val permissions by viewModel.permissions

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Welcome to Cue",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(
                text = "These advanced permissions are granted once from your laptop over ADB.\nConnect the phone, run each command, then return here.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }

        // ADB Permissions List
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            permissions.forEach { perm ->
                PermissionCard(
                    permission = perm,
                    onCopy = { viewModel.copyCommand(perm.adbCommand) },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Prefer no laptop?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Usage access can also be granted on-device:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { viewModel.openUsageAccessSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open usage access settings")
            }
        }

        // Finish Button
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(if (allGranted) "All set - continue to Cue" else "Continue to Cue", fontSize = 16.sp)
        }
    }
}

@Composable
fun PermissionCard(
    permission: SetupWizardViewModel.AdbPermissionState,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(permission.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    Text(permission.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (permission.granted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!permission.granted) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                LabeledField(
                    label = "Run on your laptop",
                    value = permission.adbCommand,
                    onChange = {},
                )
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy ADB command")
                }
            }
        }
    }
}
