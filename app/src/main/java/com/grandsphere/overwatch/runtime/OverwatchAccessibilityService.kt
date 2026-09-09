package com.grandsphere.overwatch.runtime

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.grandsphere.overwatch.MainActivity
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class OverwatchAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    @Volatile
    private var settings: AppSettings = AppSettings()

    override fun onServiceConnected() {
        instance = this
        val app = OverwatchApp.from(this)
        collectJob = scope.launch {
            app.settingsStore.settings.collect { settings = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (pendingEnableLocation.get()) {
            if (tryClickLocationSwitch()) pendingEnableLocation.set(false)
            return
        }
        if (!settings.preventCloseOnOverwatch) return
        val running = OverwatchApp.from(this).engine.state.value is AppState.Overwatch
        if (!running) return
        if (shouldBringOverwatchForward(event)) bringOverwatchForward()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!settings.hardwareKeysOutsideApp) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return OverwatchApp.from(this).hardware.onKeyDown(event.keyCode, event, settings)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun shouldBringOverwatchForward(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        if (pkg == packageName) return false
        val className = event.className?.toString().orEmpty()
        val recents = className.contains("Recents", ignoreCase = true) ||
            className.contains("Overview", ignoreCase = true) ||
            (pkg == "com.android.systemui" && className.contains("Recent", ignoreCase = true))
        val home = isHomePackage(pkg)
        val appInfo = isOurAppInfo(pkg, className)
        return recents || home || appInfo
    }

    private fun isHomePackage(pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == pkg
    }

    private fun isOurAppInfo(pkg: String, className: String): Boolean {
        if (!pkg.contains("settings", ignoreCase = true)) return false
        if (className.contains("InstalledAppDetails", ignoreCase = true) ||
            className.contains("ApplicationDetails", ignoreCase = true) ||
            className.contains("AppInfo", ignoreCase = true)
        ) {
            return true
        }
        val hay = StringBuilder()
        eventText(rootInActiveWindow, hay)
        return hay.contains(packageName) || hay.contains("Overwatch", ignoreCase = true)
    }

    private fun eventText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) eventText(node.getChild(i), out)
    }

    private fun bringOverwatchForward() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun tryClickLocationSwitch(): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickFirstOffSwitch(root)
    }

    private fun clickFirstOffSwitch(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString().orEmpty()
        val isSwitch = cls.contains("Switch", ignoreCase = true) ||
            cls.contains("Toggle", ignoreCase = true) ||
            cls.contains("CheckBox", ignoreCase = true)
        if (isSwitch && node.isClickable && !node.isChecked) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickFirstOffSwitch(child)) return true
        }
        return false
    }

    companion object {
        @Volatile
        private var instance: OverwatchAccessibilityService? = null
        private val pendingEnableLocation = AtomicBoolean(false)

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, OverwatchAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val want = expected.flattenToString()
            val shortName = "${context.packageName}/.${OverwatchAccessibilityService::class.java.simpleName}"
            return enabled.split(':').any { entry ->
                entry.equals(want, ignoreCase = true) ||
                    entry.equals(shortName, ignoreCase = true) ||
                    entry.endsWith(OverwatchAccessibilityService::class.java.name)
            }
        }

        fun requestEnableLocation() {
            pendingEnableLocation.set(true)
            instance?.openLocationSettings()
        }
    }
}
