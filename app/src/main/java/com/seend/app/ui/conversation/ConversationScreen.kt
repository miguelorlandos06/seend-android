package com.seend.app.ui.conversation

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seend.app.ui.components.Avatar
import com.seend.app.ui.components.ChatInputBar
import com.seend.app.ui.components.MessageBubble
import com.seend.app.ui.components.ReplyBar
import com.seend.app.util.textoEstado
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    otro: String,
    onBack: () -> Unit,
    onVerPerfil: (String) -> Unit,
    vm: ConversationViewModel = viewModel(
        key = "conv-$otro",
        factory = viewModelFactory { initializer { ConversationViewModel(otro) } },
    ),
) {
    val state by vm.ui.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll al final al llegar mensajes nuevos.
    LaunchedEffect(state.mensajes.size) {
        if (state.mensajes.isNotEmpty()) {
            listState.animateScrollToItem(state.mensajes.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                    ) {
                        IconButton(onClick = { onVerPerfil(state.otro) }, modifier = Modifier.padding(4.dp)) {
                            Avatar(url = state.otroAvatar, nombre = state.otro, size = 36.dp)
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = state.otro,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = textoEstado(
                                    enLinea = state.otroEnLinea,
                                    escribiendo = state.otroEscribiendo,
                                    ultima = state.otraUltimaConexion,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.otroEscribiendo || state.otroEnLinea)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = if (state.otroEscribiendo) FontStyle.Italic else FontStyle.Normal,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Column {
                state.respondiendoA?.let { m ->
                    ReplyBar(
                        autor = if (m.de == state.yo) "Tú" else m.de,
                        texto = m.texto.ifEmpty { "[Mensaje]" },
                        onCerrar = { vm.setRespondiendo(null) },
                    )
                }
                ChatInputBar(
                    texto = state.texto,
                    onTextoChange = { vm.onTextoCambiado(it, state.yo) },
                    onEnviar = { vm.enviar() },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.mensajes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sin mensajes todavía. Saluda 👋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.mensajes, key = { it.id }) { m ->
                        MessageBubble(
                            mensaje = m,
                            onResponder = { vm.setRespondiendo(it) },
                        )
                    }
                }
            }
        }
    }
}
