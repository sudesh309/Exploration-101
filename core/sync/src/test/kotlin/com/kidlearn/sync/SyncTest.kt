package com.kidlearn.sync

import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.CourseSummary
import com.kidlearn.domain.model.DriveSource
import com.kidlearn.domain.model.GitHubSource
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.KidQuestion
import com.kidlearn.domain.model.SourceSyncResult
import com.kidlearn.domain.model.SyncSettings
import com.kidlearn.domain.model.UploadResult
import com.kidlearn.domain.repository.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Answers requests from a URL-prefix table and records every request. */
    private class FakeHttp(private val routes: Map<String, Pair<Int, ByteArray>>) : HttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val (code, body) = routes.entries.firstOrNull { request.url.startsWith(it.key) && request.method == "GET" || request.method != "GET" && it.key == "PUT ${request.url}" }?.value
                ?: (404 to "not found".toByteArray())
            return HttpResponse(code, body.inputStream())
        }
    }

    private class RecordingCourses : CourseRepository {
        val bundles = mutableListOf<Pair<String, String>>()
        override fun observeCourses(): Flow<List<CourseSummary>> = emptyFlow()
        override suspend fun getCourse(courseId: String): Course? = null
        override suspend fun install(source: CoursePackSource, allowSameVersion: Boolean): InstallOutcome {
            bundles += source.displayName to "manifest"
            return InstallOutcome.Rejected(listOf("not used"))
        }
        override suspend fun installBundle(source: CoursePackSource, subPath: String): List<InstallOutcome> {
            bundles += source.displayName to subPath
            return emptyList()
        }
        override suspend fun remove(courseId: String) = Unit
    }

    private class MemoryState : SyncStateStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    private val zipBytes: ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("repo-abc/course.json")); it.write("{}".toByteArray()); it.closeEntry() }
    }.toByteArray()

    private val github = GitHubSource(owner = "school", repo = "courses", branch = "main", contentPath = "packs", token = "t0k")

    @Test
    fun `github download installs the archive and skips it when unchanged`() = runTest {
        val http = FakeHttp(
            mapOf(
                "https://api.github.com/repos/school/courses/commits/main" to (200 to "sha1\n".toByteArray()),
                "https://api.github.com/repos/school/courses/zipball/main" to (200 to zipBytes),
            ),
        )
        val courses = RecordingCourses()
        val state = MemoryState()
        val sync = RemoteContentSync(courses, state, tmp.newFolder("dl"), Dispatchers.Unconfined, http)
        val settings = SyncSettings(github = github)

        val first = sync.downloadAll(settings).single()
        assertTrue(first is SourceSyncResult.Downloaded)
        assertEquals(listOf("GitHub school/courses" to "packs"), courses.bundles)
        assertEquals("Bearer t0k", http.requests.first().headers["Authorization"])

        assertTrue(sync.downloadAll(settings).single() is SourceSyncResult.UpToDate)
        assertTrue(sync.downloadAll(settings, force = true).single() is SourceSyncResult.Downloaded)
    }

    @Test
    fun `one failing source does not stop the other`() = runTest {
        val http = FakeHttp(
            mapOf(
                "https://drive.usercontent.google.com/download?id=1AbCdEfGhIjK" to (200 to zipBytes),
            ),
        )
        val courses = RecordingCourses()
        val sync = RemoteContentSync(courses, MemoryState(), tmp.newFolder("dl"), Dispatchers.Unconfined, http)
        val results = sync.downloadAll(
            SyncSettings(github = github, drive = DriveSource(link = "https://drive.google.com/file/d/1AbCdEfGhIjK/view?usp=sharing")),
        )
        assertTrue(results[0] is SourceSyncResult.Failed)
        assertTrue(results[1] is SourceSyncResult.Downloaded)
        assertEquals(1, courses.bundles.size)
    }

    @Test
    fun `drive web page instead of a zip is reported clearly`() = runTest {
        val http = FakeHttp(mapOf("https://drive.usercontent.google.com/download" to (200 to "<html>sign in</html>".toByteArray())))
        val sync = RemoteContentSync(RecordingCourses(), MemoryState(), tmp.newFolder("dl"), Dispatchers.Unconfined, http)
        val result = sync.downloadAll(SyncSettings(drive = DriveSource(link = "1AbCdEfGhIjKlmn"))).single()
        assertTrue((result as SourceSyncResult.Failed).reason.contains("did not return a zip"))
    }

    @Test
    fun `drive folder is listed with the api key and each pack is downloaded`() = runTest {
        val listing = """{"files":[{"id":"f1aaaaaaaaaa","name":"maths.zip","modifiedTime":"t1"},
            {"id":"f2aaaaaaaaaa","name":"notes.txt","modifiedTime":"t2"},
            {"id":"f3aaaaaaaaaa","name":"quiz.json","modifiedTime":"t3"}]}"""
        val http = FakeHttp(
            mapOf(
                "https://www.googleapis.com/drive/v3/files?q=" to (200 to listing.toByteArray()),
                "https://www.googleapis.com/drive/v3/files/f1aaaaaaaaaa?alt=media" to (200 to zipBytes),
                "https://www.googleapis.com/drive/v3/files/f3aaaaaaaaaa?alt=media" to (200 to "{}".toByteArray()),
            ),
        )
        val courses = RecordingCourses()
        val state = MemoryState()
        val sync = RemoteContentSync(courses, state, tmp.newFolder("dl"), Dispatchers.Unconfined, http)
        val settings = SyncSettings(drive = DriveSource("https://drive.google.com/drive/folders/FOLDER12345?usp=sharing", apiKey = "KEY"))
        sync.downloadAll(settings)
        assertEquals(listOf("maths.zip" to "", "quiz.json" to "manifest"), courses.bundles)
        // quiz.json was "rejected" by the fake, so the version is not recorded and a retry happens next time.
        assertNull(state.map["drive:FOLDER12345"])
    }

    @Test
    fun `question log round-trips and uploads to github with the existing sha`() = runTest {
        val q = KidQuestion("id1", 1, "Ada", 8, "maths", "Counting", "l1", "Apples", 2, "image", "How many?", "Why red?", 1_700_000_000_000)
        val text = QuestionLogCodec.encode(listOf(q), "dev1", "Tablet", 1_700_000_100_000)
        assertEquals(listOf(q), QuestionLogCodec.decode(text))
        assertTrue(text.contains("\"exportedAt\": \"2023-11-14T22:"))

        val contentsUrl = "https://api.github.com/repos/school/courses/contents/questions/dev1.json"
        val http = FakeHttp(
            mapOf(
                "$contentsUrl?ref=main" to (200 to """{"sha":"old-sha"}""".toByteArray()),
                "PUT $contentsUrl" to (200 to "{}".toByteArray()),
            ),
        )
        val result = GitHubQuestionUploader(Dispatchers.Unconfined, { 0L }, http)
            .upload(listOf(q), SyncSettings(github = github.copy(questionsPath = "/questions/")), "dev1")
        assertTrue(result is UploadResult.Uploaded)
        val put = http.requests.last()
        assertEquals("PUT", put.method)
        val body = Json.parseToJsonElement(String(put.body!!)).jsonObject
        assertEquals("old-sha", body["sha"]!!.jsonPrimitive.content)
        assertEquals("main", body["branch"]!!.jsonPrimitive.content)
    }

    @Test
    fun `upload without a token explains what to set up`() = runTest {
        val result = GitHubQuestionUploader(Dispatchers.Unconfined, http = FakeHttp(emptyMap()))
            .upload(emptyList(), SyncSettings(github = github.copy(token = "")), "dev")
        assertTrue(result is UploadResult.Failed)
    }
}
