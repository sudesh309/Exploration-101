package com.kidlearn.domain.usecase

import com.kidlearn.domain.model.AudioBlock
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.ImageBlock
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.Profile
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.TextBlock
import com.kidlearn.domain.model.UploadResult
import com.kidlearn.domain.model.VideoBlock
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.QuestionRepository
import com.kidlearn.domain.repository.QuestionUploader
import com.kidlearn.domain.repository.SyncSettingsRepository
import java.util.UUID

/** Records a learner's question about one element of a lesson. */
class AskQuestion(
    private val questions: QuestionRepository,
    private val clock: Clock = Clock.SYSTEM,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** @return the saved question, or null if [text] is blank. */
    suspend operator fun invoke(
        profile: Profile,
        course: Course,
        lesson: Lesson,
        blockIndex: Int,
        text: String,
    ): KidQuestion? {
        val cleaned = text.trim().take(MAX_LENGTH)
        if (cleaned.isEmpty()) return null
        val block = lesson.blocks.getOrNull(blockIndex)
        val question = KidQuestion(
            id = newId(),
            profileId = profile.id,
            profileName = profile.name,
            profileAge = profile.age,
            courseId = course.id,
            courseTitle = course.title,
            lessonId = lesson.id,
            lessonTitle = lesson.title,
            blockIndex = blockIndex,
            blockType = block?.type ?: "lesson",
            blockPreview = block?.let(::preview),
            text = cleaned,
            askedAtMillis = clock.nowMillis(),
        )
        questions.add(question)
        return question
    }

    companion object {
        const val MAX_LENGTH = 500
        private const val PREVIEW_LENGTH = 80

        fun preview(block: ContentBlock): String? = when (block) {
            is TextBlock -> block.text
            is ImageBlock -> block.caption ?: block.altText
            is AudioBlock -> block.title
            is VideoBlock -> block.title
            is QuizBlock -> block.quiz.title ?: "Quiz"
            else -> null
        }?.let { if (it.length > PREVIEW_LENGTH) it.take(PREVIEW_LENGTH - 1) + "…" else it }
    }
}

/** Uploads all questions (the whole log, so the remote copy is always complete). */
class UploadQuestions(
    private val questions: QuestionRepository,
    private val settings: SyncSettingsRepository,
    private val uploader: QuestionUploader,
    private val clock: Clock = Clock.SYSTEM,
) {
    suspend operator fun invoke(all: List<KidQuestion>): UploadResult {
        if (all.none { !it.isUploaded }) return UploadResult.NothingToUpload
        val result = uploader.upload(all, settings.current(), settings.deviceId())
        if (result is UploadResult.Uploaded) {
            questions.markUploaded(all.filter { !it.isUploaded }.mapTo(HashSet()) { it.id }, clock.nowMillis())
        }
        return result
    }
}
