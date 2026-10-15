package com.kidlearn.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kidlearn.domain.model.CoursePackSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Builds [CoursePackSource]s from the places content can come from: any file the user picks
 * (internal storage, SD card, USB drive, cloud-drive app...), a web link, the app's import
 * folder, or packs bundled inside the APK.
 */
class PackSources internal constructor(
    private val context: Context,
    private val io: CoroutineDispatcher,
) {
    /** Folder teachers/parents can copy packs into over USB: Android/data/<app>/files/import. */
    val importFolder: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "import").apply { mkdirs() }

    fun fromUri(uri: Uri): CoursePackSource {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "pack"
        val mime = resolver.getType(uri)
        return CoursePackSource(name, formatOf(name, mime)) {
            resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $name")
        }
    }

    fun fromFile(file: File): CoursePackSource = CoursePackSource(file.name, formatOf(file.name, null)) { file.inputStream() }

    /** Downloads to a cache file first, so installation never depends on a flaky connection. */
    suspend fun fromUrl(url: String): CoursePackSource = withContext(io) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) throw IOException("Server answered ${connection.responseCode}")
            val target = File(context.cacheDir, "download-pack.tmp")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
            val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
            CoursePackSource(name, formatOf(name, connection.contentType)) { target.inputStream() }
        } finally {
            connection.disconnect()
        }
    }

    fun pendingImports(): List<Pair<File, CoursePackSource>> =
        importFolder.listFiles { f -> f.isFile && (f.name.endsWith(".zip", true) || f.name.endsWith(".json", true)) }
            ?.sortedBy { it.name }?.map { it to fromFile(it) }.orEmpty()

    fun bundled(): List<CoursePackSource> =
        context.assets.list(BUNDLED_DIR).orEmpty().filter { it.endsWith(".zip") }.map { name ->
            CoursePackSource(name, CoursePackSource.Format.ZIP) { context.assets.open("$BUNDLED_DIR/$name") }
        }

    private fun formatOf(name: String, mime: String?): CoursePackSource.Format = when {
        name.endsWith(".json", ignoreCase = true) || mime?.contains("json") == true -> CoursePackSource.Format.MANIFEST
        else -> CoursePackSource.Format.ZIP
    }

    private companion object {
        const val BUNDLED_DIR = "bundled"
    }
}
