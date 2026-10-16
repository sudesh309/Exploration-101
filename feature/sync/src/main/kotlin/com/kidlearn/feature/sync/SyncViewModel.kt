package com.kidlearn.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.SourceSyncResult
import com.kidlearn.domain.model.SyncSettings
import com.kidlearn.domain.repository.ContentSync
import com.kidlearn.domain.repository.SyncSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val form: SyncSettings = SyncSettings(),
    val loaded: Boolean = false,
    val dirty: Boolean = false,
    val downloading: Boolean = false,
    val report: List<String> = emptyList(),
)

class SyncViewModel(
    private val settings: SyncSettingsRepository,
    private val contentSync: ContentSync,
    private val isOnline: () -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(form = settings.current(), loaded = true) } }
    }

    fun edit(transform: (SyncSettings) -> SyncSettings) {
        _state.update { it.copy(form = transform(it.form), dirty = true) }
    }

    fun save() {
        viewModelScope.launch {
            settings.save(_state.value.form)
            _state.update { it.copy(form = settings.current(), dirty = false, report = listOf("✅ Settings saved")) }
        }
    }

    /** Saves pending edits, then downloads from every configured source. */
    fun download(force: Boolean) {
        val current = _state.value
        if (current.downloading) return
        val form = current.form
        if (!form.github.isConfigured && !form.drive.isConfigured) {
            _state.update { it.copy(report = listOf("⚠️ Set up GitHub or Google Drive first.")) }
            return
        }
        if (!isOnline()) {
            _state.update { it.copy(report = listOf("📶 No internet connection. Connect to Wi-Fi or mobile data and try again.")) }
            return
        }
        _state.update { it.copy(downloading = true, report = listOf("⬇️ Downloading…")) }
        viewModelScope.launch {
            if (current.dirty) settings.save(form)
            val report = try {
                contentSync.downloadAll(settings.current(), force).flatMap(::describe)
            } catch (e: Exception) {
                listOf("⚠️ ${e.message ?: "Download failed"}")
            }
            _state.update { it.copy(downloading = false, dirty = false, report = report) }
        }
    }

    private fun describe(result: SourceSyncResult): List<String> = when (result) {
        is SourceSyncResult.UpToDate -> listOf("👍 ${result.sourceName}: nothing new")
        is SourceSyncResult.Failed -> listOf("⚠️ ${result.sourceName}: ${result.reason}")
        is SourceSyncResult.Downloaded -> listOf("📦 ${result.sourceName}:") + result.outcomes.map { outcome ->
            when (outcome) {
                is InstallOutcome.Installed -> "   ✅ Added \"${outcome.course.title}\""
                is InstallOutcome.Updated -> "   🔄 Updated \"${outcome.course.title}\" to v${outcome.course.version}"
                is InstallOutcome.AlreadyUpToDate -> "   👍 \"${outcome.course.title}\" already up to date"
                is InstallOutcome.Rejected -> "   ⚠️ " + outcome.problems.take(3).joinToString("; ")
            }
        }.ifEmpty { listOf("   (no courses found)") }
    }
}
