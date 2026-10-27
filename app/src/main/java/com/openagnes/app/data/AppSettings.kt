package com.openagnes.app.data

import android.content.Context
import com.openagnes.app.BuildConfig

/**
 * Effective runtime configuration.
 *
 * The API key baked in at build time comes from the git-ignored `.env`. Because
 * shipping a key inside an APK is inherently insecure, Settings lets it be replaced
 * at runtime without rebuilding.
 */
data class AppSettings(
    val baseUrl: String,
    val apiKey: String,
    val textModel: String,
    val imageModel: String,
    val videoModel: String,
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("openagnes.settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.AGNES_BASE_URL,
        apiKey = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.AGNES_API_KEY,
        textModel = prefs.getString(KEY_TEXT_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: AgnesCatalog.DEFAULT_TEXT_MODEL,
        imageModel = prefs.getString(KEY_IMAGE_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: AgnesCatalog.DEFAULT_IMAGE_MODEL,
        videoModel = prefs.getString(KEY_VIDEO_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: AgnesCatalog.DEFAULT_VIDEO_MODEL,
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_TEXT_MODEL, settings.textModel)
            .putString(KEY_IMAGE_MODEL, settings.imageModel)
            .putString(KEY_VIDEO_MODEL, settings.videoModel)
            .apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_TEXT_MODEL = "text_model"
        const val KEY_IMAGE_MODEL = "image_model"
        const val KEY_VIDEO_MODEL = "video_model"
    }
}
