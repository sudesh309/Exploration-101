package com.kidlearn.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.ProfileReport
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.ProfileRepository
import com.kidlearn.domain.repository.ProgressRepository
import com.kidlearn.domain.usecase.BuildProfileReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data object NoProfile : ResultsUiState
    data class Ready(val report: ProfileReport) : ResultsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModel(
    courses: CourseRepository,
    profiles: ProfileRepository,
    progress: ProgressRepository,
    profileId: Long,
    buildReport: BuildProfileReport = BuildProfileReport(),
) : ViewModel() {
    private val installedCourses = courses.observeCourses().flatMapLatest { summaries ->
        flow { emit(summaries.mapNotNull { courses.getCourse(it.id) }) }
    }

    val state: StateFlow<ResultsUiState> = combine(
        profiles.observeProfiles().map { list -> list.firstOrNull { it.id == profileId } },
        installedCourses,
        progress.observeLessonProgress(profileId),
        progress.observeQuizAttempts(profileId),
    ) { profile, courseList, lessons, attempts ->
        if (profile == null) ResultsUiState.NoProfile
        else ResultsUiState.Ready(buildReport(profile, courseList, lessons, attempts))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState.Loading)
}
