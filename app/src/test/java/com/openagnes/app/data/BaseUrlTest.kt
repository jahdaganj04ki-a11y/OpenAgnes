package com.openagnes.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test: the gateway base URL is commonly written both with and without the
 * trailing `/v1`. Appending `/v1/chat/completions` unconditionally produced
 * `POST /v1/v1/chat/completions` and a 404, so normalisation is pinned here.
 */
class BaseUrlTest {

    @Test
    fun `base url with trailing v1 is not duplicated`() {
        val client = AgnesClient("https://apihub.agnes-ai.com/v1", "k")
        assertEquals("https://apihub.agnes-ai.com/v1", client.resolvedApiBase())
        assertEquals("https://apihub.agnes-ai.com", client.resolvedRoot())
    }

    @Test
    fun `bare host gains the v1 segment`() {
        val client = AgnesClient("https://apihub.agnes-ai.com", "k")
        assertEquals("https://apihub.agnes-ai.com/v1", client.resolvedApiBase())
        assertEquals("https://apihub.agnes-ai.com", client.resolvedRoot())
    }

    @Test
    fun `trailing slash and whitespace are tolerated`() {
        val client = AgnesClient("  https://apihub.agnes-ai.com/v1/  ", "k")
        assertEquals("https://apihub.agnes-ai.com/v1", client.resolvedApiBase())
        assertEquals("https://apihub.agnes-ai.com", client.resolvedRoot())
    }
}
