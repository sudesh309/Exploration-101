package com.kidlearn.content.parse

import com.kidlearn.domain.model.AgeRange
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CourseUnit
import com.kidlearn.domain.model.Lesson
import com.kidlearn.domain.model.Subject
import com.kidlearn.domain.model.UnsupportedBlock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface ManifestResult {
    data class Success(val course: Course, val warnings: List<String>) : ManifestResult
    data class Failure(val errors: List<String>) : ManifestResult
}

/**
 * Parses `course.json` into a [Course]. See docs/CONTENT_FORMAT.md for the schema.
 *
 * Parsing is forgiving where it can be: an invalid lesson block or question is skipped with a
 * warning instead of rejecting the whole course, so a single typo never blocks a classroom.
 */
class ManifestParser(private val blocks: BlockParserRegistry = BlockParserRegistry.default()) {

    fun parse(manifestJson: String, media: MediaResolver): ManifestResult {
        val root = try {
            json.parseToJsonElement(manifestJson) as? JsonObject
                ?: return ManifestResult.Failure(listOf("course.json must contain a JSON object"))
        } catch (e: SerializationException) {
            return ManifestResult.Failure(listOf("course.json is not valid JSON: ${e.message}"))
        }
        val context = ParseContext(media)
        return try {
            val course = parseCourse(root, context)
            ManifestResult.Success(course, context.warnings)
        } catch (e: ManifestException) {
            ManifestResult.Failure(listOf(e.message.orEmpty()) + context.warnings)
        }
    }

    private fun parseCourse(root: JsonObject, ctx: ParseContext): Course = with(ctx) {
        val schema = root.optInt("schemaVersion") ?: 1
        if (schema > SUPPORTED_SCHEMA) {
            throw ManifestException("course uses schemaVersion $schema; this app supports up to $SUPPORTED_SCHEMA. Please update the app.")
        }
        val id = root.string("id", "course").also(::requireSafeId)
        val subjectJson = (root["subject"] ?: throw ManifestException("course: missing 'subject'")).asObject("subject")
        val subject = Subject(
            id = subjectJson.string("id", "subject").also(::requireSafeId),
            name = subjectJson.string("name", "subject"),
            colorHex = subjectJson.optString("color"),
            icon = subjectJson.optMedia("icon", "subject"),
        )
        val ageJson = root["ageRange"] as? JsonObject
        val ageRange = try {
            AgeRange(ageJson?.optInt("min") ?: AgeRange.ALL.min, ageJson?.optInt("max") ?: AgeRange.ALL.max)
        } catch (e: IllegalArgumentException) {
            throw ManifestException("ageRange: ${e.message}")
        }
        val units = root.array("units", "course").mapIndexed { i, el -> parseUnit(el.asObject("units[$i]"), "units[$i]", ctx) }
        val lessonIds = units.flatMap { u -> u.lessons.map { it.id } }
        if (lessonIds.isEmpty()) throw ManifestException("course has no lessons")
        val dupes = lessonIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (dupes.isNotEmpty()) throw ManifestException("duplicate lesson ids: $dupes")

        Course(
            id = id,
            version = (root.optInt("version") ?: 1).coerceAtLeast(1),
            title = root.string("title", "course"),
            description = root.optString("description").orEmpty(),
            subject = subject,
            ageRange = ageRange,
            coverImage = root.optMedia("cover", "course"),
            units = units,
        )
    }

    private fun parseUnit(json: JsonObject, where: String, ctx: ParseContext): CourseUnit = with(ctx) {
        CourseUnit(
            id = json.optString("id") ?: where,
            title = json.string("title", where),
            lessons = json.array("lessons", where).mapIndexed { i, el ->
                parseLesson(el.asObject("$where.lessons[$i]"), "$where.lessons[$i]", ctx)
            },
        )
    }

    private fun parseLesson(json: JsonObject, where: String, ctx: ParseContext): Lesson = with(ctx) {
        val lessonId = json.string("id", where).also(::requireSafeId)
        val lessonWhere = "lesson '$lessonId'"
        val blocks = json.array("blocks", lessonWhere).mapIndexedNotNull { i, el ->
            parseBlock(el.asObject("$lessonWhere.blocks[$i]"), "$lessonWhere.blocks[$i]", ctx)
        }
        if (blocks.isEmpty()) throw ManifestException("$lessonWhere has no usable content")
        Lesson(
            id = lessonId,
            title = json.string("title", lessonWhere),
            summary = json.optString("summary"),
            completionPoints = (json.optInt("points") ?: DEFAULT_LESSON_POINTS).coerceAtLeast(0),
            blocks = blocks,
        )
    }

    private fun parseBlock(json: JsonObject, where: String, ctx: ParseContext): ContentBlock? = with(ctx) {
        val type = json.optString("type") ?: run {
            warn("$where: missing 'type' (block skipped)")
            return null
        }
        val parser = blocks.find(type) ?: run {
            warn("$where: unsupported block type '$type'")
            return UnsupportedBlock(type)
        }
        try {
            parser.parse(json, ctx, where)
        } catch (e: ManifestException) {
            warn("${e.message} (block skipped)")
            null
        }
    }

    private fun requireSafeId(id: String) {
        if (!SAFE_ID.matches(id)) {
            throw ManifestException("id '$id' may only use letters, digits, '-' and '_' (max 64 chars)")
        }
    }

    companion object {
        const val SUPPORTED_SCHEMA = 1
        const val DEFAULT_LESSON_POINTS = 10
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
