package com.kidlearn.domain.model

/**
 * A question a learner asked about one element (block) of a lesson. Questions are logged to a
 * JSON file on the device and can be shared or uploaded so a teacher or parent can answer them.
 */
data class KidQuestion(
    val id: String,
    val profileId: Long,
    val profileName: String,
    val profileAge: Int,
    val courseId: String,
    val courseTitle: String,
    val lessonId: String,
    val lessonTitle: String,
    /** Position of the element within the lesson (0-based). */
    val blockIndex: Int,
    val blockType: String,
    /** Short text identifying the element, e.g. the start of a paragraph or an image caption. */
    val blockPreview: String?,
    val text: String,
    val askedAtMillis: Long,
    val uploadedAtMillis: Long? = null,
) {
    val isUploaded: Boolean get() = uploadedAtMillis != null
}

/** A GitHub repository used to download courses and (with a token) to upload questions. */
data class GitHubSource(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    /** Folder inside the repository holding courses; empty means the whole repository. */
    val contentPath: String = "",
    /** Folder inside the repository where question logs are uploaded. */
    val questionsPath: String = "questions",
    /** Personal access token. Needed for private repositories and for uploading. */
    val token: String = "",
) {
    val isConfigured: Boolean get() = owner.isNotBlank() && repo.isNotBlank()
    val canUpload: Boolean get() = isConfigured && token.isNotBlank()
    val displayName: String get() = "GitHub $owner/$repo"
}

/**
 * A Google Drive location: a shared folder (needs an API key to list it) or a single shared
 * file (a zip bundle or pack), given as a link or an ID.
 */
data class DriveSource(
    val link: String = "",
    val apiKey: String = "",
) {
    val isConfigured: Boolean get() = link.isNotBlank()
    val displayName: String get() = "Google Drive"
}

data class SyncSettings(
    val github: GitHubSource = GitHubSource(),
    val drive: DriveSource = DriveSource(),
    /** Friendly name for this device, written into uploaded question logs. */
    val deviceName: String = "",
)

sealed interface SourceSyncResult {
    val sourceName: String

    data class UpToDate(override val sourceName: String) : SourceSyncResult
    data class Downloaded(override val sourceName: String, val outcomes: List<InstallOutcome>) : SourceSyncResult
    data class Failed(override val sourceName: String, val reason: String) : SourceSyncResult
}

sealed interface UploadResult {
    data class Uploaded(val count: Int, val location: String) : UploadResult
    data object NothingToUpload : UploadResult
    data class Failed(val reason: String) : UploadResult
}
