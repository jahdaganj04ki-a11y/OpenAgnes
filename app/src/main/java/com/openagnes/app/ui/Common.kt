package com.openagnes.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val imageHttp = OkHttpClient()

/**
 * Renders an image from a remote URL, a `data:` URI, a base64 payload or a local path.
 * Deliberately dependency-free instead of pulling in an image loading library.
 */
@Composable
fun AsyncBitmap(
    source: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        bitmap = null
        failed = false
        if (source.isNullOrBlank()) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { decodeImage(source) }
        if (decoded != null) bitmap = decoded else failed = true
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )

        failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("Bild nicht ladbar", style = MaterialTheme.typography.bodySmall)
        }

        else -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

private fun decodeImage(source: String): Bitmap? = runCatching {
    when {
        source.startsWith("data:") -> decodeBase64(source)
        source.startsWith("http") -> decodeHttp(source)
        else -> BitmapFactory.decodeFile(source)
    }
}.getOrNull()

private fun decodeBase64(source: String): Bitmap? {
    val comma = source.indexOf(',')
    if (comma < 0) return null
    val bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun decodeHttp(url: String): Bitmap? {
    val request = Request.Builder().url(url).build()
    return imageHttp.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            null
        } else {
            response.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }
}

/** A labelled dropdown used for model and size selection. */
@Composable
fun PickerButton(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $selected", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    text: String,
    reasoning: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!isUser && reasoning.isNotBlank()) {
            Text(
                text = reasoning.trim(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (text.isNotBlank()) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun StatusRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
