package com.kidlearn.content.install

import com.kidlearn.content.parse.ManifestParser
import com.kidlearn.content.parse.ManifestResult
import com.kidlearn.content.parse.MediaResolver
import com.kidlearn.domain.model.Course
import com.kidlearn.domain.model.CoursePackSource
import com.kidlearn.domain.model.InstallOutcome
import com.kidlearn.domain.model.MediaRef
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * File-system storage of installed courses: one directory per course under [root], each holding
 * its `course.json` and media. Everything is local, so installed courses work fully offline.
 *
 * Not thread-safe on its own; callers serialise installs (the repository uses a Mutex).
 */
class CourseStore(
    private val root: File,
    private val parser: ManifestParser = ManifestParser(),
    private val unpacker: PackUnpacker = PackUnpacker(),
) {
    init {
        root.mkdirs()
        // Clean up after an install interrupted by the app being killed.
        root.listFiles { f -> f.name.startsWith(TEMP_PREFIX) }?.forEach { it.deleteRecursively() }
    }

    fun installedCourseIds(): List<String> =
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && File(f, MANIFEST).isFile }
            ?.map { it.name }?.sorted().orEmpty()

    /** Loads a course, resolving its media to absolute file paths. Returns null if missing or invalid. */
    fun load(courseId: String): Course? {
        val dir = File(root, courseId)
        val manifest = File(dir, MANIFEST).takeIf { it.isFile } ?: return null
        return (parser.parse(manifest.readText(), resolverFor(dir)) as? ManifestResult.Success)?.course
    }

    fun remove(courseId: String) {
        File(root, courseId).takeIf { it.parentFile == root }?.deleteRecursively()
    }

    fun install(source: CoursePackSource, allowSameVersion: Boolean = false): InstallOutcome {
        val staging = newStagingDir()
        try {
            val packRoot = try {
                unpacker.unpack(source, staging)
            } catch (e: IOException) {
                return InstallOutcome.Rejected(listOf("Could not read '${source.displayName}': ${e.message}"))
            }
            return installUnpacked(packRoot, allowSameVersion)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Installs every course found in a bundle: a zip (for example a GitHub repository download)
     * holding any number of course folders and/or nested `.zip` packs, at any depth.
     * [subPath] limits the search to one folder of the bundle.
     */
    fun installBundle(source: CoursePackSource, subPath: String = "", allowSameVersion: Boolean = false): List<InstallOutcome> {
        if (source.format == CoursePackSource.Format.MANIFEST) return listOf(install(source, allowSameVersion))
        val staging = newStagingDir()
        try {
            try {
                unpacker.extract(source, staging)
            } catch (e: IOException) {
                return listOf(InstallOutcome.Rejected(listOf("Could not read '${source.displayName}': ${e.message}")))
            }
            val searchRoot = searchRoot(staging, subPath)
                ?: return listOf(InstallOutcome.Rejected(listOf("Folder '$subPath' not found in '${source.displayName}'")))
            val courseDirs = searchRoot.walkTopDown().maxDepth(MAX_BUNDLE_DEPTH)
                .filter { it.isDirectory && File(it, MANIFEST).isFile }
                .toList()
            // A course folder's own media never contains another course; skip nested matches.
            val topLevelCourses = courseDirs.filter { dir -> courseDirs.none { other -> other != dir && dir.startsWith(other) } }
            val nestedPacks = searchRoot.walkTopDown().maxDepth(MAX_BUNDLE_DEPTH)
                .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                .filter { zip -> topLevelCourses.none { zip.startsWith(it) } }
                .sortedBy { it.path }
                .toList()

            val outcomes = topLevelCourses.sortedBy { it.path }.map { installUnpacked(it, allowSameVersion) } +
                nestedPacks.map { zip ->
                    install(CoursePackSource(zip.name, CoursePackSource.Format.ZIP) { zip.inputStream() }, allowSameVersion)
                }
            return outcomes.ifEmpty { listOf(InstallOutcome.Rejected(listOf("No courses found in '${source.displayName}'"))) }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun searchRoot(extracted: File, subPath: String): File? {
        val cleaned = subPath.trim().trim('/')
        if (cleaned.isEmpty()) return extracted
        val safe = PackPaths.normalize(cleaned) ?: return null
        File(extracted, safe).takeIf { it.isDirectory }?.let { return it }
        // GitHub archives wrap everything in a single "<repo>-<sha>/" folder.
        val wrapper = extracted.listFiles { f -> f.isDirectory }?.singleOrNull() ?: return null
        return File(wrapper, safe).takeIf { it.isDirectory }
    }

    private fun installUnpacked(packRoot: File, allowSameVersion: Boolean): InstallOutcome {
        val missing = linkedSetOf<String>()
        val result = parser.parse(File(packRoot, MANIFEST).readText(), checkingResolver(packRoot, missing))
        val course = when (result) {
            is ManifestResult.Failure -> return InstallOutcome.Rejected(result.errors)
            is ManifestResult.Success -> result.course
        }
        if (missing.isNotEmpty()) {
            return InstallOutcome.Rejected(missing.map { "'${course.title}': missing media file $it" })
        }

        val existing = load(course.id)
        if (existing != null && existing.version > course.version) {
            return InstallOutcome.Rejected(
                listOf("Installed '${course.title}' is version ${existing.version}; the pack is older (version ${course.version})."),
            )
        }
        if (existing != null && existing.version == course.version && !allowSameVersion) {
            return InstallOutcome.AlreadyUpToDate(existing.toSummary())
        }

        replaceDirectory(packRoot, File(root, course.id))
        val installed = load(course.id) ?: return InstallOutcome.Rejected(listOf("Course could not be loaded after install"))
        return if (existing == null) {
            InstallOutcome.Installed(installed.toSummary())
        } else {
            InstallOutcome.Updated(installed.toSummary(), existing.version)
        }
    }

    private fun newStagingDir() = File(root, "$TEMP_PREFIX${UUID.randomUUID()}")

    /** Swaps in the new content with renames so a crash never leaves a half-written course. */
    private fun replaceDirectory(newContent: File, target: File) {
        val backup = File(root, "$TEMP_PREFIX${target.name}-old-${UUID.randomUUID()}")
        if (target.exists() && !target.renameTo(backup)) throw IOException("Could not replace ${target.name}")
        if (!newContent.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("Could not move course into place")
        }
        backup.deleteRecursively()
    }

    private fun resolverFor(dir: File) = MediaResolver { path ->
        if (isRemote(path)) MediaRef(path) else MediaRef(File(dir, PackPaths.normalize(path) ?: path).absolutePath)
    }

    private fun checkingResolver(dir: File, missing: MutableSet<String>) = MediaResolver { path ->
        if (isRemote(path)) return@MediaResolver MediaRef(path)
        val safe = PackPaths.normalize(path)
        val file = safe?.let { File(dir, it) }
        if (file == null || !file.isFile) missing += path
        MediaRef(file?.absolutePath ?: path)
    }

    private fun isRemote(path: String) = path.startsWith("https://") || path.startsWith("http://")

    companion object {
        const val MANIFEST = "course.json"
        private const val TEMP_PREFIX = ".tmp-"
        private const val MAX_BUNDLE_DEPTH = 8
    }
}
