package com.openagnes.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Thin client for the Agnes AI gateway (OpenAI-compatible).
 *
 * Every method performs a blocking OkHttp call but already hops to [Dispatchers.IO],
 * so callers may invoke them directly from a coroutine.
 */
class AgnesClient(baseUrl: String, private val apiKey: String) {

    /**
     * Accepts both `https://host` and `https://host/v1`, so a trailing `/v1` entered in
     * Settings cannot produce a doubled path such as `/v1/v1/chat/completions`.
     */
    private val apiBase = baseUrl.trim().trimEnd('/')
        .let { if (it.endsWith("/v1")) it else "$it/v1" }

    /** Video polling lives at the API root, not under `/v1`. */
    private val root = apiBase.removeSuffix("/v1")

    /** Exposed for tests so URL normalisation stays regression-covered. */
    internal fun resolvedApiBase(): String = apiBase

    internal fun resolvedRoot(): String = root

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMedia = "application/json".toMediaType()

    private fun newRequest(path: String): Request.Builder =
        Request.Builder()
            .url("$apiBase$path")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

    // ---------------------------------------------------------------- chat

    /**
     * Streams a chat completion. [onDelta] receives the incremental answer text and
     * the incremental reasoning text separately, and returns the full answer.
     */
    suspend fun streamChat(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int? = null,
        onDelta: (ChatDelta) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model, messages = messages, stream = true, maxTokens = maxTokens),
        )

        val request = newRequest("/chat/completions")
            .post(payload.toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AgnesException(readError(response))

            val source = response.body?.source() ?: throw AgnesException("Leere Antwort vom Server")
            val answer = StringBuilder()

            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue

                val payloadLine = line.removePrefix("data:").trim()
                if (payloadLine.isEmpty()) continue
                if (payloadLine == "[DONE]") break

                val delta = parseChatDelta(payloadLine, json) ?: continue
                if (delta.content.isNotEmpty()) answer.append(delta.content)
                onDelta(delta)
            }

            answer.toString()
        }
    }

    // --------------------------------------------------------------- image

    suspend fun generateImage(model: String, prompt: String, size: String?): ImageResult =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("model", model)
                put("prompt", prompt)
                if (!size.isNullOrBlank()) put("size", size)
            }

            val request = newRequest("/images/generations")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AgnesException(parseError(text, response.code))

                val obj = json.parseToJsonElement(text).asObject()
                    ?: throw AgnesException("Unerwartete Antwort: ${text.take(300)}")

                val first = obj["data"].asArray()?.firstOrNull().asObject()

                val result = ImageResult(
                    url = first?.get("url").asText()?.takeIf { it.isNotBlank() },
                    b64Json = first?.get("b64_json").asText()?.takeIf { it.isNotBlank() },
                    taskId = obj["task_id"].asText(),
                )
                if (result.url == null && result.b64Json == null) {
                    throw AgnesException("Keine Bilddaten in der Antwort: ${text.take(300)}")
                }
                result
            }
        }

    // --------------------------------------------------------------- video

    /** Creates an asynchronous video task and returns its `video_id`. */
    suspend fun createVideo(model: String, prompt: String, mode: String = "text"): String =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("model", model)
                put("prompt", prompt)
                put("mode", mode)
            }

            val request = newRequest("/videos")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AgnesException(parseError(text, response.code))

                val obj = json.parseToJsonElement(text).asObject()
                    ?: throw AgnesException("Unerwartete Antwort: ${text.take(300)}")

                obj["video_id"].asText()
                    ?: obj["id"].asText()
                    ?: obj["task_id"].asText()
                    ?: throw AgnesException("Keine video_id in der Antwort: ${text.take(300)}")
            }
        }

    /** Queries the status of a video task. */
    suspend fun pollVideo(videoId: String): VideoStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$root/agnesapi?video_id=$videoId")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw AgnesException(parseError(text, response.code))

            val obj = json.parseToJsonElement(text).asObject()
                ?: throw AgnesException("Unerwartete Antwort: ${text.take(300)}")

            val status = listOf("status", "state", "task_status")
                .firstNotNullOfOrNull { obj[it].asText() }
                .orEmpty()

            VideoStatus(status = status, url = findUrl(obj), raw = text)
        }
    }

    // ------------------------------------------------------------- helpers

    /** Finds a playable URL across the flat and nested shapes the gateway may return. */
    private fun findUrl(obj: JsonObject, depth: Int = 0): String? {
        for (key in URL_KEYS) {
            val value = obj[key].asText()
            if (!value.isNullOrBlank() && value.startsWith("http")) return value
        }
        if (depth >= 3) return null

        for (nestKey in listOf("data", "result", "output", "video")) {
            val child = obj[nestKey]
            child.asObject()?.let { nested -> findUrl(nested, depth + 1)?.let { found -> return found } }
            val array = child.asArray()
            if (array != null) {
                for (item in array) {
                    item.asObject()?.let { nested -> findUrl(nested, depth + 1)?.let { found -> return found } }
                }
            }
        }
        return null
    }

    private fun readError(response: Response): String {
        val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        return parseError(text, response.code)
    }

    private fun parseError(text: String, code: Int): String {
        val message = runCatching {
            val obj = json.parseToJsonElement(text).asObject()
            obj?.get("message").asText()
                ?: obj?.get("error").asObject()?.get("message").asText()
        }.getOrNull()

        return when {
            !message.isNullOrBlank() -> "HTTP $code: $message"
            text.isNotBlank() -> "HTTP $code: ${text.take(300)}"
            else -> "HTTP $code"
        }
    }

    private companion object {
        val URL_KEYS = listOf(
            "video_url", "videoUrl", "url", "output", "output_url",
            "download_url", "uri", "file_url", "result_url",
        )
    }
}

// ------------------------------------------------------------------ JSON helpers

/**
 * Parses a single `data:` payload of the chat SSE stream.
 *
 * Reasoning is streamed through `delta.reasoning_content` before any answer text
 * arrives, so both fields are surfaced separately. Returns null for keep-alive or
 * payload shapes that carry no delta.
 */
internal fun parseChatDelta(payload: String, json: Json): ChatDelta? {
    val chunk = runCatching { json.parseToJsonElement(payload) }.getOrNull().asObject() ?: return null
    val delta = chunk["choices"].asArray()
        ?.firstOrNull()
        .asObject()
        ?.get("delta")
        .asObject()
        ?: return null

    val content = delta["content"].asText().orEmpty()
    val reasoning = delta["reasoning_content"].asText().orEmpty()
    if (content.isEmpty() && reasoning.isEmpty()) return null
    return ChatDelta(content, reasoning)
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.asText(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
}

/** Builds the `content` field for a chat message, optionally with an inline image. */
fun chatContentOf(text: String, imageDataUrl: String?): JsonArray = buildJsonArray {
    add(buildJsonObject {
        put("type", "text")
        put("text", text)
    })
    if (imageDataUrl != null) {
        add(buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject { put("url", imageDataUrl) })
        })
    }
}

/** Builds a plain string `content` field. */
fun plainContentOf(text: String): JsonPrimitive = JsonPrimitive(text)
