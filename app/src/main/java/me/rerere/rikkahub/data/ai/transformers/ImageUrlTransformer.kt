package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

private val IMAGE_URL_REGEX = Regex(
    pattern = """https?://[^\s<>()\]]+\.(?:png|jpe?g|webp|gif)(?:\?[^\s<>()\]]*)?""",
    option = RegexOption.IGNORE_CASE
)

object ImageUrlTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { it.inlineImageUrlsAsParts() }
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { it.inlineImageUrlsAsParts() }
    }
}

internal fun UIMessage.inlineImageUrlsAsParts(): UIMessage {
    if (role != MessageRole.ASSISTANT) {
        return this
    }

    val existingImageUrls = parts
        .filterIsInstance<UIMessagePart.Image>()
        .map { it.url }
        .toSet()

    var changed = false
    val nextParts = parts.flatMap { part ->
        when (part) {
            is UIMessagePart.Text -> {
                val matches = IMAGE_URL_REGEX.findAll(part.text).toList()
                if (matches.isEmpty()) {
                    listOf(part)
                } else {
                    changed = true
                    buildList {
                        var cursor = 0
                        matches.forEach { match ->
                            val before = part.text.substring(cursor, match.range.first)
                            if (before.isNotBlank()) {
                                add(part.copy(text = before))
                            }

                            val url = match.value
                            if (url !in existingImageUrls) {
                                add(UIMessagePart.Image(url))
                            }
                            cursor = match.range.last + 1
                        }

                        val after = part.text.substring(cursor)
                        if (after.isNotBlank()) {
                            add(part.copy(text = after))
                        }
                    }
                }
            }

            else -> listOf(part)
        }
    }

    return if (changed) {
        copy(parts = nextParts)
    } else {
        this
    }
}
