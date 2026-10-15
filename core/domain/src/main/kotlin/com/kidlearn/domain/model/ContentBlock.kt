package com.kidlearn.domain.model

/**
 * One piece of lesson content. This is deliberately an open interface rather than a sealed
 * hierarchy: a new kind of block (e.g. an interactive simulation) is added by declaring a new
 * implementation, a parser for it, and a renderer for it - without touching existing code.
 */
interface ContentBlock {
    /** Stable identifier matching the `type` field used in course manifests. */
    val type: String
}

enum class TextStyle { HEADING, BODY, FUN_FACT }

data class TextBlock(val text: String, val style: TextStyle = TextStyle.BODY) : ContentBlock {
    override val type: String get() = TYPE

    companion object {
        const val TYPE = "text"
    }
}

data class ImageBlock(val image: MediaRef, val caption: String?, val altText: String?) : ContentBlock {
    override val type: String get() = TYPE

    companion object {
        const val TYPE = "image"
    }
}

data class AudioBlock(val audio: MediaRef, val title: String?, val transcript: String?) : ContentBlock {
    override val type: String get() = TYPE

    companion object {
        const val TYPE = "audio"
    }
}

data class VideoBlock(val video: MediaRef, val title: String?, val poster: MediaRef?) : ContentBlock {
    override val type: String get() = TYPE

    companion object {
        const val TYPE = "video"
    }
}

data class QuizBlock(val quiz: Quiz) : ContentBlock {
    override val type: String get() = TYPE

    companion object {
        const val TYPE = "quiz"
    }
}

/** Placeholder for a block type this version of the app does not understand yet. */
data class UnsupportedBlock(val declaredType: String) : ContentBlock {
    override val type: String get() = declaredType
}
