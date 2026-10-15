package com.kidlearn.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kidlearn.feature.catalog.CourseScreen
import com.kidlearn.feature.catalog.CourseViewModel
import com.kidlearn.feature.catalog.HomeScreen
import com.kidlearn.feature.catalog.HomeViewModel
import com.kidlearn.feature.importer.ImportScreen
import com.kidlearn.feature.importer.ImportViewModel
import com.kidlearn.feature.lesson.LessonScreen
import com.kidlearn.feature.lesson.LessonViewModel
import com.kidlearn.feature.profiles.EditProfileScreen
import com.kidlearn.feature.profiles.EditProfileViewModel
import com.kidlearn.feature.profiles.ProfilesScreen
import com.kidlearn.feature.profiles.ProfilesViewModel
import com.kidlearn.feature.results.ResultsScreen
import com.kidlearn.feature.results.ResultsViewModel

private object Routes {
    const val PROFILES = "profiles"
    const val EDIT_PROFILE = "profile/edit?id={id}"
    const val HOME = "home"
    const val COURSE = "course/{courseId}"
    const val LESSON = "lesson/{courseId}/{lessonId}"
    const val RESULTS = "results"
    const val IMPORT = "import"

    fun editProfile(id: Long?) = "profile/edit" + (id?.let { "?id=$it" } ?: "")
    fun course(courseId: String) = "course/$courseId"
    fun lesson(courseId: String, lessonId: String) = "lesson/$courseId/$lessonId"
}

/**
 * The app's navigation graph: the only place that knows about every feature. Features only
 * receive the domain interfaces they need, keeping them independent and replaceable.
 */
@Composable
fun KidLearnNavHost(container: AppContainer, sharedPack: Uri?) {
    val nav = rememberNavController()
    // A pack shared into the app is imported exactly once, even across rotation.
    var pendingPack by rememberSaveable { mutableStateOf(sharedPack?.toString()) }
    val start = if (container.session.currentProfileId != null) Routes.HOME else Routes.PROFILES
    // The learner is chosen on the profiles screen; every other screen reads it from the session.
    fun profileId(): Long = container.session.currentProfileId ?: -1L

    NavHost(nav, startDestination = start) {
        composable(Routes.PROFILES) {
            ProfilesScreen(
                viewModel = viewModel { ProfilesViewModel(container.profiles) },
                onSelect = { profile ->
                    container.session.currentProfileId = profile.id
                    nav.navigate(Routes.HOME) { popUpTo(Routes.PROFILES) { inclusive = true } }
                },
                onAdd = { nav.navigate(Routes.editProfile(null)) },
                onEdit = { nav.navigate(Routes.editProfile(it.id)) },
            )
        }
        composable(Routes.EDIT_PROFILE, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            EditProfileScreen(
                viewModel = viewModel { EditProfileViewModel(container.profiles, id) },
                isNew = id == null,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container.courses, container.profiles, profileId()) },
                onOpenCourse = { nav.navigate(Routes.course(it)) },
                onOpenResults = { nav.navigate(Routes.RESULTS) },
                onOpenImport = { nav.navigate(Routes.IMPORT) },
                onSwitchProfile = { nav.switchProfile(container) },
            )
        }
        composable(Routes.COURSE) { entry ->
            val courseId = entry.arguments?.getString("courseId").orEmpty()
            CourseScreen(
                viewModel = viewModel { CourseViewModel(container.courses, container.progress, profileId(), courseId) },
                onBack = { nav.popBackStack() },
                onOpenLesson = { nav.navigate(Routes.lesson(courseId, it)) },
            )
        }
        composable(Routes.LESSON) { entry ->
            val courseId = entry.arguments?.getString("courseId").orEmpty()
            val lessonId = entry.arguments?.getString("lessonId").orEmpty()
            LessonScreen(
                viewModel = viewModel {
                    LessonViewModel(
                        courses = container.courses,
                        progress = container.progress,
                        completeLesson = container.completeLesson,
                        submitQuizUseCase = container.submitQuiz,
                        profileId = profileId(),
                        courseId = courseId,
                        lessonId = lessonId,
                    )
                },
                onBack = { nav.popBackStack() },
                // Replace the current lesson so "back" returns to the course outline.
                onOpenLesson = { next -> nav.navigate(Routes.lesson(courseId, next)) { popUpTo(Routes.COURSE) } },
            )
        }
        composable(Routes.RESULTS) {
            ResultsScreen(
                viewModel = viewModel { ResultsViewModel(container.courses, container.profiles, container.progress, profileId()) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.IMPORT) {
            val vm = viewModel { ImportViewModel(container.courses, container.packSourceProvider) }
            pendingPack?.let { pack ->
                LaunchedEffect(pack) {
                    vm.importFile(Uri.parse(pack))
                    pendingPack = null
                }
            }
            ImportScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
    }

    if (sharedPack != null) {
        LaunchedEffect(sharedPack) { nav.navigate(Routes.IMPORT) }
    }
}

private fun NavHostController.switchProfile(container: AppContainer) {
    container.session.currentProfileId = null
    navigate(Routes.PROFILES) { popUpTo(graph.id) { inclusive = true } }
}
