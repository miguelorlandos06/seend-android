package com.seend.app.ui.chatlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seend.app.ServiceLocator
import com.seend.app.data.remote.ConnState
import com.seend.app.ui.components.Avatar
import com.seend.app.ui.components.ChatListItem
import com.seend.app.ui.components.ConnectionBanner
import com.seend.app.ui.components.EmptyState

/**
 * Pantalla principal: lista de conversaciones derivada localmente desde
 * los mensajes guardados en Room. TopAppBar con avatar del usuario que
 * abre "Mi perfil", FAB para crear nuevo chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNuevoChat: () -> Unit,
    onAbrirChat: (String) -> Unit,
    onMiPerfil: () -> Unit,
    vm: ChatListViewModel = viewModel(),
) {
    val state by vm.ui.collectAsState()
    val connState by ServiceLocator.repo.connectionState.collectAsState(initial = ConnState.Disconnected)
    val miUsuario by ServiceLocator.repo.usuarioPorNombreFlow(state.yo.orEmpty())
        .collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = onMiPerfil) {
                        if (miUsuario != null && miUsuario?.avatar?.isNotEmpty() == true) {
                            Avatar(
                                url = miUsuario?.avatar,
                                nombre = state.yo.orEmpty(),
                                size = 32.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Person, contentDescription = "Mi perfil")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevoChat) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo chat")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ConnectionBanner(state = connState)
            if (state.chats.isEmpty() && !state.cargando) {
                EmptyState(mensaje = "No tienes conversaciones.\nToca + para empezar.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.chats, key = { it.otro }) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onAbrirChat(chat.otro) },
                        )
                    }
                }
            }
        }
    }
}
