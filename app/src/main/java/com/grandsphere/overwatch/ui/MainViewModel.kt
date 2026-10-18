package com.grandsphere.overwatch.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.OverwatchExchange
import com.grandsphere.overwatch.data.OverwatchRepository
import com.grandsphere.overwatch.data.SettingsExport
import com.grandsphere.overwatch.ui.import.OverwatchImportLine
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

    private val _overwatchImportLines = MutableStateFlow<List<OverwatchImportLine>?>(null)
    val overwatchImportLines: StateFlow<List<OverwatchImportLine>?> = _overwatchImportLines.asStateFlow()

    private val _overwatchImportError = MutableStateFlow<String?>(null)
    val overwatchImportError: StateFlow<String?> = _overwatchImportError.asStateFlow()

    private var pendingImportConfigs: List<OverwatchConfig> = emptyList()

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
        val notifyIds = current.notifyEffectIds.filter { it != "popup" }
        val cleaned = current.copy(
            notifyEffectIds = notifyIds,
            notifyBubblePopup = "bubble" in notifyIds,
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

    fun duplicateConfig(config: OverwatchConfig) {
        if (config.isDefault()) return
        viewModelScope.launch {
            val taken = repository.list().map { it.name }.toSet()
            val name = OverwatchExchange.uniqueTempName(taken)
            val source = repository.get(config.id) ?: return@launch
            _draft.value = source.copy(id = 0, name = name)
            _nav.value = Nav.Editor(null)
        }
    }

    fun shareOverwatch(context: Context, config: OverwatchConfig) {
        viewModelScope.launch {
            runCatching {
                shareJsonText(
                    context,
                    OverwatchExchange.overwatchesJson(listOf(config)),
                    "overwatch-${config.name}.json",
                    "Share Overwatch",
                )
            }.onFailure {
                Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateOverwatchImportLine(key: Int, line: OverwatchImportLine) {
        val current = _overwatchImportLines.value ?: return
        _overwatchImportLines.value = current.map { if (it.key == key) line else it }
        _overwatchImportError.value = null
    }

    fun dismissOverwatchImport() {
        _overwatchImportLines.value = null
        _overwatchImportError.value = null
        pendingImportConfigs = emptyList()
    }

    fun confirmOverwatchImport(context: Context) {
        val lines = _overwatchImportLines.value ?: return
        viewModelScope.launch {
            val existing = repository.list().map { it.name }.toSet()
            val selected = lines.filter { it.selected }
            if (selected.isEmpty()) {
                _overwatchImportError.value = "Select at least one Overwatch."
                return@launch
            }
            val names = mutableSetOf<String>()
            for (line in selected) {
                val trimmed = line.name.trim()
                if (trimmed.isBlank()) {
                    _overwatchImportError.value = "Every selected Overwatch needs a name."
                    return@launch
                }
                if (trimmed in existing) {
                    _overwatchImportError.value = "\"$trimmed\" already exists. Choose another name."
                    return@launch
                }
                if (trimmed in names) {
                    _overwatchImportError.value = "Duplicate name \"$trimmed\" in import."
                    return@launch
                }
                names += trimmed
            }
            val byKey = pendingImportConfigs.withIndex().associate { (i, c) -> i to c }
            val toInsert = selected.mapNotNull { line ->
                byKey[line.key]?.copy(name = line.name.trim(), id = 0)
            }
            repository.insertImportedConfigs(toInsert)
            dismissOverwatchImport()
            Toast.makeText(context, "Imported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun beginOverwatchImport(configs: List<OverwatchConfig>) {
        pendingImportConfigs = configs
        viewModelScope.launch {
            val existing = repository.list().map { it.name }.toSet()
            val reserved = existing.toMutableSet()
            val lines = configs.mapIndexed { index, cfg ->
                val conflict = cfg.name in existing
                val name = if (conflict) {
                    OverwatchExchange.uniqueTempName(reserved).also { reserved += it }
                } else {
                    cfg.name
                }
                OverwatchImportLine(
                    key = index,
                    originalName = cfg.name,
                    selected = true,
                    name = name,
                    needsRename = conflict,
                )
            }
            _overwatchImportLines.value = lines
            _overwatchImportError.value = null
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
                    val json = repository.exportOverwatchesJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(StandardCharsets.UTF_8))
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

    fun shareOverwatchesExport(context: Context) {
        viewModelScope.launch {
            runCatching {
                val json = repository.exportOverwatchesJson()
                shareJsonText(context, json, OverwatchExchange.FILE_OVERWATCHES, "Share Overwatches")
            }.onFailure {
                Toast.makeText(context, "Share export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLog(context: Context) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = repository.ensureUserLogShareable()
                        ?: error("No log file")
                    val json = OverwatchExchange.logJson(file.readText())
                    shareJsonText(context, json, "overwatch-log.json", "Share log")
                }
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
                    val text = file.readText()
                    val json = OverwatchExchange.logJson(text)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(StandardCharsets.UTF_8))
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
                    val json = OverwatchExchange.verboseLogJson(file.readText())
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(StandardCharsets.UTF_8))
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
                withContext(Dispatchers.IO) {
                    val file = com.grandsphere.overwatch.runtime.VerboseLog.ensureShareableCopy(context)
                        ?: error("No verbose log")
                    val json = OverwatchExchange.verboseLogJson(file.readText())
                    shareJsonText(context, json, "overwatch-verbose-log.json", "Share verbose log")
                }
            }.onFailure {
                Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importExchange(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { repository.readTextFromUri(uri) }
                importExchangeJson(context, json)
            }.onFailure {
                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importExchangeJson(context: Context, json: String) {
        viewModelScope.launch {
            runCatching {
                when (OverwatchExchange.detectFormat(json)) {
                    OverwatchExchange.FORMAT_OVERWATCHES -> {
                        beginOverwatchImport(OverwatchExchange.parseOverwatches(json))
                    }
                    SettingsExport.FORMAT -> {
                        withContext(Dispatchers.IO) {
                            val merged = SettingsExport.merge(
                                current = settingsStore.settings.first(),
                                json = json,
                                knownConfigIds = repository.list().map { it.id }.toSet(),
                            )
                            settingsStore.update { merged }
                        }
                        refreshOverwatchWidgets(context)
                        Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
                    }
                    OverwatchExchange.FORMAT_LOG -> {
                        withContext(Dispatchers.IO) {
                            val text = OverwatchExchange.parseLogText(json)
                            com.grandsphere.overwatch.runtime.DocumentsLog.appendRaw(context, text)
                        }
                        Toast.makeText(context, "Log imported", Toast.LENGTH_SHORT).show()
                    }
                    OverwatchExchange.FORMAT_VERBOSE_LOG -> {
                        withContext(Dispatchers.IO) {
                            val text = OverwatchExchange.parseVerboseLogText(json)
                            com.grandsphere.overwatch.runtime.VerboseLog.importText(context, text)
                        }
                        Toast.makeText(context, "Verbose log imported", Toast.LENGTH_SHORT).show()
                    }
                    else -> error("Unrecognized export")
                }
            }.onFailure {
                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleIncomingShare(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: return
                viewModelScope.launch {
                    runCatching {
                        when {
                            type.startsWith("text/") -> {
                                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                                    ?: return@launch
                                importExchangeJson(context, text)
                            }
                            else -> {
                                val uri = IntentCompat.getParcelableExtra(
                                    intent,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                ) ?: return@launch
                                val json = withContext(Dispatchers.IO) {
                                    repository.readTextFromUri(uri)
                                }
                                importExchangeJson(context, json)
                            }
                        }
                    }.onFailure {
                        Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
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
                val json = SettingsExport.toJson(settingsStore.settings.first())
                shareJsonText(context, json, SettingsExport.FILE_NAME, "Share settings")
            }.onFailure {
                Toast.makeText(context, "Share settings failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun shareJsonText(
        context: Context,
        json: String,
        fileName: String,
        chooserTitle: String,
    ) {
        val shareUri = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, fileName)
            file.writeText(json)
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newRawUri("export", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
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
