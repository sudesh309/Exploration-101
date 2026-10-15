package com.kidlearn.domain.model

data class Profile(
    val id: Long,
    val name: String,
    val age: Int,
    val avatar: String,
    val createdAtMillis: Long,
)

data class LessonProgress(
    val profileId: Long,
    val courseId: String,
    val lessonId: String,
    val pointsEarned: Int,
    val completedAtMillis: Long,
)

data class QuizAttempt(
    val profileId: Long,
    val courseId: String,
    val lessonId: String,
    val quizId: String,
    val score: Int,
    val maxScore: Int,
    val takenAtMillis: Long,
)
