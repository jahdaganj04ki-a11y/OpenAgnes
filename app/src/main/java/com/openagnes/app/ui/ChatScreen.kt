package com.openagnes.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openagnes.app.AppViewModel
import com.openagnes.app.data.AgnesCatalog

@Composable
fun ChatScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val conversations by vm.conversations.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val stream by vm.stream.collectAsState()

    val conversation = conversations.firstOrNull { it.id == activeId }

    var input by remember { mutableStateOf("") }
    var attachedPath by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachedPath = vm.importImage(uri)
    }

    val listState = rememberLazyListState()
    val messages = conversation?.messages.orEmpty()

    LaunchedEffect(messages.size, stream.answer.length) {
        val total = messages.size + if (stream.streaming) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier.fillMaxSize()) {

        // --- header -------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.closeConversation() }) { Text("‹ Zurück") }
            Text(
                text = conversation?.title ?: "Chat",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (conversation != null) {
                PickerButton(
                    label = "Modell",
                    options = AgnesCatalog.textModels,
                    selected = conversation.model,
                    onSelected = { vm.setConversationModel(conversation.id, it) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // --- messages -----------------------------------------------------
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                val isUser = message.role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    message.imagePath?.let { path ->
                        AsyncBitmap(
                            source = path,
                            contentDescription = "Anhang",
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (message.text.isNotBlank()) {
                        MessageBubble(
                            text = message.text,
                            reasoning = message.reasoning,
                            isUser = isUser,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }

            if (stream.streaming) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        if (stream.answer.isBlank()) {
                            StatusRow(
                                text = if (stream.reasoning.isBlank()) "Warte auf Antwort…" else "Denkt nach…",
                            )
                        } else {
                            MessageBubble(
                                text = stream.answer,
                                reasoning = "",
                                isUser = false,
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                    }
                }
            }
        }

        // --- composer -----------------------------------------------------
        stream.error?.let { ErrorText(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }

        attachedPath?.let { path ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncBitmap(
                    source = path,
                    contentDescription = "Anhang",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                TextButton(onClick = { attachedPath = null }) { Text("Anhang entfernen") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Text("+ Bild")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nachricht…") },
                maxLines = 4,
            )
            Button(
                onClick = {
                    vm.sendMessage(input.trim(), attachedPath)
                    input = ""
                    attachedPath = null
                },
                enabled = !stream.streaming && (input.isNotBlank() || attachedPath != null),
            ) {
                Text("Senden")
            }
        }
    }
}
