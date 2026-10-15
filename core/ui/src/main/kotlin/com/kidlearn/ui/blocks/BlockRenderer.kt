package com.kidlearn.ui.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.QuestionResponse
import com.kidlearn.domain.model.Quiz
import com.kidlearn.domain.model.QuizResult

/**
 * Draws one type of [ContentBlock]. Feature modules contribute renderers (text/image in
 * :feature:lesson, audio/video in :feature:media, quizzes in :feature:quiz) and the app
 * registers them, so the lesson player never needs to know which kinds of content exist.
 */
interface BlockRenderer {
    /** Matches [ContentBlock.type]. */
    val type: String

    @Composable
    fun Render(block: ContentBlock, session: LessonSession)
}

/** What a block may need from the lesson it is shown in. */
interface LessonSession {
    val profileId: Long
    val courseId: String
    val lessonId: String

    /** Grades and records a quiz attempt for the current learner. */
    suspend fun submitQuiz(quiz: Quiz, responses: Map<String, QuestionResponse>): QuizResult
}

class BlockRendererRegistry(renderers: Iterable<BlockRenderer>) {
    private val byType = renderers.associateBy { it.type }

    fun find(type: String): BlockRenderer? = byType[type]
}

val LocalBlockRenderers = staticCompositionLocalOf { BlockRendererRegistry(emptyList()) }
