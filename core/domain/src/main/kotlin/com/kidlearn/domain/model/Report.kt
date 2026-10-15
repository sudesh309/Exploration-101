package com.kidlearn.domain.model

import kotlin.math.roundToInt

enum class Grade(val label: String, val minPercent: Int, val stars: Int) {
    A_PLUS("A+", 90, 3),
    A("A", 80, 3),
    B("B", 70, 2),
    C("C", 60, 2),
    D("D", 40, 1),
    STARTED("Keep going!", 0, 0);

    companion object {
        fun forPercent(percent: Int): Grade = entries.first { percent >= it.minPercent }
    }
}

data class Marks(val earned: Int, val possible: Int) {
    val percent: Int get() = if (possible == 0) 0 else (earned * 100.0 / possible).roundToInt()
    val grade: Grade get() = Grade.forPercent(percent)

    operator fun plus(other: Marks) = Marks(earned + other.earned, possible + other.possible)

    companion object {
        val ZERO = Marks(0, 0)
    }
}

data class QuizScore(val lessonId: String, val quizId: String, val title: String, val best: Int?, val max: Int, val attempts: Int)

data class CourseReport(
    val course: CourseSummary,
    val lessonsCompleted: Int,
    val lessonsTotal: Int,
    val learningMarks: Marks,
    val quizMarks: Marks,
    val quizzes: List<QuizScore>,
) {
    val totalMarks: Marks get() = learningMarks + quizMarks
    val completionPercent: Int get() = if (lessonsTotal == 0) 0 else lessonsCompleted * 100 / lessonsTotal
}

data class SubjectReport(val subject: Subject, val courses: List<CourseReport>) {
    val totalMarks: Marks get() = courses.fold(Marks.ZERO) { acc, c -> acc + c.totalMarks }
}

data class ProfileReport(val profile: Profile, val subjects: List<SubjectReport>) {
    val totalMarks: Marks get() = subjects.fold(Marks.ZERO) { acc, s -> acc + s.totalMarks }
    val lessonsCompleted: Int get() = subjects.sumOf { s -> s.courses.sumOf { it.lessonsCompleted } }
}
