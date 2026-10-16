package com.kidlearn.sync

import com.kidlearn.domain.model.GitHubSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The few GitHub REST calls KidLearn needs: latest commit, repository download, file upload. */
class GitHubClient(private val http: HttpClient, private val apiBase: String = "https://api.github.com") {

    /** SHA of the branch's latest commit: a cheap way to know whether anything changed. */
    fun latestCommit(source: GitHubSource): String {
        val url = "${repoUrl(source)}/commits/${urlEncode(source.branch)}"
        http.execute(HttpRequest(url, headers = headers(source, accept = "application/vnd.github.sha"))).use { response ->
            if (!response.isSuccess) throw response.failure(describe(response.code, source, "Checking"))
            return response.text().trim()
        }
    }

    /** Downloads the whole branch as one zip (GitHub's "Download ZIP"). */
    fun downloadArchive(source: GitHubSource, target: File) {
        val url = "${repoUrl(source)}/zipball/${urlEncode(source.branch)}"
        http.execute(HttpRequest(url, headers = headers(source))).use { response ->
            if (!response.isSuccess) throw response.failure(describe(response.code, source, "Downloading"))
            response.saveTo(target)
        }
    }

    /** Creates or replaces one file in the repository (a commit on [GitHubSource.branch]). */
    @OptIn(ExperimentalEncodingApi::class)
    fun putFile(source: GitHubSource, path: String, content: ByteArray, message: String) {
        val url = "${repoUrl(source)}/contents/${encodePath(path)}"
        val existingSha = http.execute(HttpRequest("$url?ref=${urlEncode(source.branch)}", headers = headers(source))).use { response ->
            when {
                response.code == 404 -> null
                response.isSuccess -> (json.parseToJsonElement(response.text()) as? JsonObject)?.get("sha")?.jsonPrimitive?.content
                else -> throw response.failure(describe(response.code, source, "Reading $path"))
            }
        }
        val body = buildJsonObject {
            put("message", message)
            put("content", Base64.encode(content))
            put("branch", source.branch)
            if (existingSha != null) put("sha", existingSha)
        }.toString().toByteArray()
        val request = HttpRequest(url, method = "PUT", headers = headers(source) + ("Content-Type" to "application/json"), body = body)
        http.execute(request).use { response ->
            if (!response.isSuccess) throw response.failure(describe(response.code, source, "Uploading $path"))
        }
    }

    private fun repoUrl(source: GitHubSource) = "$apiBase/repos/${urlEncode(source.owner.trim())}/${urlEncode(source.repo.trim())}"

    private fun headers(source: GitHubSource, accept: String = "application/vnd.github+json"): Map<String, String> = buildMap {
        put("Accept", accept)
        put("User-Agent", "KidLearn-App")
        put("X-GitHub-Api-Version", "2022-11-28")
        if (source.token.isNotBlank()) put("Authorization", "Bearer ${source.token.trim()}")
    }

    private fun describe(code: Int, source: GitHubSource, action: String): String = when (code) {
        401 -> "$action ${source.displayName}: the token is invalid or expired"
        403 -> "$action ${source.displayName}: access denied (check the token's permissions or try again later)"
        404 -> "$action ${source.displayName}: repository or branch '${source.branch}' not found (private repositories need a token)"
        else -> "$action ${source.displayName}"
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun requireLooksLikeZip(file: File, sourceName: String) {
    val header = ByteArray(2)
    val read = file.inputStream().use { it.read(header) }
    if (read < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
        throw IOException("$sourceName did not return a zip file. Check that it is shared publicly or that the link is correct.")
    }
}
