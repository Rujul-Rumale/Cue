package dev.context.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.context.app.automation.engine.TriggerEvent
import dev.context.app.core.Clock
import dev.context.app.model.ActionExecution
import dev.context.app.model.ActionSpec
import dev.context.app.model.ActionType
import dev.context.app.model.Param
import dev.context.app.model.TriggerType

/**
 * System UI automation: global actions, taps, swipes and key interception.
 *
 * Enable once in system settings (or the in-app setup wizard). Every entry
 * point degrades honestly when the service is off: actions report failure
 * with a message instead of crashing.
 */
class CueAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (pkg == packageName) return // ignore our own windows
        runCatching {
            dev.context.app.ContextApp.container.dispatch(
                TriggerEvent(
                    TriggerType.FOREGROUND_APP_CHANGED,
                    mapOf(
                        Param.PACKAGE to pkg,
                        Param.AT to Clock.SYSTEM.nowMillis().toString(),
                    ),
                ),
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> "volume_up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "volume_down"
            else -> return super.onKeyEvent(event)
        }
        runCatching {
            dev.context.app.ContextApp.container.dispatch(
                TriggerEvent(
                    TriggerType.VOLUME_KEY_PRESSED,
                    mapOf(
                        Param.KEY to key,
                        Param.AT to Clock.SYSTEM.nowMillis().toString(),
                    ),
                ),
            )
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: CueAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null

        fun perform(action: ActionSpec): ActionExecution {
            val svc = instance
                ?: return ActionExecution(action, ok = false, message = "enable the Cue accessibility service first")
            return try {
                when (action.type) {
                    ActionType.LOCK_SCREEN -> svc.globalAction(GLOBAL_ACTION_LOCK_SCREEN, action, "locked")
                    ActionType.OPEN_NOTIFICATIONS -> svc.globalAction(GLOBAL_ACTION_NOTIFICATIONS, action, "shade open")
                    ActionType.OPEN_QUICK_SETTINGS -> svc.globalAction(GLOBAL_ACTION_QUICK_SETTINGS, action, "quick settings open")
                    ActionType.TAKE_SCREENSHOT ->
                        if (Build.VERSION.SDK_INT >= 28) svc.globalAction(GLOBAL_ACTION_TAKE_SCREENSHOT, action, "screenshot")
                        else ActionExecution(action, ok = false, message = "needs Android 9+")
                    ActionType.CLICK_AT_COORDS -> svc.tap(action)
                    ActionType.CLICK_BY_TEXT -> svc.tapText(action)
                    ActionType.SWIPE_GESTURE -> svc.swipe(action)
                    else -> ActionExecution(action, ok = false, message = "not an accessibility action")
                }
            } catch (t: Throwable) {
                ActionExecution(action, ok = false, message = t.message ?: t.javaClass.simpleName)
            }
        }

        private fun CueAccessibilityService.globalAction(id: Int, action: ActionSpec, okMsg: String): ActionExecution {
            val ok = performGlobalAction(id)
            return ActionExecution(action, ok = ok, message = if (ok) okMsg else "system refused")
        }

        private fun CueAccessibilityService.tap(action: ActionSpec): ActionExecution {
            val x = action.params[Param.X]?.toFloatOrNull()
                ?: return ActionExecution(action, ok = false, message = "missing x")
            val y = action.params[Param.Y]?.toFloatOrNull()
                ?: return ActionExecution(action, ok = false, message = "missing y")
            return gesture(action, Path().apply { moveTo(x, y) }, 80L, "tapped $x,$y")
        }

        private fun CueAccessibilityService.tapText(action: ActionSpec): ActionExecution {
            val text = action.params[Param.TEXT].orEmpty()
            if (text.isBlank()) return ActionExecution(action, ok = false, message = "missing text")
            val root = rootInActiveWindow ?: return ActionExecution(action, ok = false, message = "no active window")
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val node = nodes.firstOrNull()
            nodes.forEach { if (it !== node) it.recycle() }
            if (node == null) return ActionExecution(action, ok = false, message = "text not on screen")
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.recycle()
            return ActionExecution(action, ok = ok, message = if (ok) "tapped \"$text\"" else "tap refused")
        }

        private fun CueAccessibilityService.swipe(action: ActionSpec): ActionExecution {
            val display = runCatching {
                val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            }.getOrNull() ?: (1080 to 1920)
            val (w, h) = display
            val (x0, y0, x1, y1) = when (action.params[Param.DIRECTION]) {
                "down" -> floatArrayOf(w / 2f, h * 0.25f, w / 2f, h * 0.75f)
                "up" -> floatArrayOf(w / 2f, h * 0.75f, w / 2f, h * 0.25f)
                "left" -> floatArrayOf(w * 0.75f, h / 2f, w * 0.25f, h / 2f)
                else -> floatArrayOf(w * 0.25f, h / 2f, w * 0.75f, h / 2f)
            }
            val path = Path().apply { moveTo(x0, y0); lineTo(x1, y1) }
            return gesture(action, path, 300L, "swiped ${action.params[Param.DIRECTION] ?: "right"}")
        }

        private fun CueAccessibilityService.gesture(
            action: ActionSpec,
            path: Path,
            durationMs: Long,
            okMsg: String,
        ): ActionExecution {
            if (Build.VERSION.SDK_INT < 24) {
                return ActionExecution(action, ok = false, message = "needs Android 7+")
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val dispatched = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            return ActionExecution(action, ok = dispatched, message = if (dispatched) okMsg else "gesture refused")
        }
    }
}
