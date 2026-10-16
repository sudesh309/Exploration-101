package com.kidlearn.domain.repository

import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.QuizAttempt
import com.kidlearn.domain.model.SourceSyncResult
import com.kidlearn.domain.model.SyncSettings
import com.kidlearn.domain.model.UploadResult
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun observeCourses(): Flow<List<CourseSummary>>

    /** Loads the full course structure, or null if it is not installed. */
    suspend fun getCourse(courseId: String): Course?

    /** Installs a new course or updates an existing one if the pack has a newer version. */
    suspend fun install(source: CoursePackSource, allowSameVersion: Boolean = false): InstallOutcome

    /** Installs every course in a bundle (one or many course folders / packs in one zip). */
    suspend fun installBundle(source: CoursePackSource, subPath: String = ""): List<InstallOutcome>

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

interface QuestionRepository {
    fun observeQuestions(): Flow<List<KidQuestion>>
    suspend fun add(question: KidQuestion)
    suspend fun markUploaded(ids: Set<String>, atMillis: Long)
    suspend fun delete(id: String)

    /** Writes the full question log as a JSON file ready to share, and returns it. */
    suspend fun exportForSharing(deviceId: String, deviceName: String): java.io.File
}

interface SyncSettingsRepository {
    fun observeSettings(): Flow<SyncSettings>
    suspend fun current(): SyncSettings
    suspend fun save(settings: SyncSettings)

    /** Random, stable identifier of this installation (names the uploaded question file). */
    fun deviceId(): String
}

/** Downloads course bundles from the configured online sources and installs them. */
interface ContentSync {
    /** @param force download even if a source has not changed since the last download. */
    suspend fun downloadAll(settings: SyncSettings, force: Boolean = false): List<SourceSyncResult>
}

/** Sends the question log somewhere a teacher/parent can read it. */
interface QuestionUploader {
    suspend fun upload(questions: List<KidQuestion>, settings: SyncSettings, deviceId: String): UploadResult
}
