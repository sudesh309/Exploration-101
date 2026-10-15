package com.kidlearn.content.parse

import com.kidlearn.domain.model.MediaRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Thrown for a structural problem that makes the current element unusable. */
class ManifestException(message: String) : Exception(message)

/** Turns a path written in a manifest into a playable/displayable [MediaRef]. */
fun interface MediaResolver {
    fun resolve(path: String): MediaRef
}

/**
 * Shared helpers for block/question parsers. Problems that do not prevent parsing (e.g. a
 * missing optional image) are collected as [warnings]; hard errors throw [ManifestException].
 */
class ParseContext(private val mediaResolver: MediaResolver) {
    private val _warnings = mutableListOf<String>()
    val warnings: List<String> get() = _warnings

    fun warn(message: String) {
        _warnings += message
    }

    fun media(path: String, where: String): MediaRef {
        if (path.isBlank()) throw ManifestException("$where: empty media path")
        return mediaResolver.resolve(path)
    }

    fun JsonObject.string(key: String, where: String): String =
        optString(key) ?: throw ManifestException("$where: missing text field '$key'")

    fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    fun JsonObject.optInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    fun JsonObject.optDouble(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    fun JsonObject.optBoolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    fun JsonObject.array(key: String, where: String): JsonArray =
        this[key] as? JsonArray ?: throw ManifestException("$where: missing list '$key'")

    fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

    fun JsonObject.optMedia(key: String, where: String): MediaRef? = optString(key)?.let { media(it, "$where.$key") }

    fun JsonElement.asObject(where: String): JsonObject =
        this as? JsonObject ?: throw ManifestException("$where: expected an object")
}
