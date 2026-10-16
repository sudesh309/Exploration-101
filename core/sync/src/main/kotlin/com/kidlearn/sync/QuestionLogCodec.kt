package com.kidlearn.sync

import com.kidlearn.domain.model.KidQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The questions log file format, readable by people and by any tool:
 *
 * ```json
 * { "format": "kidlearn-questions", "version": 1, "deviceId": "...", "deviceName": "Class 3 tablet",
 *   "exportedAt": "2026-10-16T09:30:00Z",
 *   "questions": [ { "id": "...", "learner": "Ada", "age": 8, "course": "Counting Fun",
 *                    "lesson": "Count the apples", "elementIndex": 2, "elementType": "image",
 *                    "element": "How many apples?", "question": "Why are apples red?",
 *                    "askedAt": "2026-10-16T09:12:44Z", ... } ] }
 * ```
 */
object QuestionLogCodec {
    const val FORMAT = "kidlearn-questions"
    private val json = Json { prettyPrint = true }

    fun encode(questions: List<KidQuestion>, deviceId: String, deviceName: String, exportedAtMillis: Long): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("format", FORMAT)
                put("version", 1)
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("exportedAt", isoTime(exportedAtMillis))
                put("questions", buildJsonArray { questions.sortedBy { it.askedAtMillis }.forEach { add(encodeQuestion(it)) } })
            },
        )

    fun decode(text: String): List<KidQuestion> {
        if (text.isBlank()) return emptyList()
        val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val items = root["questions"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.let(::decodeQuestion) }
    }

    private fun encodeQuestion(q: KidQuestion) = buildJsonObject {
        put("id", q.id)
        put("learner", q.profileName)
        put("learnerId", q.profileId)
        put("age", q.profileAge)
        put("courseId", q.courseId)
        put("course", q.courseTitle)
        put("lessonId", q.lessonId)
        put("lesson", q.lessonTitle)
        put("elementIndex", q.blockIndex)
        put("elementType", q.blockType)
        q.blockPreview?.let { put("element", it) }
        put("question", q.text)
        put("askedAt", isoTime(q.askedAtMillis))
        put("askedAtMillis", q.askedAtMillis)
        q.uploadedAtMillis?.let { put("uploadedAtMillis", it) }
    }

    private fun decodeQuestion(o: JsonObject): KidQuestion? {
        fun str(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        fun long(key: String) = (o[key] as? JsonPrimitive)?.longOrNull
        fun int(key: String) = (o[key] as? JsonPrimitive)?.intOrNull
        return KidQuestion(
            id = str("id") ?: return null,
            profileId = long("learnerId") ?: 0,
            profileName = str("learner").orEmpty(),
            profileAge = int("age") ?: 0,
            courseId = str("courseId").orEmpty(),
            courseTitle = str("course").orEmpty(),
            lessonId = str("lessonId").orEmpty(),
            lessonTitle = str("lesson").orEmpty(),
            blockIndex = int("elementIndex") ?: 0,
            blockType = str("elementType").orEmpty(),
            blockPreview = str("element"),
            text = str("question") ?: return null,
            askedAtMillis = long("askedAtMillis") ?: 0,
            uploadedAtMillis = long("uploadedAtMillis"),
        )
    }

    /** ISO-8601 UTC without java.time (unavailable on Android 7 without desugaring). */
    private fun isoTime(millis: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(millis))
    }
}
