package com.kidlearn.data.repository

import com.kidlearn.data.db.LessonProgressEntity
import com.kidlearn.data.db.ProfileDao
import com.kidlearn.data.db.ProfileEntity
import com.kidlearn.data.db.ProgressDao
import com.kidlearn.data.db.QuizAttemptEntity
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.QuizAttempt
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.ProfileRepository
import com.kidlearn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomProfileRepository(
    private val dao: ProfileDao,
    private val clock: Clock,
) : ProfileRepository {
    override fun observeProfiles(): Flow<List<Profile>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfile(profileId: Long): Profile? = dao.get(profileId)?.toDomain()

    override suspend fun createProfile(name: String, age: Int, avatar: String): Profile {
        val entity = ProfileEntity(name = name.trim(), age = age, avatar = avatar, createdAt = clock.nowMillis())
        return entity.copy(id = dao.insert(entity)).toDomain()
    }

    override suspend fun updateProfile(profile: Profile) {
        dao.update(ProfileEntity(profile.id, profile.name.trim(), profile.age, profile.avatar, profile.createdAtMillis))
    }

    override suspend fun deleteProfile(profileId: Long) = dao.delete(profileId)

    private fun ProfileEntity.toDomain() = Profile(id, name, age, avatar, createdAt)
}

internal class RoomProgressRepository(private val dao: ProgressDao) : ProgressRepository {
    override fun observeLessonProgress(profileId: Long): Flow<List<LessonProgress>> =
        dao.observeLessons(profileId).map { list ->
            list.map { LessonProgress(it.profileId, it.courseId, it.lessonId, it.pointsEarned, it.completedAt) }
        }

    override fun observeQuizAttempts(profileId: Long): Flow<List<QuizAttempt>> =
        dao.observeQuizAttempts(profileId).map { list ->
            list.map { QuizAttempt(it.profileId, it.courseId, it.lessonId, it.quizId, it.score, it.maxScore, it.takenAt) }
        }

    override suspend fun saveLessonProgress(progress: LessonProgress) = dao.upsertLesson(
        LessonProgressEntity(progress.profileId, progress.courseId, progress.lessonId, progress.pointsEarned, progress.completedAtMillis),
    )

    override suspend fun saveQuizAttempt(attempt: QuizAttempt) = dao.insertQuizAttempt(
        QuizAttemptEntity(
            profileId = attempt.profileId,
            courseId = attempt.courseId,
            lessonId = attempt.lessonId,
            quizId = attempt.quizId,
            score = attempt.score,
            maxScore = attempt.maxScore,
            takenAt = attempt.takenAtMillis,
        ),
    )
}
