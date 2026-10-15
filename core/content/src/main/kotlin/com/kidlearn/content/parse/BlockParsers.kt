package com.kidlearn.content.parse

import com.kidlearn.domain.model.AudioBlock
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.ImageBlock
import com.kidlearn.domain.model.QuizBlock
import com.kidlearn.domain.model.TextBlock
import com.kidlearn.domain.model.TextStyle
import com.kidlearn.domain.model.VideoBlock
import kotlinx.serialization.json.JsonObject

/** Parses one `type` of lesson block. Register new implementations to support new block types. */
interface BlockParser {
    val type: String
    fun parse(json: JsonObject, context: ParseContext, where: String): ContentBlock
}

object TextBlockParser : BlockParser {
    override val type = TextBlock.TYPE

    override fun parse(json: JsonObject, context: ParseContext, where: String) = with(context) {
        val style = when (json.optString("style")?.lowercase()) {
            null, "body" -> TextStyle.BODY
            "heading" -> TextStyle.HEADING
            "fun_fact", "funfact" -> TextStyle.FUN_FACT
            else -> TextStyle.BODY.also { warn("$where: unknown text style, using body") }
        }
        TextBlock(json.string("text", where), style)
    }
}

object ImageBlockParser : BlockParser {
    override val type = ImageBlock.TYPE

    override fun parse(json: JsonObject, context: ParseContext, where: String) = with(context) {
        ImageBlock(media(json.string("src", where), where), json.optString("caption"), json.optString("alt"))
    }
}

object AudioBlockParser : BlockParser {
    override val type = AudioBlock.TYPE

    override fun parse(json: JsonObject, context: ParseContext, where: String) = with(context) {
        AudioBlock(media(json.string("src", where), where), json.optString("title"), json.optString("transcript"))
    }
}

object VideoBlockParser : BlockParser {
    override val type = VideoBlock.TYPE

    override fun parse(json: JsonObject, context: ParseContext, where: String) = with(context) {
        VideoBlock(media(json.string("src", where), where), json.optString("title"), json.optMedia("poster", where))
    }
}

class QuizBlockParser(private val questions: QuestionParserRegistry) : BlockParser {
    override val type = QuizBlock.TYPE

    override fun parse(json: JsonObject, context: ParseContext, where: String): ContentBlock = with(context) {
        QuizBlock(QuizParser(questions).parse(json, context, where))
    }
}

class BlockParserRegistry(parsers: Iterable<BlockParser>) {
    private val byType: Map<String, BlockParser> = parsers.associateBy { it.type }

    fun find(type: String): BlockParser? = byType[type]

    fun plus(parser: BlockParser) = BlockParserRegistry(byType.values + parser)

    companion object {
        fun default(questions: QuestionParserRegistry = QuestionParserRegistry.default()) = BlockParserRegistry(
            listOf(TextBlockParser, ImageBlockParser, AudioBlockParser, VideoBlockParser, QuizBlockParser(questions)),
        )
    }
}
