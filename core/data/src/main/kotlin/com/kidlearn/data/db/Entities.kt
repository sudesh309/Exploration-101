package com.kidlearn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int,
    val avatar: String,
    val createdAt: Long,
)

/** One row per completed lesson; re-completing a lesson just refreshes the row. */
@Entity(
    tableName = "lesson_progress",
    primaryKeys = ["profileId", "courseId", "lessonId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class LessonProgressEntity(
    val profileId: Long,
    val courseId: String,
    val lessonId: String,
    val pointsEarned: Int,
    val completedAt: Long,
)

/** Every quiz attempt is kept, so learners and parents can see improvement over time. */
@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "courseId")],
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val courseId: String,
    val lessonId: String,
    val quizId: String,
    val score: Int,
    val maxScore: Int,
    val takenAt: Long,
)
