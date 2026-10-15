package com.kidlearn.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.ProgressRepository
import com.kidlearn.domain.usecase.completedLessonIds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface CourseUiState {
    data object Loading : CourseUiState
    data object NotFound : CourseUiState
    data class Ready(val course: Course, val completedLessonIds: Set<String>) : CourseUiState {
        val nextLessonId: String? get() = course.lessons.firstOrNull { it.id !in completedLessonIds }?.id
    }
}

class CourseViewModel(
    courses: CourseRepository,
    progress: ProgressRepository,
    profileId: Long,
    courseId: String,
) : ViewModel() {
    val state: StateFlow<CourseUiState> = combine(
        flow { emit(courses.getCourse(courseId)) },
        progress.observeLessonProgress(profileId),
    ) { course, done ->
        if (course == null) CourseUiState.NotFound else CourseUiState.Ready(course, course.completedLessonIds(done))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseUiState.Loading)
}
