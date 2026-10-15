package com.kidlearn.content.install

import com.kidlearn.domain.model.CoursePackSource
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

internal object PackPaths {
    /** Returns a safe relative path, or null if it tries to escape the pack (zip-slip) or is absolute. */
    fun normalize(path: String): String? {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || path.startsWith("/") || path.contains(':')) return null
        return parts.joinToString("/")
    }
}

/**
 * Extracts a pack into a staging directory with streaming I/O (constant memory, so even large
 * videos are fine on low-RAM phones) and returns the directory that holds `course.json`.
 */
class PackUnpacker(
    private val maxTotalBytes: Long = 4L * 1024 * 1024 * 1024,
    private val maxEntries: Int = 10_000,
) {
    fun unpack(source: CoursePackSource, destination: File): File {
        destination.mkdirs()
        when (source.format) {
            CoursePackSource.Format.MANIFEST -> source.openStream().use { input ->
                File(destination, CourseStore.MANIFEST).outputStream().use { input.copyTo(it, BUFFER) }
            }
            CoursePackSource.Format.ZIP -> unzip(source, destination)
        }
        return locateManifestRoot(destination)
    }

    private fun unzip(source: CoursePackSource, destination: File) {
        var total = 0L
        var entries = 0
        ZipInputStream(source.openStream().buffered(BUFFER)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > maxEntries) throw IOException("pack has too many files")
                val name = entry.name
                if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith("._")) continue
                val safe = PackPaths.normalize(name) ?: throw IOException("unsafe path in pack: $name")
                val out = File(destination, safe)
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { os ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxTotalBytes) throw IOException("pack is larger than allowed")
                        os.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    /** Accept both `course.json` at the zip root and inside a single top-level folder. */
    private fun locateManifestRoot(dir: File): File {
        if (File(dir, CourseStore.MANIFEST).isFile) return dir
        val children = dir.listFiles { f -> f.isDirectory }.orEmpty()
        val single = children.singleOrNull { File(it, CourseStore.MANIFEST).isFile }
        return single ?: throw IOException("no ${CourseStore.MANIFEST} found in pack")
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
