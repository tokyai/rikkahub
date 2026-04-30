package me.rerere.ai.provider.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import me.rerere.common.http.await
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.uuid.Uuid

class GrokProvider(
    private val client: OkHttpClient,
    context: Context? = null
) : Provider<ProviderSetting.Grok> {
    private val keyRoulette = if (context != null) KeyRoulette.lru(context) else KeyRoulette.default()
    private val openAIProvider = OpenAIProvider(client, context)

    override suspend fun listModels(providerSetting: ProviderSetting.Grok): List<Model> =
        withContext(Dispatchers.IO) {
            val languageModels = fetchXaiModels(
                providerSetting = providerSetting,
                path = "language-models",
                type = ModelType.CHAT,
            )
            val imageModels = runCatching {
                fetchXaiModels(
                    providerSetting = providerSetting,
                    path = "image-generation-models",
                    type = ModelType.IMAGE,
                )
            }.getOrDefault(emptyList())

            (languageModels + imageModels).distinctBy { it.type to it.modelId }
        }

    override suspend fun generateText(
        providerSetting: ProviderSetting.Grok,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        return openAIProvider.generateText(providerSetting.toOpenAISetting(), messages, params)
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.Grok,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        return openAIProvider.streamText(providerSetting.toOpenAISetting(), messages, params)
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        require(providerSetting is ProviderSetting.Grok) {
            "Expected Grok provider setting"
        }
        return openAIProvider.generateImage(providerSetting.toOpenAISetting(), params)
    }

    private suspend fun fetchXaiModels(
        providerSetting: ProviderSetting.Grok,
        path: String,
        type: ModelType,
    ): List<Model> {
        val key = keyRoulette.next(providerSetting.apiKey, providerSetting.id.toString())
        val request = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/$path")
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to get Grok models: ${response.code} ${response.body.string()}")
        }

        val bodyStr = response.body.string()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val models = bodyJson["models"]?.jsonArray
            ?: bodyJson["data"]?.jsonArray
            ?: JsonArray(emptyList())

        return models.mapNotNull { modelJson ->
            val modelObj = modelJson.jsonObject
            val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Model(
                id = Uuid.random(),
                modelId = id,
                displayName = id,
                type = type,
                inputModalities = modelObj.parseModalities("input_modalities")
                    ?: ModelRegistry.MODEL_INPUT_MODALITIES.getData(id),
                outputModalities = modelObj.parseModalities("output_modalities")
                    ?: if (type == ModelType.IMAGE) listOf(Modality.IMAGE) else ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id),
                abilities = if (type == ModelType.CHAT) {
                    ModelRegistry.MODEL_ABILITIES.getData(id)
                } else {
                    emptyList()
                },
            )
        }
    }

    private fun JsonObject.parseModalities(key: String): List<Modality>? {
        val values = this[key]?.jsonArray ?: return null
        val modalities = values.mapNotNull { value ->
            when (value.jsonPrimitive.contentOrNull?.lowercase()) {
                "text" -> Modality.TEXT
                "image" -> Modality.IMAGE
                else -> null
            }
        }
        return modalities.ifEmpty { null }
    }

    private fun ProviderSetting.Grok.toOpenAISetting(): ProviderSetting.OpenAI {
        return ProviderSetting.OpenAI(
            id = id,
            enabled = enabled,
            name = name,
            models = models,
            balanceOption = balanceOption,
            builtIn = builtIn,
            description = description,
            shortDescription = shortDescription,
            apiKey = apiKey,
            baseUrl = baseUrl,
            chatCompletionsPath = chatCompletionsPath,
            useResponseApi = useResponseApi,
        )
    }
}
