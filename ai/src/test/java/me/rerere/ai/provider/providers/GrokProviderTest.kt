package me.rerere.ai.provider.providers

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrokProviderTest {
    @Test
    fun `listModels should read xAI language and image model capabilities`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val body = when (request.url.encodedPath) {
                    "/v1/language-models" -> """
                        {
                          "models": [
                            {
                              "id": "grok-4.20",
                              "input_modalities": ["text", "image"],
                              "output_modalities": ["text"]
                            }
                          ]
                        }
                    """.trimIndent()

                    "/v1/image-generation-models" -> """
                        {
                          "models": [
                            {
                              "id": "grok-2-image",
                              "input_modalities": ["text"],
                              "output_modalities": ["image"]
                            }
                          ]
                        }
                    """.trimIndent()

                    else -> error("Unexpected path: ${request.url.encodedPath}")
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val provider = GrokProvider(client)

        val models = provider.listModels(
            ProviderSetting.Grok(
                apiKey = "xai-key",
                baseUrl = "https://api.x.ai/v1"
            )
        )

        val chatModel = models.single { it.modelId == "grok-4.20" }
        assertEquals(ModelType.CHAT, chatModel.type)
        assertEquals(listOf(Modality.TEXT, Modality.IMAGE), chatModel.inputModalities)
        assertEquals(listOf(Modality.TEXT), chatModel.outputModalities)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), chatModel.abilities)

        val imageModel = models.single { it.modelId == "grok-2-image" }
        assertEquals(ModelType.IMAGE, imageModel.type)
        assertEquals(listOf(Modality.TEXT), imageModel.inputModalities)
        assertEquals(listOf(Modality.IMAGE), imageModel.outputModalities)
    }

    @Test
    fun `listModels should keep language models when image model endpoint is unavailable`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val code = if (request.url.encodedPath == "/v1/language-models") 200 else 404
                val body = if (code == 200) {
                    """{"models":[{"id":"grok-4.20-reasoning","input_modalities":["text"],"output_modalities":["text"]}]}"""
                } else {
                    """{"error":{"message":"not available"}}"""
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Not Found")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val provider = GrokProvider(client)

        val models = provider.listModels(ProviderSetting.Grok(apiKey = "xai-key"))

        assertEquals(listOf("grok-4.20-reasoning"), models.map { it.modelId })
        assertTrue(models.single().abilities.contains(ModelAbility.REASONING))
    }
}
