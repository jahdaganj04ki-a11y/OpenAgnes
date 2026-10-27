package com.openagnes.app.data

/**
 * Models and parameters available on the Agnes AI gateway.
 *
 * Verified against `GET /v1/models` and by probing the live endpoints:
 * - chat: `POST /v1/chat/completions` (OpenAI compatible, SSE when `stream=true`)
 * - image: `POST /v1/images/generations`
 * - video: `POST /v1/videos` with a required `mode`, polled via `GET /agnesapi?video_id=`
 */
object AgnesCatalog {

    /** Text and vision-language models. */
    val textModels = listOf(
        "agnes-3.0-flash",
        "agnes-2.5-flash",
        "agnes-2.0-flash",
        "agnes-2.5-pro-alpha",
        "agnes-2.5-pro-beta",
    )

    /** Image generation and editing models. */
    val imageModels = listOf(
        "agnes-image-2.5-flash",
        "agnes-image-2.1-flash",
        "agnes-image-2.0-flash",
    )

    /** Video generation models. */
    val videoModels = listOf(
        "agnes-video-2.5-flash",
        "agnes-video-2.5",
    )

    val imageSizes = listOf("1024x1024", "1152x768", "768x1152", "512x512")

    /**
     * The gateway rejects unknown video modes. Confirmed accepted values are
     * `text` (prompt only), `keyframe` (needs first/last frame) and
     * `reference` (needs reference media). Only `text` is offered here.
     */
    val videoModes = listOf("text")

    const val DEFAULT_TEXT_MODEL = "agnes-2.5-flash"
    const val DEFAULT_IMAGE_MODEL = "agnes-image-2.1-flash"
    const val DEFAULT_VIDEO_MODEL = "agnes-video-2.5-flash"
}
