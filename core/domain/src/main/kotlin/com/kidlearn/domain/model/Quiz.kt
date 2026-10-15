package com.kidlearn.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

data class Quiz(
    val id: String,
    val title: String?,
    val passPercent: Int,
    val questions: List<Question>,
) {
    val maxScore: Int get() = questions.sumOf { it.points }

    fun grade(responses: Map<String, QuestionResponse>): QuizResult {
        val correctness = questions.associate { q ->
            q.id to (responses[q.id]?.let(q::isCorrect) ?: false)
        }
        val score = questions.filter { correctness[it.id] == true }.sumOf { it.points }
        return QuizResult(quizId = id, score = score, maxScore = maxScore, passPercent = passPercent, correctness = correctness)
    }
}

data class QuizResult(
    val quizId: String,
    val score: Int,
    val maxScore: Int,
    val passPercent: Int,
    val correctness: Map<String, Boolean>,
) {
    val percent: Int get() = if (maxScore == 0) 100 else (score * 100.0 / maxScore).roundToInt()
    val passed: Boolean get() = percent >= passPercent
}

/** What the learner answered. Open so new question types can bring their own response shape. */
interface QuestionResponse

data class ChoiceResponse(val selected: Set<Int>) : QuestionResponse

data class TextResponse(val value: String) : QuestionResponse

/** A gradable question. New question types implement this interface. */
interface Question {
    val id: String
    val prompt: String
    val image: MediaRef?
    val points: Int
    val explanation: String?

    fun isCorrect(response: QuestionResponse): Boolean
}

/** Single- or multiple-answer question. True/false questions are choice questions too. */
data class ChoiceQuestion(
    override val id: String,
    override val prompt: String,
    override val image: MediaRef?,
    override val points: Int,
    override val explanation: String?,
    val options: List<String>,
    val correctOptions: Set<Int>,
    val multiSelect: Boolean,
) : Question {
    init {
        require(options.size >= 2) { "Question '$id' needs at least two options" }
        require(correctOptions.isNotEmpty()) { "Question '$id' needs at least one correct option" }
        require(correctOptions.all { it in options.indices }) { "Question '$id' has an answer index out of range" }
        require(multiSelect || correctOptions.size == 1) { "Single-choice question '$id' must have exactly one answer" }
    }

    override fun isCorrect(response: QuestionResponse): Boolean =
        response is ChoiceResponse && response.selected == correctOptions
}

/** Free numeric answer, e.g. "3 + 4 = ?". */
data class NumberQuestion(
    override val id: String,
    override val prompt: String,
    override val image: MediaRef?,
    override val points: Int,
    override val explanation: String?,
    val answer: Double,
    val tolerance: Double = 0.0,
) : Question {
    override fun isCorrect(response: QuestionResponse): Boolean {
        val value = (response as? TextResponse)?.value?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return false
        return abs(value - answer) <= tolerance + EPSILON
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/** Short typed answer, compared ignoring case and surrounding whitespace. */
data class TextQuestion(
    override val id: String,
    override val prompt: String,
    override val image: MediaRef?,
    override val points: Int,
    override val explanation: String?,
    val acceptedAnswers: List<String>,
) : Question {
    override fun isCorrect(response: QuestionResponse): Boolean {
        val given = (response as? TextResponse)?.value?.normalized() ?: return false
        return acceptedAnswers.any { it.normalized() == given }
    }

    private fun String.normalized() = trim().lowercase().replace(Regex("\\s+"), " ")
}
