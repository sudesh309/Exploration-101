package com.kidlearn.feature.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.UploadResult
import com.kidlearn.domain.repository.QuestionRepository
import com.kidlearn.domain.repository.SyncSettingsRepository
import com.kidlearn.domain.usecase.UploadQuestions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class QuestionsUiState(
    val loading: Boolean = true,
    val questions: List<KidQuestion> = emptyList(),
    val showAllLearners: Boolean = false,
    val pendingUpload: Int = 0,
    val canUpload: Boolean = false,
    val uploadTarget: String = "",
    val busy: Boolean = false,
)

class QuestionsViewModel(
    private val questions: QuestionRepository,
    private val settings: SyncSettingsRepository,
    private val uploadQuestions: UploadQuestions,
    private val profileId: Long,
) : ViewModel() {
    private val showAll = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Files ready to hand to the system share sheet. */
    private val _shareFile = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val shareFile: SharedFlow<File> = _shareFile.asSharedFlow()

    val state: StateFlow<QuestionsUiState> = combine(
        questions.observeQuestions(),
        settings.observeSettings(),
        showAll,
        busy,
    ) { all, sync, everyone, working ->
        QuestionsUiState(
            loading = false,
            questions = all.filter { everyone || it.profileId == profileId }.sortedByDescending { it.askedAtMillis },
            showAllLearners = everyone,
            pendingUpload = all.count { !it.isUploaded },
            canUpload = sync.github.canUpload,
            uploadTarget = sync.github.displayName,
            busy = working,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuestionsUiState())

    fun setShowAllLearners(show: Boolean) {
        showAll.value = show
    }

    fun delete(question: KidQuestion) {
        viewModelScope.launch { questions.delete(question.id) }
    }

    /** Exports the whole log (all learners) as a JSON file for sharing (email, chat, Drive app…). */
    fun share() = work {
        val s = settings.current()
        _shareFile.emit(questions.exportForSharing(settings.deviceId(), s.deviceName))
    }

    /** Uploads the whole log (all learners on this device) to the configured GitHub repository. */
    fun upload() = work {
        val all = questions.observeQuestions().first()
        val message = when (val result = uploadQuestions(all)) {
            is UploadResult.Uploaded -> "✅ Uploaded ${result.count} questions to ${result.location}"
            UploadResult.NothingToUpload -> "👍 Everything is already uploaded"
            is UploadResult.Failed -> "⚠️ ${result.reason}"
        }
        _messages.emit(message)
    }

    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.emit("⚠️ ${e.message ?: "Something went wrong"}")
            } finally {
                busy.value = false
            }
        }
    }
}
