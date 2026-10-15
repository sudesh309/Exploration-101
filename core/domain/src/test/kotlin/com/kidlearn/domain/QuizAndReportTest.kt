package com.kidlearn.domain

import com.kidlearn.domain.model.AgeRange
import com.kidlearn.domain.model.ChoiceQuestion
import com.kidlearn.domain.model.ChoiceResponse
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CourseUnit
import com.kidlearn.domain.model.Grade
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.LessonProgress
import com.kidlearn.domain.model.NumberQuestion
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.Quiz
import com.kidlearn.domain.model.QuizAttempt
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.Subject
import com.kidlearn.domain.model.TextBlock
import com.kidlearn.domain.model.TextQuestion
import com.kidlearn.domain.model.TextResponse
import com.kidlearn.domain.usecase.BuildProfileReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAndReportTest {
    private val quiz = Quiz(
        id = "quiz1",
        title = "Numbers",
        passPercent = 60,
        questions = listOf(
            ChoiceQuestion("a", "2+2?", null, 1, null, listOf("3", "4"), setOf(1), multiSelect = false),
            ChoiceQuestion("b", "Even?", null, 2, null, listOf("2", "3", "4"), setOf(0, 2), multiSelect = true),
            NumberQuestion("c", "Half of 5?", null, 1, null, answer = 2.5),
            TextQuestion("d", "Red planet?", null, 1, null, listOf("Mars")),
        ),
    )

    @Test
    fun `grades every question type`() {
        val result = quiz.grade(
            mapOf(
                "a" to ChoiceResponse(setOf(1)),
                "b" to ChoiceResponse(setOf(0)), // partially right counts as wrong
                "c" to TextResponse(" 2,5 "),
                "d" to TextResponse("  mars "),
            ),
        )
        assertEquals(3, result.score)
        assertEquals(5, result.maxScore)
        assertEquals(60, result.percent)
        assertTrue(result.passed)
        assertFalse(result.correctness.getValue("b"))
    }

    @Test
    fun `unanswered questions score zero`() {
        assertEquals(0, quiz.grade(emptyMap()).score)
    }

    @Test
    fun `report combines lesson points with best quiz attempt`() {
        val course = Course(
            id = "maths", version = 1, title = "Maths", description = "",
            subject = Subject("maths", "Maths"), ageRange = AgeRange.ALL, coverImage = null,
            units = listOf(
                CourseUnit(
                    "u1", "Unit",
                    listOf(
                        Lesson("l1", "Intro", null, 10, listOf(TextBlock("hi"))),
                        Lesson("l2", "Test", null, 10, listOf(QuizBlock(quiz))),
                    ),
                ),
            ),
        )
        val profile = Profile(1, "Ada", 8, "cat", 0)
        val report = BuildProfileReport()(
            profile,
            listOf(course),
            listOf(
                LessonProgress(1, "maths", "l1", 10, 0),
                LessonProgress(1, "maths", "removed-lesson", 10, 0),
            ),
            listOf(
                QuizAttempt(1, "maths", "l2", "quiz1", 2, 5, 0),
                QuizAttempt(1, "maths", "l2", "quiz1", 4, 5, 1),
            ),
        )
        val courseReport = report.subjects.single().courses.single()
        assertEquals(1, courseReport.lessonsCompleted)
        assertEquals(10, courseReport.learningMarks.earned)
        assertEquals(20, courseReport.learningMarks.possible)
        assertEquals(4, courseReport.quizMarks.earned)
        assertEquals(2, courseReport.quizzes.single().attempts)
        assertEquals(14, report.totalMarks.earned)
        assertEquals(25, report.totalMarks.possible)
        assertEquals(56, report.totalMarks.percent)
        assertEquals(Grade.D, report.totalMarks.grade)
    }

    @Test
    fun `quiz without attempts has no best score`() {
        val course = Course(
            "c", 1, "C", "", Subject("s", "S"), AgeRange.ALL, null,
            listOf(CourseUnit("u", "U", listOf(Lesson("l", "L", null, 0, listOf(QuizBlock(quiz)))))),
        )
        val report = BuildProfileReport().courseReport(course, emptyList(), emptyList())
        assertNull(report.quizzes.single().best)
        assertEquals(Grade.STARTED, report.totalMarks.grade)
    }

    @Test
    fun `age range filter`() {
        assertTrue(8 in AgeRange(5, 8))
        assertFalse(9 in AgeRange(5, 8))
    }
}
