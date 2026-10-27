package com.openagnes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openagnes.app.AppViewModel
import com.openagnes.app.ui.theme.OpenAgnesTheme

private enum class Tab(val label: String) {
    Chats("Chats"),
    Image("Bild"),
    Video("Video"),
    Settings("Setup"),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val activeId by vm.activeId.collectAsState()
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableStateOf(Tab.Chats) }

    OpenAgnesTheme {
        val active = activeId
        if (active != null) {
            ChatScreen(vm, Modifier.fillMaxSize())
            return@OpenAgnesTheme
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Text(entry.label.take(1)) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (!settings.hasKey) {
                    Text(
                        text = "Kein API-Key gesetzt. Bitte unter „Setup“ eintragen.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                when (tab) {
                    Tab.Chats -> ConversationsScreen(vm)
                    Tab.Image -> ImageScreen(vm)
                    Tab.Video -> VideoScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun ConversationsScreen(vm: AppViewModel) {
    val conversations by vm.conversations.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.newConversation() }) { Text("Neu") }
        }

        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Noch keine Chats. Mit „Neu“ starten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { vm.openConversation(conversation.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = conversation.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${conversation.model} · ${conversation.messages.size} Nachrichten",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.deleteConversation(conversation.id) }) {
                                Text("Löschen")
                            }
                        }
                    }
                }
            }
        }
    }
}
