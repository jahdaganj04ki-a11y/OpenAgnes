package com.openagnes.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real gateway end to end. Skipped automatically when no key is
 * available, so the suite stays green offline.
 */
class AgnesClientLiveTest {

    private val apiKey: String = System.getenv("AGNES_API_KEY").orEmpty()
    private val baseUrl: String = System.getenv("AGNES_BASE_URL") ?: "https://apihub.agnes-ai.com/v1"

    @Before
    fun requireKey() {
        assumeTrue("AGNES_API_KEY ist nicht gesetzt – Live-Tests übersprungen.", apiKey.isNotBlank())
    }

    @Test
    fun `streams a real chat completion`() = runBlocking {
        val client = AgnesClient(baseUrl, apiKey)
        val deltas = mutableListOf<ChatDelta>()

        val answer = client.streamChat(
            model = "agnes-2.5-flash",
            messages = listOf(ChatMessage("user", plainContentOf("Antworte mit genau einem Wort: HALLO"))),
            maxTokens = 1024,
        ) { deltas += it }

        println("streamChat deltas=${deltas.size} answer='$answer'")
        println("reasoning='${deltas.joinToString("") { it.reasoning }}'")

        assertTrue("Es kamen keine Stream-Chunks an", deltas.isNotEmpty())
        assertTrue(
            "Weder Antwort noch Reasoning wurden gestreamt",
            answer.isNotBlank() || deltas.any { it.reasoning.isNotBlank() },
        )
    }

    @Test
    fun `streams image input through the vision path`() = runBlocking {
        val client = AgnesClient(baseUrl, apiKey)
        // 1x1 red pixel PNG as a data URL – proves the content-parts shape is accepted.
        val pixel =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val content = chatContentOf("Welche Farbe hat das Bild? Ein Wort.", "data:image/png;base64,$pixel")

        val answer = client.streamChat(
            model = "agnes-2.5-flash",
            messages = listOf(ChatMessage("user", content)),
            maxTokens = 1024,
        ) { }

        println("vision answer='$answer'")
        assertTrue(answer.isNotBlank())
    }

    @Test
    fun `reports an unreadable key as an http error`() = runBlocking {
        val client = AgnesClient(baseUrl, "sk-definitely-not-a-valid-key")
        val failure = try {
            client.streamChat("agnes-2.5-flash", listOf(ChatMessage("user", plainContentOf("hi")))) { }
            null
        } catch (e: Exception) {
            e
        }

        println("invalid key error: ${failure?.message}")
        assertNotNull("Ein ungültiger Key muss einen Fehler auslösen", failure)
        assertTrue(
            "Fehlermeldung sollte den HTTP-Status nennen, war: ${failure?.message}",
            failure?.message?.startsWith("HTTP ") == true,
        )
    }

    @Test
    fun `generates a real image`() = runBlocking {
        val client = AgnesClient(baseUrl, apiKey)
        val result = client.generateImage(
            model = "agnes-image-2.1-flash",
            prompt = "a single red circle centered on a plain white background",
            size = "512x512",
        )

        println("image url=${result.url}")
        assertTrue(
            "Weder URL noch Base64-Bilddaten geliefert",
            !result.url.isNullOrBlank() || !result.b64Json.isNullOrBlank(),
        )
    }

    @Test
    fun `video mode text is accepted by the gateway`() = runBlocking {
        val client = AgnesClient(baseUrl, apiKey)
        val outcome = try {
            "task=${client.createVideo("agnes-video-2.5-flash", "a calm ocean wave", "text")}"
        } catch (e: Exception) {
            "error=${e.message}"
        }

        println("video create: $outcome")
        // The render queue is frequently saturated (HTTP 503 video_queue_full), which is a
        // service condition, not a client bug. Either outcome is acceptable; what must not
        // happen is a parameter validation error such as the earlier "mode is required".
        assertTrue(
            "Unerwarteter Fehler beim Anlegen der Video-Aufgabe: $outcome",
            outcome.startsWith("task=") ||
                outcome.contains("queue", ignoreCase = true) ||
                outcome.contains("capacity", ignoreCase = true) ||
                outcome.contains("unavailable", ignoreCase = true),
        )
    }
}
