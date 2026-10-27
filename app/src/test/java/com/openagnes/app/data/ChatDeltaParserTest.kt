package com.openagnes.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The payloads below were captured verbatim from the live gateway. Reasoning arrives
 * through `delta.reasoning_content` before any answer text, which is easy to get wrong.
 */
class ChatDeltaParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `role-only first chunk carries no delta`() {
        val payload =
            """{"id":"c3","object":"chat.completion.chunk","created":1793131974,"model":"agnes-2.5-flash","choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}"""
        assertNull(parseChatDelta(payload, json))
    }

    @Test
    fun `reasoning chunk is surfaced separately from the answer`() {
        val payload =
            """{"id":"c3","created":1793131974,"model":"agnes-2.5-flash","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"reasoning_content":"The"}}]}"""
        val delta = parseChatDelta(payload, json)
        assertEquals("", delta?.content)
        assertEquals("The", delta?.reasoning)
    }

    @Test
    fun `content chunk is surfaced as answer text`() {
        val payload = """{"choices":[{"index":0,"delta":{"content":"Hallo"}}]}"""
        val delta = parseChatDelta(payload, json)
        assertEquals("Hallo", delta?.content)
        assertEquals("", delta?.reasoning)
    }

    @Test
    fun `unknown fields and extra metadata do not break parsing`() {
        val payload =
            """{"provider_specific_fields":{"matched_stop":null},"choices":[{"index":0,"finish_reason":"stop","delta":{"content":"!"}}],"usage":{"total_tokens":12}}"""
        assertEquals("!", parseChatDelta(payload, json)?.content)
    }

    @Test
    fun `malformed json is ignored instead of aborting the stream`() {
        assertNull(parseChatDelta("{not json at all", json))
    }

    @Test
    fun `chunk without choices yields nothing`() {
        assertNull(parseChatDelta("""{"id":"c3","usage":{"total_tokens":5}}""", json))
    }
}
