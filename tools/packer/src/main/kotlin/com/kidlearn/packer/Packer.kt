package com.kidlearn.packer

import com.kidlearn.content.parse.ManifestParser
import com.kidlearn.content.parse.ManifestResult
import com.kidlearn.content.parse.MediaResolver
import com.kidlearn.domain.model.MediaRef
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Content-author tool: validates a course folder and zips it into an importable pack.
 *
 *   ./gradlew :tools:packer:run --args="path/to/course-folder [output.zip]"
 *
 * The same parser as the app is used, so a pack that validates here imports in the app.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: packer <course-folder> [output.zip]")
        exitProcess(2)
    }
    val folder = File(args[0]).absoluteFile
    val manifest = File(folder, "course.json")
    if (!manifest.isFile) fail("No course.json in $folder")

    val missing = mutableListOf<String>()
    val resolver = MediaResolver { path ->
        if (!path.startsWith("http") && !File(folder, path).isFile) missing += path
        MediaRef(path)
    }
    val course = when (val result = ManifestParser().parse(manifest.readText(), resolver)) {
        is ManifestResult.Failure -> fail(result.errors.joinToString("\n  ", prefix = "Invalid course:\n  "))
        is ManifestResult.Success -> {
            result.warnings.forEach { println("warning: $it") }
            result.course
        }
    }
    if (missing.isNotEmpty()) fail(missing.joinToString("\n  ", prefix = "Missing media files:\n  "))

    val output = File(args.getOrElse(1) { "${course.id}-v${course.version}.zip" }).absoluteFile
    output.parentFile?.mkdirs()
    ZipOutputStream(output.outputStream().buffered()).use { zip ->
        folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.path }.forEach { file ->
            val entry = ZipEntry(file.relativeTo(folder).invariantSeparatorsPath)
            // Media is already compressed; storing it avoids wasting CPU on the phone when unpacking.
            if (file.extension.lowercase() in COMPRESSED) {
                entry.method = ZipEntry.STORED
                entry.size = file.length()
                entry.crc = crcOf(file)
            }
            entry.time = 0 // reproducible output
            zip.putNextEntry(entry)
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    println("OK: '${course.title}' v${course.version} (${course.subject.name}, ages ${course.ageRange.min}-${course.ageRange.max}), ${course.lessons.size} lessons -> $output")
}

private val COMPRESSED = setOf("mp4", "webm", "mkv", "mp3", "m4a", "aac", "ogg", "opus", "jpg", "jpeg", "png", "webp", "gif", "zip")

private fun crcOf(file: File): Long {
    val crc = CRC32()
    val buffer = ByteArray(64 * 1024)
    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
        }
    }
    return crc.value
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
