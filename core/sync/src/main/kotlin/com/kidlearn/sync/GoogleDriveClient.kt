package com.kidlearn.sync

import com.kidlearn.domain.model.DriveSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

/**
 * Downloads course bundles from Google Drive without requiring a Google sign-in:
 *
 * * a **shared file** ("Anyone with the link") is downloaded directly; no API key needed.
 * * a **shared folder** is listed with the Drive API, which needs an API key (free, created in
 *   Google Cloud Console with the Drive API enabled). Every `.zip` / `.json` in it is downloaded.
 */
class GoogleDriveClient(
    private val http: HttpClient,
    private val apiBase: String = "https://www.googleapis.com/drive/v3",
    private val publicDownloadBase: String = "https://drive.usercontent.google.com/download",
) {
    data class DriveFile(val id: String, val name: String, val modifiedTime: String?)

    sealed interface Target {
        val id: String

        data class Folder(override val id: String) : Target
        data class SingleFile(override val id: String) : Target
    }

    fun parse(source: DriveSource): Target {
        val link = source.link.trim()
        FOLDER.find(link)?.let { return Target.Folder(it.groupValues[1]) }
        FILE.find(link)?.let { return Target.SingleFile(it.groupValues[1]) }
        ID_PARAM.find(link)?.let { return Target.SingleFile(it.groupValues[1]) }
        if (RAW_ID.matches(link)) {
            // A bare ID could be either; with an API key we can ask Drive which one it is.
            return if (source.apiKey.isNotBlank() && isFolder(link, source.apiKey)) Target.Folder(link) else Target.SingleFile(link)
        }
        throw IOException("This doesn't look like a Google Drive link: $link")
    }

    fun listFolder(folderId: String, apiKey: String): List<DriveFile> {
        if (apiKey.isBlank()) throw IOException("Listing a Google Drive folder needs an API key (or share a single zip file instead).")
        val query = urlEncode("'$folderId' in parents and trashed = false")
        val url = "$apiBase/files?q=$query&fields=${urlEncode("files(id,name,modifiedTime,mimeType)")}&pageSize=1000&key=${urlEncode(apiKey)}"
        http.execute(HttpRequest(url)).use { response ->
            if (!response.isSuccess) throw response.failure("Listing the Google Drive folder (is it shared with 'Anyone with the link'?)")
            val files = (json.parseToJsonElement(response.text()) as? JsonObject)?.get("files") as? JsonArray ?: return emptyList()
            return files.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!name.endsWith(".zip", true) && !name.endsWith(".json", true)) return@mapNotNull null
                DriveFile(obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null, name, obj["modifiedTime"]?.jsonPrimitive?.content)
            }.sortedBy { it.name }
        }
    }

    /** Last-modified time of a single file, or null if it can't be known without an API key. */
    fun modifiedTime(fileId: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val url = "$apiBase/files/${urlEncode(fileId)}?fields=modifiedTime&key=${urlEncode(apiKey)}"
        http.execute(HttpRequest(url)).use { response ->
            if (!response.isSuccess) throw response.failure("Reading the Google Drive file")
            return (json.parseToJsonElement(response.text()) as? JsonObject)?.get("modifiedTime")?.jsonPrimitive?.content
        }
    }

    fun download(fileId: String, apiKey: String, target: File) {
        val url = if (apiKey.isNotBlank()) {
            "$apiBase/files/${urlEncode(fileId)}?alt=media&key=${urlEncode(apiKey)}"
        } else {
            // confirm=t skips the "can't scan this file for viruses" page shown for large files.
            "$publicDownloadBase?id=${urlEncode(fileId)}&export=download&confirm=t"
        }
        http.execute(HttpRequest(url)).use { response ->
            if (!response.isSuccess) throw response.failure("Downloading from Google Drive (is the file shared with 'Anyone with the link'?)")
            response.saveTo(target)
        }
    }

    private fun isFolder(id: String, apiKey: String): Boolean {
        val url = "$apiBase/files/${urlEncode(id)}?fields=mimeType&key=${urlEncode(apiKey)}"
        http.execute(HttpRequest(url)).use { response ->
            if (!response.isSuccess) return false
            val mime = (json.parseToJsonElement(response.text()) as? JsonObject)?.get("mimeType")?.jsonPrimitive?.content
            return mime == FOLDER_MIME
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val FOLDER = Regex("/folders/([A-Za-z0-9_-]{10,})")
        val FILE = Regex("/file/d/([A-Za-z0-9_-]{10,})")
        val ID_PARAM = Regex("[?&]id=([A-Za-z0-9_-]{10,})")
        val RAW_ID = Regex("[A-Za-z0-9_-]{10,}")
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}
