package com.openagnes.app.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openagnes.app.AppViewModel
import com.openagnes.app.data.AgnesCatalog

@Composable
fun VideoScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val job by vm.videoJob.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AgnesCatalog.videoModes.first()) }
    var model by remember(settings.videoModel) { mutableStateOf(settings.videoModel) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video erzeugen", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerButton(
                label = "Modell",
                options = AgnesCatalog.videoModels,
                selected = model,
                onSelected = {
                    model = it
                    vm.updateSettings(settings.copy(videoModel = it))
                },
            )
            PickerButton(
                label = "Modus",
                options = AgnesCatalog.videoModes,
                selected = mode,
                onSelected = { mode = it },
            )
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Button(
            onClick = { vm.generateVideo(prompt.trim(), mode) },
            enabled = !job.running && prompt.isNotBlank(),
        ) {
            Text(if (job.running) "Läuft…" else "Video erzeugen")
        }

        if (job.running) StatusRow(job.statusText)
        job.error?.let { ErrorText(it) }

        job.videoUrl?.let { url ->
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(url))
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
            Text(
                text = "Videolinks sind temporär. Bei Bedarf sofort ansehen oder herunterladen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = "Hinweis: Videogenerierung ist serverseitig begrenzt. Meldet der Dienst " +
                "„video queue is full“, wird automatisch bis zu dreimal erneut versucht.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
