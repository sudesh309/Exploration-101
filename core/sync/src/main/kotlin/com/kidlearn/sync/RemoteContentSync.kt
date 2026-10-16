package com.kidlearn.sync

import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.DriveSource
import com.kidlearn.domain.model.GitHubSource
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.SourceSyncResult
import com.kidlearn.domain.model.SyncSettings
import com.kidlearn.domain.model.UploadResult
import com.kidlearn.domain.repository.Clock
import com.kidlearn.domain.repository.ContentSync
import com.kidlearn.domain.repository.CourseRepository
import com.kidlearn.domain.repository.QuestionUploader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Remembers what was last downloaded from each source, so unchanged sources are skipped. */
interface SyncStateStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * Downloads from every configured source (GitHub and Google Drive) and installs all courses
 * found. Each source is independent: one failing (offline, wrong link) never blocks the other.
 */
class RemoteContentSync(
    private val courses: CourseRepository,
    private val state: SyncStateStore,
    private val downloadDir: File,
    private val io: CoroutineDispatcher,
    http: HttpClient = UrlConnectionHttpClient(),
) : ContentSync {
    private val github = GitHubClient(http)
    private val drive = GoogleDriveClient(http)

    override suspend fun downloadAll(settings: SyncSettings, force: Boolean): List<SourceSyncResult> = buildList {
        if (settings.github.isConfigured) add(syncGitHub(settings.github, force))
        if (settings.drive.isConfigured) add(syncDrive(settings.drive, force))
    }

    private suspend fun syncGitHub(source: GitHubSource, force: Boolean): SourceSyncResult = guarded(source.displayName) {
        val key = "github:${source.owner}/${source.repo}@${source.branch}:${source.contentPath}"
        val version = withContext(io) { github.latestCommit(source) }
        if (!force && state.get(key) == version) return@guarded SourceSyncResult.UpToDate(source.displayName)

        val archive = File(downloadDir, "github.zip")
        try {
            withContext(io) {
                github.downloadArchive(source, archive)
                requireLooksLikeZip(archive, source.displayName)
            }
            val outcomes = courses.installBundle(zipSource(archive, source.displayName), source.contentPath)
            if (outcomes.none { it is InstallOutcome.Rejected }) state.put(key, version)
            SourceSyncResult.Downloaded(source.displayName, outcomes)
        } finally {
            archive.delete()
        }
    }

    private suspend fun syncDrive(source: DriveSource, force: Boolean): SourceSyncResult = guarded(source.displayName) {
        val target = withContext(io) { drive.parse(source) }
        val key = "drive:${target.id}"
        val files: List<GoogleDriveClient.DriveFile> = withContext(io) {
            when (target) {
                is GoogleDriveClient.Target.Folder -> drive.listFolder(target.id, source.apiKey)
                is GoogleDriveClient.Target.SingleFile ->
                    listOf(GoogleDriveClient.DriveFile(target.id, "drive-pack.zip", drive.modifiedTime(target.id, source.apiKey)))
            }
        }
        if (files.isEmpty()) return@guarded SourceSyncResult.Failed(source.displayName, "The Drive folder has no .zip or .json course files.")
        // Without modification times (single file, no API key) we cannot tell; always download.
        val version = files.takeIf { list -> list.all { it.modifiedTime != null } }
            ?.joinToString("|") { "${it.id}:${it.modifiedTime}" }
        if (!force && version != null && state.get(key) == version) return@guarded SourceSyncResult.UpToDate(source.displayName)

        val outcomes = files.flatMapIndexed { index, file ->
            val local = File(downloadDir, "drive-$index-${file.name.filter { it.isLetterOrDigit() || it in "._-" }}")
            try {
                withContext(io) { drive.download(file.id, source.apiKey, local) }
                if (file.name.endsWith(".json", ignoreCase = true)) {
                    listOf(courses.install(CoursePackSource(file.name, CoursePackSource.Format.MANIFEST) { local.inputStream() }))
                } else {
                    withContext(io) { requireLooksLikeZip(local, source.displayName) }
                    courses.installBundle(zipSource(local, file.name))
                }
            } finally {
                local.delete()
            }
        }
        if (version != null && outcomes.none { it is InstallOutcome.Rejected }) state.put(key, version)
        SourceSyncResult.Downloaded(source.displayName, outcomes)
    }

    private inline fun guarded(name: String, block: () -> SourceSyncResult): SourceSyncResult = try {
        downloadDir.mkdirs()
        block()
    } catch (e: IOException) {
        SourceSyncResult.Failed(name, e.message ?: "Network error")
    }

    private fun zipSource(file: File, name: String) = CoursePackSource(name, CoursePackSource.Format.ZIP) { file.inputStream() }
}

/**
 * Uploads the question log to the configured GitHub repository as
 * `<questionsPath>/<deviceId>.json`. Each device owns its own file, so devices never overwrite
 * each other's questions and no merging is needed.
 */
class GitHubQuestionUploader(
    private val io: CoroutineDispatcher,
    private val clock: Clock = Clock.SYSTEM,
    http: HttpClient = UrlConnectionHttpClient(),
) : QuestionUploader {
    private val github = GitHubClient(http)

    override suspend fun upload(questions: List<KidQuestion>, settings: SyncSettings, deviceId: String): UploadResult {
        val source = settings.github
        if (!source.canUpload) {
            return UploadResult.Failed("Set up GitHub (repository and a token with write access) in the grown-ups settings first.")
        }
        val folder = source.questionsPath.trim().trim('/').ifEmpty { "questions" }
        val path = "$folder/$deviceId.json"
        val content = QuestionLogCodec.encode(questions, deviceId, settings.deviceName, clock.nowMillis()).toByteArray()
        return try {
            withContext(io) {
                github.putFile(source, path, content, "KidLearn: questions from ${settings.deviceName.ifBlank { deviceId.take(8) }}")
            }
            UploadResult.Uploaded(questions.size, "github.com/${source.owner}/${source.repo}/blob/${source.branch}/$path")
        } catch (e: IOException) {
            UploadResult.Failed(e.message ?: "Upload failed")
        }
    }
}
