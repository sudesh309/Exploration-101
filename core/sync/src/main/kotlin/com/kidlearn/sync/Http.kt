package com.kidlearn.sync

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

class HttpResponse(val code: Int, private val stream: InputStream, private val onClose: () -> Unit = {}) : Closeable {
    val isSuccess: Boolean get() = code in 200..299

    fun text(): String = stream.bufferedReader().use { it.readText() }

    /** Streams the body to [file] with a fixed-size buffer (constant memory for large downloads). */
    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        stream.use { input -> file.outputStream().use { input.copyTo(it, 64 * 1024) } }
    }

    override fun close() {
        stream.close()
        onClose()
    }
}

/** Minimal blocking HTTP abstraction so the sync logic can be tested without a network. */
fun interface HttpClient {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}

/** [HttpClient] on top of the platform's HttpURLConnection (no extra library needed on Android). */
class UrlConnectionHttpClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) : HttpClient {
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        request.body?.let { body ->
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = (if (code >= 400) connection.errorStream else connection.inputStream) ?: ByteArrayInputStream(ByteArray(0))
        return HttpResponse(code, stream) { connection.disconnect() }
    }
}

internal fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

internal fun encodePath(path: String): String = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { urlEncode(it) }

internal fun HttpResponse.failure(what: String): IOException {
    val detail = runCatching { text().take(200) }.getOrDefault("")
    return IOException("$what failed (HTTP $code)${if (detail.isNotBlank()) ": $detail" else ""}")
}
