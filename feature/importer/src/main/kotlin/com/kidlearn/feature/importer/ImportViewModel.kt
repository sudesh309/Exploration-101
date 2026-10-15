package com.kidlearn.feature.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Turns the places a pack can come from into [CoursePackSource]s. Implemented by the data layer. */
interface PackSourceProvider {
    fun fromUri(uri: Uri): CoursePackSource
    suspend fun fromUrl(url: String): CoursePackSource
    fun importFolderPath(): String

    /** Installs every pack waiting in the import folder. */
    suspend fun importFromFolder(): List<InstallOutcome>
}

data class ImportStatus(val busy: Boolean = false, val messages: List<String> = emptyList())

class ImportViewModel(
    private val courses: CourseRepository,
    private val sources: PackSourceProvider,
) : ViewModel() {
    val installed: StateFlow<List<CourseSummary>> =
        courses.observeCourses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow(ImportStatus())
    val status: StateFlow<ImportStatus> = _status.asStateFlow()

    val importFolderPath: String get() = sources.importFolderPath()

    fun importFile(uri: Uri) = launchImport { listOf(courses.install(sources.fromUri(uri))) }

    fun importUrl(url: String) = launchImport {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) { "Please enter a web link starting with https://" }
        listOf(courses.install(sources.fromUrl(trimmed)))
    }

    fun scanImportFolder() = launchImport {
        sources.importFromFolder().ifEmpty { throw IllegalStateException("No .zip or .json files found in the import folder.") }
    }

    fun remove(courseId: String) {
        viewModelScope.launch { courses.remove(courseId) }
    }

    private fun launchImport(block: suspend () -> List<InstallOutcome>) {
        if (_status.value.busy) return
        _status.value = ImportStatus(busy = true)
        viewModelScope.launch {
            val messages = try {
                block().map(::describe)
            } catch (e: Exception) {
                listOf("⚠️ ${e.message ?: "Import failed"}")
            }
            _status.value = ImportStatus(busy = false, messages = messages)
        }
    }

    private fun describe(outcome: InstallOutcome): String = when (outcome) {
        is InstallOutcome.Installed -> "✅ Added \"${outcome.course.title}\" (${outcome.course.subject.name})"
        is InstallOutcome.Updated -> "🔄 Updated \"${outcome.course.title}\" to version ${outcome.course.version}"
        is InstallOutcome.AlreadyUpToDate -> "👍 \"${outcome.course.title}\" is already up to date"
        is InstallOutcome.Rejected -> "⚠️ Could not add this pack:\n• " + outcome.problems.take(8).joinToString("\n• ")
    }
}
