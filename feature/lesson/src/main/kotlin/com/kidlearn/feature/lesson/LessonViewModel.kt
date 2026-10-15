package com.kidlearn.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.QuestionResponse
import com.kidlearn.domain.model.Quiz
import com.kidlearn.domain.model.QuizResult
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.ProgressRepository
import com.kidlearn.domain.usecase.CompleteLesson
import com.kidlearn.domain.usecase.SubmitQuiz
import com.kidlearn.ui.blocks.LessonSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data object NotFound : LessonUiState
    data class Ready(
        val course: Course,
        val lesson: Lesson,
        val position: Int,
        val nextLessonId: String?,
        val completed: Boolean,
    ) : LessonUiState
}

class LessonViewModel(
    private val courses: CourseRepository,
    progress: ProgressRepository,
    private val completeLesson: CompleteLesson,
    private val submitQuizUseCase: SubmitQuiz,
    override val profileId: Long,
    override val courseId: String,
    override val lessonId: String,
) : ViewModel(), LessonSession {

    private val loaded = MutableStateFlow<Pair<Course, Lesson>?>(null)
    private val missing = MutableStateFlow(false)

    val state: StateFlow<LessonUiState> = combine(
        loaded,
        missing,
        progress.observeLessonProgress(profileId).map { list -> list.any { it.courseId == courseId && it.lessonId == lessonId } },
    ) { pair, notFound, done ->
        when {
            notFound -> LessonUiState.NotFound
            pair == null -> LessonUiState.Loading
            else -> {
                val (course, lesson) = pair
                val index = course.lessons.indexOf(lesson)
                LessonUiState.Ready(course, lesson, index + 1, course.lessons.getOrNull(index + 1)?.id, done)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonUiState.Loading)

    init {
        viewModelScope.launch {
            val course = courses.getCourse(courseId)
            val lesson = course?.lesson(lessonId)
            if (course == null || lesson == null) missing.value = true else loaded.value = course to lesson
        }
    }

    fun markCompleted() {
        val (_, lesson) = loaded.value ?: return
        viewModelScope.launch { completeLesson(profileId, courseId, lesson) }
    }

    override suspend fun submitQuiz(quiz: Quiz, responses: Map<String, QuestionResponse>): QuizResult =
        submitQuizUseCase(profileId, courseId, lessonId, quiz, responses)
}
