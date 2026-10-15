package com.kidlearn.domain.repository

import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.QuizAttempt
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun observeCourses(): Flow<List<CourseSummary>>

    /** Loads the full course structure, or null if it is not installed. */
    suspend fun getCourse(courseId: String): Course?

    /** Installs a new course or updates an existing one if the pack has a newer version. */
    suspend fun install(source: CoursePackSource, allowSameVersion: Boolean = false): InstallOutcome

    suspend fun remove(courseId: String)
}

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun getProfile(profileId: Long): Profile?
    suspend fun createProfile(name: String, age: Int, avatar: String): Profile
    suspend fun updateProfile(profile: Profile)
    suspend fun deleteProfile(profileId: Long)
}

interface ProgressRepository {
    fun observeLessonProgress(profileId: Long): Flow<List<LessonProgress>>
    fun observeQuizAttempts(profileId: Long): Flow<List<QuizAttempt>>
    suspend fun saveLessonProgress(progress: LessonProgress)
    suspend fun saveQuizAttempt(attempt: QuizAttempt)
}

/** Abstracted so use cases stay deterministic under test. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
