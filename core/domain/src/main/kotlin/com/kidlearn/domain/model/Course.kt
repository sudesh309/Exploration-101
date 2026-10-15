package com.kidlearn.domain.model

/**
 * A subject such as Maths or Robotics. Subjects are not hard-coded: any course pack can
 * declare a new one, so new subjects appear simply by importing content.
 */
data class Subject(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val icon: MediaRef? = null,
)

data class AgeRange(val min: Int, val max: Int) {
    init {
        require(min <= max) { "AgeRange min ($min) must be <= max ($max)" }
    }

    operator fun contains(age: Int): Boolean = age in min..max

    companion object {
        val ALL = AgeRange(5, 15)
    }
}

/**
 * Location of a piece of media. After installation this is an absolute file path (offline),
 * but a pack may also reference an http(s) URL, which is only playable while online.
 */
@JvmInline
value class MediaRef(val location: String) {
    val isRemote: Boolean get() = location.startsWith("http://") || location.startsWith("https://")
}

/** Lightweight description of an installed course, cheap enough to list many at once. */
data class CourseSummary(
    val id: String,
    val version: Int,
    val title: String,
    val description: String,
    val subject: Subject,
    val ageRange: AgeRange,
    val coverImage: MediaRef?,
    val lessonCount: Int,
)

data class Course(
    val id: String,
    val version: Int,
    val title: String,
    val description: String,
    val subject: Subject,
    val ageRange: AgeRange,
    val coverImage: MediaRef?,
    val units: List<CourseUnit>,
) {
    val lessons: List<Lesson> by lazy { units.flatMap { it.lessons } }

    fun lesson(lessonId: String): Lesson? = lessons.firstOrNull { it.id == lessonId }

    fun toSummary() = CourseSummary(
        id = id,
        version = version,
        title = title,
        description = description,
        subject = subject,
        ageRange = ageRange,
        coverImage = coverImage,
        lessonCount = lessons.size,
    )
}

/** A chapter grouping related lessons. */
data class CourseUnit(
    val id: String,
    val title: String,
    val lessons: List<Lesson>,
)

/**
 * A lesson is an ordered list of content blocks. Its learning mode (visual, audio, video, quiz,
 * or a mix) is defined purely by the blocks it contains.
 */
data class Lesson(
    val id: String,
    val title: String,
    val summary: String?,
    val completionPoints: Int,
    val blocks: List<ContentBlock>,
) {
    val quizzes: List<Quiz> get() = blocks.filterIsInstance<QuizBlock>().map { it.quiz }
}
