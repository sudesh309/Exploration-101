package com.kidlearn.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.Subject
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.ProfileRepository
import com.kidlearn.domain.usecase.CoursesForLearner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val subjects: List<Subject> = emptyList(),
    val selectedSubjectId: String? = null,
    val courses: List<CourseSummary> = emptyList(),
    val showAllAges: Boolean = false,
    val hiddenByAge: Int = 0,
)

class HomeViewModel(
    courses: CourseRepository,
    profiles: ProfileRepository,
    profileId: Long,
    private val coursesForLearner: CoursesForLearner = CoursesForLearner(),
) : ViewModel() {
    private val selectedSubject = MutableStateFlow<String?>(null)
    private val showAllAges = MutableStateFlow(false)
    private val profile = profiles.observeProfiles().map { list -> list.firstOrNull { it.id == profileId } }

    val state: StateFlow<HomeUiState> = combine(
        courses.observeCourses(),
        profile,
        selectedSubject,
        showAllAges,
    ) { all, learner, subjectId, allAges ->
        val forAge = coursesForLearner(all, learner)
        val visible = if (allAges) all.sortedBy { it.title } else forAge
        HomeUiState(
            loading = false,
            profile = learner,
            subjects = visible.map { it.subject }.distinctBy { it.id }.sortedBy { it.name },
            selectedSubjectId = subjectId,
            courses = visible.filter { subjectId == null || it.subject.id == subjectId },
            showAllAges = allAges,
            hiddenByAge = all.size - forAge.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectSubject(subjectId: String?) {
        selectedSubject.value = subjectId
    }

    fun setShowAllAges(show: Boolean) {
        showAllAges.value = show
    }
}
