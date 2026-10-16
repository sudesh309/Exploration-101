package com.kidlearn.domain

import com.kidlearn.domain.model.AgeRange
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CourseUnit
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.Subject
import com.kidlearn.domain.model.TextBlock
import com.kidlearn.domain.repository.QuestionRepository
import com.kidlearn.domain.usecase.AskQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class AskQuestionTest {
    private class MemoryQuestions : QuestionRepository {
        val saved = mutableListOf<KidQuestion>()
        override fun observeQuestions(): Flow<List<KidQuestion>> = flowOf(saved)
        override suspend fun add(question: KidQuestion) { saved += question }
        override suspend fun markUploaded(ids: Set<String>, atMillis: Long) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun exportForSharing(deviceId: String, deviceName: String): File = error("unused")
    }

    private val lesson = Lesson("l1", "Plants", null, 10, listOf(TextBlock("x".repeat(200))))
    private val course = Course("c", 1, "Science", "", Subject("s", "S"), AgeRange.ALL, null, listOf(CourseUnit("u", "U", listOf(lesson))))
    private val profile = Profile(7, "Ada", 9, "🦊", 0)

    @Test
    fun `records which element the question is about`() = runTest {
        val repo = MemoryQuestions()
        val q = AskQuestion(repo, clock = { 42L }, newId = { "q1" })(profile, course, lesson, 0, "  Why do plants need light?  ")!!
        assertEquals("Why do plants need light?", q.text)
        assertEquals("text", q.blockType)
        assertEquals(80, q.blockPreview!!.length)
        assertEquals("Ada", q.profileName)
        assertEquals(42L, q.askedAtMillis)
        assertEquals(listOf(q), repo.saved)
    }

    @Test
    fun `blank questions are ignored`() = runTest {
        val repo = MemoryQuestions()
        assertNull(AskQuestion(repo)(profile, course, lesson, 0, "   "))
        assertEquals(0, repo.saved.size)
    }
}
