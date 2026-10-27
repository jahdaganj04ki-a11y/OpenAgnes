package com.openagnes.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredMessage(
    val role: String,
    val text: String,
    val reasoning: String = "",
    /** Local file holding an image the user attached (kept so it survives restarts). */
    val imagePath: String? = null,
    /** Remote URL of an image produced by the model. */
    val imageUrl: String? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val model: String,
    val createdAt: Long,
    val messages: List<StoredMessage> = emptyList(),
)

/** Conversations are small; a single JSON file is enough and keeps the app dependency-free. */
class ChatStore(context: Context) {

    private val file = File(context.filesDir, "conversations.json")
    private val serializer = ListSerializer(Conversation.serializer())
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun load(): List<Conversation> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(serializer, file.readText())
    }.getOrDefault(emptyList())

    fun save(conversations: List<Conversation>) {
        runCatching { file.writeText(json.encodeToString(serializer, conversations)) }
    }
}
