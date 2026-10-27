package com.openagnes.app

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openagnes.app.data.AgnesClient
import com.openagnes.app.data.AppSettings
import com.openagnes.app.data.ChatMessage
import com.openagnes.app.data.ChatStore
import com.openagnes.app.data.Conversation
import com.openagnes.app.data.SettingsStore
import com.openagnes.app.data.StoredMessage
import com.openagnes.app.data.chatContentOf
import com.openagnes.app.data.plainContentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatStreamState(
    val streaming: Boolean = false,
    val answer: String = "",
    val reasoning: String = "",
    val error: String? = null,
)

data class ImageJobState(
    val running: Boolean = false,
    val statusText: String = "",
    val error: String? = null,
    val prompt: String = "",
    val imageUrl: String? = null,
    val imageB64: String? = null,
)

data class VideoJobState(
    val running: Boolean = false,
    val statusText: String = "",
    val error: String? = null,
    val prompt: String = "",
    val videoUrl: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val chatStore = ChatStore(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _conversations = MutableStateFlow(chatStore.load().sortedByDescending { it.createdAt })
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _stream = MutableStateFlow(ChatStreamState())
    val stream: StateFlow<ChatStreamState> = _stream.asStateFlow()

    private val _imageJob = MutableStateFlow(ImageJobState())
    val imageJob: StateFlow<ImageJobState> = _imageJob.asStateFlow()

    private val _videoJob = MutableStateFlow(VideoJobState())
    val videoJob: StateFlow<VideoJobState> = _videoJob.asStateFlow()

    private var cachedClient: Pair<Pair<String, String>, AgnesClient>? = null

    private fun client(): AgnesClient {
        val current = _settings.value
        val key = current.baseUrl to current.apiKey
        cachedClient?.let { if (it.first == key) return it.second }
        return AgnesClient(current.baseUrl, current.apiKey).also { cachedClient = key to it }
    }

    // ------------------------------------------------------------- settings

    fun updateSettings(next: AppSettings) {
        settingsStore.save(next)
        _settings.value = next
    }

    // -------------------------------------------------------- conversations

    private fun persist() {
        chatStore.save(_conversations.value)
    }

    private fun replace(conversation: Conversation) {
        _conversations.value = _conversations.value.map {
            if (it.id == conversation.id) conversation else it
        }
        persist()
    }

    fun newConversation(): String {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = "Neuer Chat",
            model = _settings.value.textModel,
            createdAt = System.currentTimeMillis(),
        )
        _conversations.value = listOf(conversation) + _conversations.value
        persist()
        _activeId.value = conversation.id
        return conversation.id
    }

    fun openConversation(id: String) {
        _activeId.value = id
        _stream.value = ChatStreamState()
    }

    fun closeConversation() {
        _activeId.value = null
        _stream.value = ChatStreamState()
    }

    fun deleteConversation(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        if (_activeId.value == id) _activeId.value = null
        persist()
    }

    fun setConversationModel(id: String, model: String) {
        _conversations.value.firstOrNull { it.id == id }?.let { replace(it.copy(model = model)) }
    }

    fun conversation(id: String?): Conversation? =
        id?.let { active -> _conversations.value.firstOrNull { it.id == active } }

    // ------------------------------------------------------------ image i/o

    /** Copies a picked image into app storage so it survives permission changes. */
    fun importImage(uri: Uri): String? = runCatching {
        val dir = File(getApplication<Application>().filesDir, "images").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    }.getOrNull()

    private suspend fun readAsDataUrl(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null
            "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    // ----------------------------------------------------------------- chat

    fun sendMessage(text: String, imagePath: String?) {
        val active = conversation(_activeId.value) ?: return
        if (_stream.value.streaming) return
        if (text.isBlank() && imagePath == null) return

        val userMessage = StoredMessage(role = "user", text = text, imagePath = imagePath)
        val withUser = active.copy(
            title = if (active.messages.isEmpty()) {
                text.take(40).ifBlank { "Bild" }
            } else {
                active.title
            },
            messages = active.messages + userMessage,
        )
        replace(withUser)
        _stream.value = ChatStreamState(streaming = true)

        viewModelScope.launch {
            val api = client()
            try {
                val payload = withUser.messages.map { message ->
                    val dataUrl = message.imagePath?.let { readAsDataUrl(it) }
                    if (dataUrl != null) {
                        ChatMessage(message.role, chatContentOf(message.text, dataUrl))
                    } else {
                        ChatMessage(message.role, plainContentOf(message.text))
                    }
                }

                val answer = api.streamChat(withUser.model, payload) { delta ->
                    _stream.update {
                        it.copy(answer = it.answer + delta.content, reasoning = it.reasoning + delta.reasoning)
                    }
                }

                val produced = _stream.value
                replace(
                    withUser.copy(
                        messages = withUser.messages + StoredMessage(
                            role = "assistant",
                            text = answer,
                            reasoning = produced.reasoning,
                        ),
                    ),
                )
                _stream.value = ChatStreamState()
            } catch (e: Exception) {
                val partial = _stream.value
                if (partial.answer.isNotBlank()) {
                    replace(
                        withUser.copy(
                            messages = withUser.messages + StoredMessage(
                                role = "assistant",
                                text = partial.answer,
                                reasoning = partial.reasoning,
                            ),
                        ),
                    )
                }
                _stream.value = ChatStreamState(
                    streaming = false,
                    error = e.message ?: "Unbekannter Fehler",
                )
            }
        }
    }

    // ---------------------------------------------------------------- image

    fun generateImage(prompt: String, size: String) {
        if (_imageJob.value.running) return
        _imageJob.value = ImageJobState(running = true, statusText = "Bild wird erzeugt…", prompt = prompt)

        viewModelScope.launch {
            try {
                val result = client().generateImage(_settings.value.imageModel, prompt, size)
                _imageJob.value = ImageJobState(
                    prompt = prompt,
                    imageUrl = result.url,
                    imageB64 = result.b64Json,
                    statusText = "Fertig",
                )
            } catch (e: Exception) {
                _imageJob.value = ImageJobState(
                    prompt = prompt,
                    error = e.message ?: "Unbekannter Fehler",
                )
            }
        }
    }

    // ---------------------------------------------------------------- video

    /**
     * Creates a video task and polls until it finishes. The gateway returns HTTP 503
     * with `video_queue_full` while its render queue is saturated, so creation is
     * retried a few times before giving up.
     */
    fun generateVideo(prompt: String, mode: String) {
        if (_videoJob.value.running) return
        _videoJob.value = VideoJobState(running = true, statusText = "Aufgabe wird erstellt…", prompt = prompt)

        viewModelScope.launch {
            try {
                var videoId: String? = null
                var lastError: String? = null
                for (attempt in 1..3) {
                    try {
                        videoId = client().createVideo(_settings.value.videoModel, prompt, mode)
                        break
                    } catch (e: Exception) {
                        lastError = e.message
                        if (e.message?.contains("queue", ignoreCase = true) == true && attempt < 3) {
                            _videoJob.update {
                                it.copy(statusText = "Warteschlange voll – neuer Versuch ($attempt/3)…")
                            }
                            delay(10_000)
                        } else {
                            throw e
                        }
                    }
                }

                if (videoId == null) throw IllegalStateException(lastError ?: "Keine video_id erhalten")

                _videoJob.update { it.copy(statusText = "In Warteschlange…") }

                repeat(120) {
                    delay(5_000)
                    val status = client().pollVideo(videoId!!)
                    _videoJob.update {
                        it.copy(statusText = "Status: ${status.status.ifBlank { "unbekannt" }}")
                    }
                    if (status.isTerminal) {
                        if (status.isSuccess && status.url != null) {
                            _videoJob.value = VideoJobState(
                                prompt = prompt,
                                videoUrl = status.url,
                                statusText = "Fertig",
                            )
                        } else {
                            _videoJob.value = VideoJobState(
                                prompt = prompt,
                                error = "Video fehlgeschlagen (${status.status}): ${status.raw.take(200)}",
                            )
                        }
                        return@launch
                    }
                }

                _videoJob.update {
                    it.copy(running = false, error = "Zeitüberschreitung beim Warten auf das Video")
                }
            } catch (e: Exception) {
                _videoJob.value = VideoJobState(
                    prompt = prompt,
                    error = e.message ?: "Unbekannter Fehler",
                )
            }
        }
    }
}
