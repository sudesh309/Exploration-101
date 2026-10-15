package com.kidlearn.domain.model

import java.io.InputStream

/** Where a course pack comes from: a file picked from storage, a URL download, a bundled asset... */
class CoursePackSource(
    val displayName: String,
    val format: Format,
    val openStream: () -> InputStream,
) {
    enum class Format {
        /** A .zip holding `course.json` plus media files. */
        ZIP,

        /** A bare `course.json` (text/quiz-only, or media referenced by URL). */
        MANIFEST,
    }
}

sealed interface InstallOutcome {
    data class Installed(val course: CourseSummary) : InstallOutcome
    data class Updated(val course: CourseSummary, val previousVersion: Int) : InstallOutcome
    data class AlreadyUpToDate(val course: CourseSummary) : InstallOutcome
    data class Rejected(val problems: List<String>) : InstallOutcome
}
