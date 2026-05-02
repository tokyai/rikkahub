package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUrlOutputTransformerTest {
    @Test
    fun `appendImagePartsForImageUrls adds image part for grok file url`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text(
                    "done https://grok.ioll.pp.ua/v1/files/image?id=b9f4bd47-e750-41cc-b5e2-e6deeeb887a8"
                )
            )
        )

        val transformed = appendImagePartsForImageUrls(message)

        assertEquals(2, transformed.parts.size)
        assertTrue(transformed.parts[0] is UIMessagePart.Text)
        assertEquals(
            "https://grok.ioll.pp.ua/v1/files/image?id=b9f4bd47-e750-41cc-b5e2-e6deeeb887a8",
            (transformed.parts[1] as UIMessagePart.Image).url
        )
    }
}
