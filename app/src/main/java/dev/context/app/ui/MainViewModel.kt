package dev.context.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.context.app.ContextApp
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.automation.repository.Backup
import dev.context.app.model.ActionSpec
import dev.context.app.model.Automation
import dev.context.app.model.ConditionSpec
import dev.context.app.model.ExecutionLog
import dev.context.app.model.ExecutionMode
import dev.context.app.model.LocationMarker
import dev.context.app.model.TriggerSpec
import dev.context.app.model.Variable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Locations : Screen()
    object Logs : Screen()
    object Permissions : Screen()
    object Settings : Screen()
    object Debug : Screen()
    data class Editor(val automationId: Long) : Screen()
}

class EditorDraft(
    val automationId: Long = 0L,
    var name: String = "",
    var trigger: TriggerSpec? = null,
    var conditions: List<ConditionSpec> = emptyList(),
    var matchAny: Boolean = false,
    var actions: List<ActionSpec> = emptyList(),
    var mode: ExecutionMode = ExecutionMode.ONCE_PER_TRANSITION,
    var cooldownMinutes: Int = 30,
    var useGpsForLocation: Boolean = false,
)

/** One global view model for the single-activity app. Navigation is a plain back stack. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = ContextApp.container
    private val repo get() = container.repository
    private val settings get() = container.settings

    val automations: StateFlow<List<Automation>>
    val locations: StateFlow<List<LocationMarker>>
    val logs: StateFlow<List<ExecutionLog>>
    val variables: StateFlow<List<Variable>>
    val devMode: StateFlow<Boolean>
    val hysteresisMinutes: StateFlow<Int>
    val lowPowerMode: StateFlow<Boolean>
    val fgServiceNotification: StateFlow<Boolean>

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val backStack = mutableListOf<Screen>()

    private val _draft = MutableStateFlow<EditorDraft?>(null)
    val draft: StateFlow<EditorDraft?> = _draft.asStateFlow()

    init {
        automations = repo.observeAutomations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        locations = repo.observeLocations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        logs = repo.observeLogs(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        variables = repo.observeVariables()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        devMode = settings.developerMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        hysteresisMinutes = settings.geofenceHysteresisMs
            .map { (it / 60_000L).toInt() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
        lowPowerMode = settings.lowPowerMode
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        fgServiceNotification = settings.fgServiceNotification
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    }

    // ---- Navigation --------------------------------------------------------

    fun push(screen: Screen) {
        backStack.add(_screen.value)
        _screen.value = screen
    }

    fun pop() {
        _screen.value = if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) else Screen.Home
    }

    // ---- Editor ------------------------------------------------------------

    fun openEditor(automationId: Long = 0L) {
        val existing = if (automationId != 0L) automations.value.find { it.id == automationId } else null
        val d = EditorDraft(automationId)
        if (existing != null) {
            d.name = existing.name
            d.trigger = existing.trigger
            d.conditions = existing.conditions
            d.matchAny = existing.matchAny
            d.actions = existing.actions
            d.mode = existing.mode
            d.cooldownMinutes = existing.cooldownMinutes
            d.useGpsForLocation = existing.useGpsForLocation
        }
        _draft.value = d
        push(Screen.Editor(automationId))
    }

    fun saveDraft(): Boolean {
        val d = _draft.value ?: return false
        if (d.name.isBlank() || d.trigger == null) return false
        viewModelScope.launch {
            val existing = if (d.automationId != 0L)
                automations.value.find { it.id == d.automationId } else null
            val automation = Automation(
                id = d.automationId,
                name = d.name.trim(),
                enabled = existing?.enabled ?: true,
                trigger = d.trigger!!,
                conditions = d.conditions,
                matchAny = d.matchAny,
                actions = d.actions,
                mode = d.mode,
                cooldownMinutes = d.cooldownMinutes,
                useGpsForLocation = d.useGpsForLocation,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            repo.saveAutomation(automation)
            container.notifyDataChanged()
        }
        _draft.value = null
        pop()
        return true
    }

    fun cancelEditor() {
        _draft.value = null
        pop()
    }

    /** Re-emit an edited draft. EditorDraft has identity equality, so a fresh instance
     * always propagates through StateFlow and recomposes the editor. */
    fun updateDraft(draft: EditorDraft) {
        _draft.value = EditorDraft(
            automationId = draft.automationId,
            name = draft.name,
            trigger = draft.trigger,
            conditions = draft.conditions.toList(),
            matchAny = draft.matchAny,
            actions = draft.actions.toList(),
            mode = draft.mode,
            cooldownMinutes = draft.cooldownMinutes,
            useGpsForLocation = draft.useGpsForLocation,
        )
    }

    // ---- Automation ops ----------------------------------------------------

    fun toggleAutomation(automation: Automation) {
        viewModelScope.launch {
            repo.setEnabled(automation.id, !automation.enabled)
            container.notifyDataChanged()
        }
    }

    fun deleteAutomation(automation: Automation) {
        viewModelScope.launch {
            repo.deleteAutomation(automation.id)
            container.notifyDataChanged()
        }
    }

    // ---- Locations ---------------------------------------------------------

    fun saveLocation(location: LocationMarker) {
        viewModelScope.launch {
            repo.saveLocation(location)
            container.notifyDataChanged()
        }
    }

    fun deleteLocation(location: LocationMarker) {
        viewModelScope.launch {
            repo.deleteLocation(location.id)
            container.insideStore.clearLocation(location.id)
            container.notifyDataChanged()
        }
    }

    // ---- Logs / variables / settings --------------------------------------

    fun clearLogs() {
        viewModelScope.launch { repo.clearLogs() }
    }

    fun deleteLog(log: ExecutionLog) {
        viewModelScope.launch { repo.deleteLog(log.id) }
    }

    fun saveVariables(next: List<Variable>) {
        viewModelScope.launch {
            for (v in next) repo.setVariable(v)
        }
    }

    fun deleteVariable(key: String) {
        viewModelScope.launch { repo.deleteVariable(key) }
    }

    fun setSettings(
        devMode: Boolean? = null,
        hysteresisMinutes: Int? = null,
        lowPowerMode: Boolean? = null,
        fgServiceNotification: Boolean? = null,
    ) {
        viewModelScope.launch {
            if (devMode != null) settings.setDeveloperMode(devMode)
            if (hysteresisMinutes != null) settings.setHysteresisMinutes(hysteresisMinutes)
            if (lowPowerMode != null) {
                settings.setLowPowerMode(lowPowerMode)
                container.notifyDataChanged()
            }
            if (fgServiceNotification != null) {
                settings.setFgServiceNotification(fgServiceNotification)
                container.restartForegroundService()
            }
        }
    }

    // ---- Backup / restore --------------------------------------------------

    fun buildBackup(): Backup =
        Backup(automations = automations.value, locations = locations.value, variables = variables.value)

    fun applyBackup(backup: Backup, replace: Boolean) {
        viewModelScope.launch {
            if (replace) {
                repo.replaceAll(backup.automations, backup.locations, backup.variables)
            } else {
                for (a in backup.automations) repo.saveAutomation(a.copy(id = 0L))
                for (l in backup.locations) repo.saveLocation(l.copy(id = 0L))
                for (v in backup.variables) repo.setVariable(v)
            }
            container.notifyDataChanged()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repo.replaceAll(emptyList(), emptyList(), emptyList())
            container.notifyDataChanged()
        }
    }

    // ---- Debug -------------------------------------------------------------

    fun debugEmit(event: TriggerEvent) {
        container.dispatch(event.copy(source = "debug"))
    }
}