package com.grandsphere.overwatch.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.data.SettingsExport
import com.grandsphere.overwatch.data.SettingsStore
import com.grandsphere.overwatch.data.db.SeedConfigs
import com.grandsphere.overwatch.domain.engine.OverwatchEngine
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.widget.refreshOverwatchWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

class MainViewModel(
    val engine: OverwatchEngine,
    private val repository: OverwatchRepository,
    private val settingsStore: SettingsStore,
    private val app: OverwatchApp,
) : ViewModel() {

    val appState: StateFlow<AppState> = engine.state
    val configs: StateFlow<List<OverwatchConfig>> = repository.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _nav = MutableStateFlow<Nav>(Nav.Home)
    val nav: StateFlow<Nav> = _nav.asStateFlow()

    private val _draft = MutableStateFlow<OverwatchConfig?>(null)
    val draft: StateFlow<OverwatchConfig?> = _draft.asStateFlow()

    fun openDetail(id: Long) {
        _nav.value = Nav.Detail(id)
    }

    fun openEditor(id: Long?) {
        viewModelScope.launch {
            _draft.value = if (id == null) {
                SeedConfigs.blankNew()
            } else {
                repository.get(id) ?: SeedConfigs.blankNew()
            }
            _nav.value = Nav.Editor(id)
        }
    }

    fun updateDraft(config: OverwatchConfig) {
        _draft.value = config
    }

    fun saveDraft() {
        val current = _draft.value ?: return
        val cleaned = current.copy(
            notifyEffectIds = current.notifyEffectIds.filter { it != "popup" },
            graceNotifyEffectIds = current.graceNotifyEffectIds.filter { it != "popup" },
            safetyEffectIds = current.safetyEffectIds.filter {
                com.grandsphere.overwatch.domain.catalog.AlarmCatalog.isAllowedInSafety(it)
            },
            safetyLocationRecent = false,
            safetyLocationContinuous = false,
            safetySoundDurationMs = current.safetySoundDurationMs.coerceAtLeast(1_000L),
        )
        val named = if (cleaned.isDefault()) cleaned.copy(name = OverwatchConfig.DEFAULT_NAME) else cleaned
        viewModelScope.launch {
            val id = repository.save(named)
            _nav.value = Nav.Detail(id)
        }
    }

    fun goHome() {
        _nav.value = Nav.Home
    }

    fun openSettings() {
        _nav.value = Nav.Settings
    }

    fun openAbout() {
        _nav.value = Nav.About
    }

    fun openHelp() {
        _nav.value = Nav.Help
    }

    fun back() {
        _nav.value = when (val n = _nav.value) {
            Nav.Home -> Nav.Home
            is Nav.Detail -> Nav.Home
            is Nav.Editor -> {
                _draft.value = null
                n.id?.let { Nav.Detail(it) } ?: Nav.Home
            }
            Nav.Settings, Nav.About, Nav.Help -> Nav.Home
        }
    }

    fun enable(config: OverwatchConfig) {
        com.grandsphere.overwatch.runtime.VerboseLog.d("UI", "enable ${config.name}")
        if (!engine.enable(config)) {
            val running = engine.state.value as? AppState.Overwatch ?: return
            com.grandsphere.overwatch.runtime.OverwatchNotifications.announceCurrentlyRunning(app, running)
        }
    }

    fun panic() {
        viewModelScope.launch {
            val fallback = repository.defaultConfig()
                ?: configs.value.firstOrNull { it.isDefault() }
                ?: configs.value.firstOrNull()
            com.grandsphere.overwatch.runtime.VerboseLog.d("UI", "panic fallback=${fallback?.name}")
            engine.panic(fallback)
        }
    }

    fun deleteConfig(config: OverwatchConfig) {
        if (config.isDefault()) return
        viewModelScope.launch {
            repository.delete(config.id)
            if (_nav.value is Nav.Detail) _nav.value = Nav.Home
        }
    }

    fun clearOverwatches() {
        viewModelScope.launch {
            repository.clearOverwatchesKeepDefault()
            _nav.value = Nav.Home
        }
    }

    fun writeExportTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = repository.exportConfigsDb()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Could not write export")
                }
            }.onSuccess {
                Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching { settingsStore.update(transform) }
                .onSuccess { onSuccess?.invoke() }
        }
    }

    fun exportDb(context: Context) {
        viewModelScope.launch {
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    val file = repository.exportConfigsDb()
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("export", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share export"))
            }.onFailure {
                Toast.makeText(context, "Share export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLog(context: Context) {
        viewModelScope.launch {
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    val file = repository.ensureUserLogShareable()
                        ?: error("No log file")
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("log", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share log"))
            }.onFailure {
                Toast.makeText(context, "Share log failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun writeLogTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = repository.ensureUserLogShareable()
                        ?: error("No log file")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Could not write log")
                }
            }.onSuccess {
                Toast.makeText(context, "Log exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun writeVerboseLogTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = com.grandsphere.overwatch.runtime.VerboseLog.ensureShareableCopy(context)
                        ?: error("No verbose log")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Could not write verbose log")
                }
            }.onSuccess {
                Toast.makeText(context, "Verbose log exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareVerboseLog(context: Context) {
        viewModelScope.launch {
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    val file = com.grandsphere.overwatch.runtime.VerboseLog.ensureShareableCopy(context)
                        ?: error("No verbose log")
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("verbose-log", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share export verbose log"))
            }.onFailure {
                Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importDb(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.importConfigs(uri) }
                .onFailure {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun writeSettingsExportTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = SettingsExport.toJson(settingsStore.settings.first())
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Could not write settings export")
                }
            }.onSuccess {
                Toast.makeText(context, "Settings exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Settings export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareSettings(context: Context) {
        viewModelScope.launch {
            runCatching {
                val shareUri = withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, SettingsExport.FILE_NAME)
                    file.writeText(SettingsExport.toJson(settingsStore.settings.first()))
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    clipData = ClipData.newRawUri("settings", shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share export settings"))
            }.onFailure {
                Toast.makeText(context, "Share settings failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importSettings(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val tmp = File(context.cacheDir, "overwatch-settings-import.json")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Could not read settings import")
                    try {
                        val merged = SettingsExport.merge(
                            current = settingsStore.settings.first(),
                            json = tmp.readText(),
                            knownConfigIds = repository.list().map { it.id }.toSet(),
                        )
                        settingsStore.update { merged }
                    } finally {
                        tmp.delete()
                    }
                }
                refreshOverwatchWidgets(context)
            }.onSuccess {
                Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Settings import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        fun factory(app: OverwatchApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app.engine, app.repository, app.settingsStore, app) as T
                }
            }
    }
}
