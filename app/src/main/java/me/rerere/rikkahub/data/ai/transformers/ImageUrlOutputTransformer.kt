package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

private val GROK_FILE_IMAGE_URL_REGEX = Regex(
    "https?://[^\\s<>\"'`]+/v1/files/image\\?id=[A-Za-z0-9._~-]+"
)

private val COMMON_IMAGE_URL_REGEX = Regex(
    "https?://[^\\s<>\"'`]+?\\.(?:png|jpe?g|webp|gif)(?:\\?[^\\s<>\"'`]*)?",
    RegexOption.IGNORE_CASE
)

object ImageUrlOutputTransformer : OutputMessageTransformer {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map(::appendImagePartsForImageUrls)
    }
}

internal fun appendImagePartsForImageUrls(message: UIMessage): UIMessage {
    if (message.role != MessageRole.ASSISTANT) return message

    val existingImageUrls = message.parts
        .filterIsInstance<UIMessagePart.Image>()
        .mapTo(mutableSetOf()) { it.url }

    val imageUrls = message.parts
        .filterIsInstance<UIMessagePart.Text>()
        .flatMap { part -> extractImageUrls(part.text) }
        .distinct()
        .filterNot { it in existingImageUrls }

    if (imageUrls.isEmpty()) return message

    return message.copy(
        parts = message.parts + imageUrls.map { UIMessagePart.Image(url = it) }
    )
}

private fun extractImageUrls(text: String): List<String> {
    return (GROK_FILE_IMAGE_URL_REGEX.findAll(text) + COMMON_IMAGE_URL_REGEX.findAll(text))
        .map { it.value.trimEnd('.', ',', ';', ':', '!', ')', ']', '}') }
        .toList()
}
