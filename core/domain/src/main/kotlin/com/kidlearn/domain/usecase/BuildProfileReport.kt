package com.kidlearn.domain.usecase

import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CourseReport
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.Marks
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.ProfileReport
import com.kidlearn.domain.model.QuizAttempt
import com.kidlearn.domain.model.QuizScore
import com.kidlearn.domain.model.SubjectReport

/**
 * Turns raw progress records into marks. Pure and synchronous so it is trivial to test.
 *
 * Marks for a course = completion points of finished lessons + best score of every quiz.
 * Records for lessons/quizzes no longer present in an updated course are ignored.
 */
class BuildProfileReport {
    operator fun invoke(
        profile: Profile,
        courses: List<Course>,
        lessonProgress: List<LessonProgress>,
        quizAttempts: List<QuizAttempt>,
    ): ProfileReport {
        val progressByCourse = lessonProgress.groupBy { it.courseId }
        val attemptsByCourse = quizAttempts.groupBy { it.courseId }
        val subjects = courses
            .map { courseReport(it, progressByCourse[it.id].orEmpty(), attemptsByCourse[it.id].orEmpty()) }
            .groupBy { it.course.subject.id }
            .map { (_, reports) -> SubjectReport(reports.first().course.subject, reports.sortedBy { it.course.title }) }
            .sortedBy { it.subject.name }
        return ProfileReport(profile, subjects)
    }

    fun courseReport(course: Course, progress: List<LessonProgress>, attempts: List<QuizAttempt>): CourseReport {
        val completed = progress.associateBy { it.lessonId }
        var learning = Marks.ZERO
        var quizMarks = Marks.ZERO
        val quizScores = ArrayList<QuizScore>()
        val attemptsByQuiz = attempts.groupBy { it.lessonId to it.quizId }

        for (lesson in course.lessons) {
            val earned = if (lesson.id in completed) lesson.completionPoints else 0
            learning += Marks(earned, lesson.completionPoints)
            for (quiz in lesson.quizzes) {
                val tries = attemptsByQuiz[lesson.id to quiz.id].orEmpty()
                // Rescale in case the quiz changed size after the attempt was recorded.
                val best = tries.maxOfOrNull { rescale(it.score, it.maxScore, quiz.maxScore) }
                quizMarks += Marks(best ?: 0, quiz.maxScore)
                quizScores += QuizScore(lesson.id, quiz.id, quiz.title ?: lesson.title, best, quiz.maxScore, tries.size)
            }
        }
        return CourseReport(
            course = course.toSummary(),
            lessonsCompleted = course.lessons.count { it.id in completed },
            lessonsTotal = course.lessons.size,
            learningMarks = learning,
            quizMarks = quizMarks,
            quizzes = quizScores,
        )
    }

    private fun rescale(score: Int, fromMax: Int, toMax: Int): Int =
        if (fromMax == toMax || fromMax == 0) score.coerceAtMost(toMax) else (score.toLong() * toMax / fromMax).toInt()
}
