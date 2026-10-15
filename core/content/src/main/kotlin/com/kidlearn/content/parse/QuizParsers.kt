package com.kidlearn.content.parse

import com.kidlearn.domain.model.ChoiceQuestion
import com.kidlearn.domain.model.NumberQuestion
import com.kidlearn.domain.model.Question
import com.kidlearn.domain.model.Quiz
import com.kidlearn.domain.model.TextQuestion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Parses one `type` of quiz question. */
interface QuestionParser {
    val types: Set<String>
    fun parse(json: JsonObject, common: QuestionCommon, context: ParseContext, where: String): Question
}

/** Fields every question shares, pre-parsed so individual parsers stay small. */
data class QuestionCommon(
    val id: String,
    val prompt: String,
    val image: com.kidlearn.domain.model.MediaRef?,
    val points: Int,
    val explanation: String?,
)

object ChoiceQuestionParser : QuestionParser {
    override val types = setOf("single_choice", "multiple_choice", "true_false")

    override fun parse(json: JsonObject, common: QuestionCommon, context: ParseContext, where: String): Question = with(context) {
        val type = json.string("type", where)
        if (type == "true_false") {
            val answer = (json["answer"] as? JsonPrimitive)?.booleanOrNull
                ?: throw ManifestException("$where: true_false 'answer' must be true or false")
            return@with ChoiceQuestion(
                common.id, common.prompt, common.image, common.points, common.explanation,
                options = listOf("True", "False"),
                correctOptions = setOf(if (answer) 0 else 1),
                multiSelect = false,
            )
        }
        val options = json.array("options", where).map { it.jsonPrimitive.content }
        val answers = when (val raw = json["answer"]) {
            is JsonPrimitive -> listOfNotNull(raw.intOrNull)
            is kotlinx.serialization.json.JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            else -> emptyList()
        }
        if (answers.isEmpty()) throw ManifestException("$where: 'answer' must be an option index or list of indexes")
        try {
            ChoiceQuestion(
                common.id, common.prompt, common.image, common.points, common.explanation,
                options = options,
                correctOptions = answers.toSet(),
                multiSelect = type == "multiple_choice",
            )
        } catch (e: IllegalArgumentException) {
            throw ManifestException("$where: ${e.message}")
        }
    }
}

object NumberQuestionParser : QuestionParser {
    override val types = setOf("number")

    override fun parse(json: JsonObject, common: QuestionCommon, context: ParseContext, where: String): Question = with(context) {
        NumberQuestion(
            common.id, common.prompt, common.image, common.points, common.explanation,
            answer = json.optDouble("answer") ?: throw ManifestException("$where: numeric 'answer' required"),
            tolerance = json.optDouble("tolerance") ?: 0.0,
        )
    }
}

object TextQuestionParser : QuestionParser {
    override val types = setOf("text")

    override fun parse(json: JsonObject, common: QuestionCommon, context: ParseContext, where: String): Question = with(context) {
        val accepted = json.optArray("answers")?.map { it.jsonPrimitive.content }
            ?: listOfNotNull(json.optString("answer"))
        if (accepted.isEmpty()) throw ManifestException("$where: 'answer' or 'answers' required")
        TextQuestion(common.id, common.prompt, common.image, common.points, common.explanation, accepted)
    }
}

class QuestionParserRegistry(parsers: Iterable<QuestionParser>) {
    private val byType: Map<String, QuestionParser> = parsers.flatMap { p -> p.types.map { it to p } }.toMap()

    fun find(type: String): QuestionParser? = byType[type]

    companion object {
        fun default() = QuestionParserRegistry(listOf(ChoiceQuestionParser, NumberQuestionParser, TextQuestionParser))
    }
}

internal class QuizParser(private val registry: QuestionParserRegistry) {
    fun parse(json: JsonObject, context: ParseContext, where: String): Quiz = with(context) {
        val quizId = json.string("id", where)
        val questions = json.array("questions", where).mapIndexedNotNull { index, element ->
            val qWhere = "$where.questions[$index]"
            try {
                val obj = element.asObject(qWhere)
                val type = obj.string("type", qWhere)
                val parser = registry.find(type) ?: throw ManifestException("$qWhere: unknown question type '$type'")
                val common = QuestionCommon(
                    id = obj.optString("id") ?: "q${index + 1}",
                    prompt = obj.string("prompt", qWhere),
                    image = obj.optMedia("image", qWhere),
                    points = (obj.optInt("points") ?: 1).coerceAtLeast(0),
                    explanation = obj.optString("explanation"),
                )
                parser.parse(obj, common, context, qWhere)
            } catch (e: ManifestException) {
                warn("${e.message} (question skipped)")
                null
            }
        }
        if (questions.isEmpty()) throw ManifestException("$where: quiz '$quizId' has no valid questions")
        val duplicate = questions.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicate.isNotEmpty()) throw ManifestException("$where: duplicate question ids $duplicate")
        Quiz(
            id = quizId,
            title = json.optString("title"),
            passPercent = (json.optInt("passPercent") ?: 60).coerceIn(0, 100),
            questions = questions,
        )
    }
}
