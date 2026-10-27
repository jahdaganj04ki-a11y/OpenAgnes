package com.openagnes.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openagnes.app.AppViewModel
import com.openagnes.app.data.AgnesCatalog
import com.openagnes.app.data.AppSettings

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var textModel by remember(settings.textModel) { mutableStateOf(settings.textModel) }
    var imageModel by remember(settings.imageModel) { mutableStateOf(settings.imageModel) }
    var videoModel by remember(settings.videoModel) { mutableStateOf(settings.videoModel) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Basis-URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API-Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Standardmodelle", style = MaterialTheme.typography.titleSmall)
        PickerButton(
            label = "Text/Vision",
            options = AgnesCatalog.textModels,
            selected = textModel,
            onSelected = { textModel = it },
        )
        PickerButton(
            label = "Bild",
            options = AgnesCatalog.imageModels,
            selected = imageModel,
            onSelected = { imageModel = it },
        )
        PickerButton(
            label = "Video",
            options = AgnesCatalog.videoModels,
            selected = videoModel,
            onSelected = { videoModel = it },
        )

        Button(
            onClick = {
                vm.updateSettings(
                    AppSettings(
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        textModel = textModel,
                        imageModel = imageModel,
                        videoModel = videoModel,
                    ),
                )
                savedMessage = "Gespeichert."
            },
            enabled = baseUrl.isNotBlank() && apiKey.isNotBlank(),
        ) {
            Text("Speichern")
        }

        savedMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "Der eingebaute API-Key stammt aus der zur Build-Zeit gelesenen .env-Datei. " +
                "Ein Key im APK ist grundsätzlich unsicher – für produktive Nutzung gehört der " +
                "Zugriff hinter einen eigenen Server-Proxy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
