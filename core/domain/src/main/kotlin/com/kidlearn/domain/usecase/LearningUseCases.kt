package com.kidlearn.domain.usecase

import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.QuestionResponse
import com.kidlearn.domain.model.Quiz
import com.kidlearn.domain.model.QuizAttempt
import com.kidlearn.domain.model.QuizResult
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.ProgressRepository

class CompleteLesson(
    private val progress: ProgressRepository,
    private val clock: Clock = Clock.SYSTEM,
) {
    suspend operator fun invoke(profileId: Long, courseId: String, lesson: Lesson) {
        progress.saveLessonProgress(
            LessonProgress(
                profileId = profileId,
                courseId = courseId,
                lessonId = lesson.id,
                pointsEarned = lesson.completionPoints,
                completedAtMillis = clock.nowMillis(),
            ),
        )
    }
}

class SubmitQuiz(
    private val progress: ProgressRepository,
    private val clock: Clock = Clock.SYSTEM,
) {
    suspend operator fun invoke(
        profileId: Long,
        courseId: String,
        lessonId: String,
        quiz: Quiz,
        responses: Map<String, QuestionResponse>,
    ): QuizResult {
        val result = quiz.grade(responses)
        progress.saveQuizAttempt(
            QuizAttempt(
                profileId = profileId,
                courseId = courseId,
                lessonId = lessonId,
                quizId = quiz.id,
                score = result.score,
                maxScore = result.maxScore,
                takenAtMillis = clock.nowMillis(),
            ),
        )
        return result
    }
}

/** Courses suitable for a learner's age, best matches first. */
class CoursesForLearner {
    operator fun invoke(courses: List<CourseSummary>, profile: Profile?): List<CourseSummary> {
        if (profile == null) return courses
        return courses
            .filter { profile.age in it.ageRange }
            .sortedWith(compareBy({ it.subject.name }, { it.ageRange.min }, { it.title }))
    }
}

/** Groups a course's lessons with the learner's completion flags, for course outline screens. */
fun Course.completedLessonIds(progress: List<LessonProgress>): Set<String> {
    val ids = lessons.mapTo(HashSet()) { it.id }
    return progress.asSequence().filter { it.courseId == id && it.lessonId in ids }.mapTo(HashSet()) { it.lessonId }
}
