package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUrlTransformerTest {
    @Test
    fun `assistant image url should become image part and be removed from text`() {
        val url =
            "https://assets.grok.com/users/user-id/generated/image-id/image.jpg"
        val message = UIMessage.assistant("生成好了：$url\n")

        val transformed = message.inlineImageUrlsAsParts()

        assertEquals(2, transformed.parts.size)
        assertEquals("生成好了：", (transformed.parts[0] as UIMessagePart.Text).text)
        assertEquals(url, (transformed.parts[1] as UIMessagePart.Image).url)
    }

    @Test
    fun `non image urls should stay as text`() {
        val url = "https://example.com/page"
        val message = UIMessage.assistant("参考：$url")

        val transformed = message.inlineImageUrlsAsParts()

        assertEquals(listOf(UIMessagePart.Text("参考：$url")), transformed.parts)
    }

    @Test
    fun `existing image parts should not be duplicated`() {
        val url = "https://assets.grok.com/users/user-id/generated/image-id/image.jpg"
        val message = UIMessage.assistant("生成好了：$url").copy(
            parts = listOf(
                UIMessagePart.Text("生成好了："),
                UIMessagePart.Image(url)
            )
        )

        val transformed = message.inlineImageUrlsAsParts()

        assertEquals(2, transformed.parts.size)
        assertTrue(transformed.parts[1] is UIMessagePart.Image)
    }
}
