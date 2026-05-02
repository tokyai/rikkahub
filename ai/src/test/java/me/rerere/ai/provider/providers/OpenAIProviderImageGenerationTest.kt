package me.rerere.ai.provider.providers

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIProviderImageGenerationTest {
    @Test
    fun `parseImageGenerationItems supports url responses`() = runBlocking {
        val data = buildJsonArray {
            add(
                buildJsonObject {
                    put("url", "https://example.com/generated.png")
                }
            )
        }

        val items = parseImageGenerationItems(data) { url ->
            assertEquals("https://example.com/generated.png", url)
            "downloaded-base64"
        }

        assertEquals(1, items.size)
        assertEquals("downloaded-base64", items.single().data)
        assertEquals("image/png", items.single().mimeType)
    }
}
