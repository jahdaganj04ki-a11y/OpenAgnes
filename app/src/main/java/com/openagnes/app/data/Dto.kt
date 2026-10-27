package com.openagnes.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `content` is deliberately a raw [JsonElement]: the gateway accepts either a plain
 * string (text-only turns) or an array of content parts (text + image for vision).
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

/** One streamed chunk: the visible answer text and, separately, the model's reasoning. */
data class ChatDelta(val content: String, val reasoning: String)

data class ImageResult(val url: String?, val b64Json: String?, val taskId: String?)

data class VideoStatus(val status: String, val url: String?, val raw: String) {
    val isTerminal: Boolean
        get() = status.lowercase() in setOf("succeeded", "success", "completed", "done", "failed", "error", "cancelled")

    val isSuccess: Boolean
        get() = status.lowercase() in setOf("succeeded", "success", "completed", "done")
}

class AgnesException(message: String) : Exception(message)
