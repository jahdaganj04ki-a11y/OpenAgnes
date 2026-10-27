package com.openagnes.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.openagnes.app.AppViewModel
import com.openagnes.app.data.AgnesCatalog

@Composable
fun ImageScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val job by vm.imageJob.collectAsState()

    var prompt by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(AgnesCatalog.imageSizes.first()) }
    var model by remember(settings.imageModel) { mutableStateOf(settings.imageModel) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bild erzeugen", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerButton(
                label = "Modell",
                options = AgnesCatalog.imageModels,
                selected = model,
                onSelected = {
                    model = it
                    vm.updateSettings(settings.copy(imageModel = it))
                },
            )
            PickerButton(
                label = "Größe",
                options = AgnesCatalog.imageSizes,
                selected = size,
                onSelected = { size = it },
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
            onClick = { vm.generateImage(prompt.trim(), size) },
            enabled = !job.running && prompt.isNotBlank(),
        ) {
            Text(if (job.running) "Erzeuge…" else "Erzeugen")
        }

        if (job.running) StatusRow(job.statusText)
        job.error?.let { ErrorText(it) }

        val source = job.imageUrl ?: job.imageB64?.let { "data:image/png;base64,$it" }
        if (source != null) {
            AsyncBitmap(
                source = source,
                contentDescription = job.prompt,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
