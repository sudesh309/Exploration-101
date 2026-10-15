package com.kidlearn.content

import com.kidlearn.content.install.CourseStore
import com.kidlearn.content.parse.ManifestParser
import com.kidlearn.content.parse.ManifestResult
import com.kidlearn.domain.model.AudioBlock
import com.kidlearn.domain.model.ChoiceQuestion
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.ImageBlock
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.MediaRef
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.UnsupportedBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CourseStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun manifest(version: Int = 1, extraBlock: String = "") = """
        {
          "schemaVersion": 1,
          "id": "space",
          "version": $version,
          "title": "Space",
          "subject": {"id": "science", "name": "Science", "color": "#42A5F5"},
          "ageRange": {"min": 6, "max": 10},
          "units": [{"id": "u1", "title": "Planets", "lessons": [
            {"id": "l1", "title": "The Sun", "points": 5, "blocks": [
              {"type": "text", "text": "The Sun is a star.", "style": "heading"},
              {"type": "image", "src": "img/sun.png", "caption": "Sun"},
              {"type": "audio", "src": "audio/sun.mp3"}$extraBlock
            ]},
            {"id": "l2", "title": "Quiz", "blocks": [
              {"type": "quiz", "id": "q", "questions": [
                {"id": "a", "type": "true_false", "prompt": "The Sun is a star", "answer": true},
                {"id": "b", "type": "single_choice", "prompt": "Closest planet?", "options": ["Mars", "Mercury"], "answer": 1},
                {"id": "c", "type": "single_choice", "prompt": "broken", "options": ["x"], "answer": 0}
              ]}
            ]}
          ]}]
        }
    """.trimIndent()

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun pack(manifest: String, prefix: String = "", media: Boolean = true): CoursePackSource {
        val files = buildMap {
            put("${prefix}course.json", manifest.toByteArray())
            if (media) {
                put("${prefix}img/sun.png", byteArrayOf(1, 2, 3))
                put("${prefix}audio/sun.mp3", byteArrayOf(4, 5))
            }
        }
        val data = zip(files)
        return CoursePackSource("space.zip", CoursePackSource.Format.ZIP) { data.inputStream() }
    }

    @Test
    fun `parses all block and question types, skipping broken ones`() {
        val result = ManifestParser().parse(manifest(extraBlock = """, {"type": "hologram"}"""), { MediaRef(it) })
        result as ManifestResult.Success
        val lessons = result.course.lessons
        assertEquals(2, lessons.size)
        assertTrue(lessons[0].blocks[1] is ImageBlock)
        assertTrue(lessons[0].blocks[2] is AudioBlock)
        assertTrue(lessons[0].blocks[3] is UnsupportedBlock)
        val quiz = (lessons[1].blocks.single() as QuizBlock).quiz
        assertEquals(2, quiz.questions.size) // the broken question is skipped
        assertEquals(setOf(0), (quiz.questions[0] as ChoiceQuestion).correctOptions)
        assertEquals(10, lessons[1].completionPoints) // default
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `installs, resolves media to local files, and updates by version`() {
        val store = CourseStore(tmp.newFolder("courses"))

        val first = store.install(pack(manifest(version = 1), prefix = "space/"))
        assertTrue(first is InstallOutcome.Installed)
        val image = store.load("space")!!.lessons[0].blocks[1] as ImageBlock
        assertTrue(File(image.image.location).isFile)

        assertTrue(store.install(pack(manifest(version = 1))) is InstallOutcome.AlreadyUpToDate)

        val updated = store.install(pack(manifest(version = 2)))
        assertEquals(1, (updated as InstallOutcome.Updated).previousVersion)
        assertEquals(2, store.load("space")!!.version)

        assertTrue(store.install(pack(manifest(version = 1))) is InstallOutcome.Rejected)
        assertEquals(listOf("space"), store.installedCourseIds())
    }

    @Test
    fun `rejects packs with missing media or unsafe paths`() {
        val store = CourseStore(tmp.newFolder("courses"))
        val missing = store.install(pack(manifest(), media = false)) as InstallOutcome.Rejected
        assertTrue(missing.problems.any { "img/sun.png" in it })

        val evilData = zip(mapOf("../evil.txt" to byteArrayOf(1), "course.json" to manifest().toByteArray()))
        val evil = store.install(CoursePackSource("evil.zip", CoursePackSource.Format.ZIP) { evilData.inputStream() })
        assertTrue(evil is InstallOutcome.Rejected)
        assertTrue(store.installedCourseIds().isEmpty())
    }

    @Test
    fun `bare manifest with remote media installs`() {
        val store = CourseStore(tmp.newFolder("courses"))
        val json = manifest().replace("img/sun.png", "https://example.org/sun.png").replace("audio/sun.mp3", "https://example.org/sun.mp3")
        val outcome = store.install(CoursePackSource("course.json", CoursePackSource.Format.MANIFEST) { json.byteInputStream() })
        assertTrue(outcome is InstallOutcome.Installed)
    }

    @Test
    fun `invalid json is rejected with a readable error`() {
        val result = ManifestParser().parse("{ nope", { MediaRef(it) })
        assertTrue(result is ManifestResult.Failure)
    }
}
