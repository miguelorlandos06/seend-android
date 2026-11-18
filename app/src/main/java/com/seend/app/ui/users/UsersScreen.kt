package com.seend.app.ui.users

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seend.app.ui.components.EmptyState
import com.seend.app.ui.components.UserListItem

/**
 * Pantalla "Nuevo chat": lista de usuarios registrados, agrupada en
 * línea / desconectados. Tocar un usuario abre la conversación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onSeleccion: (String) -> Unit,
    vm: UsersViewModel = viewModel(),
) {
    val state by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (state.usuarios.isEmpty() && !state.cargando) {
            EmptyState(mensaje = "No hay otros usuarios registrados todavía.")
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            val (online, offline) = state.usuarios.partition { it.enLinea }
            if (online.isNotEmpty()) {
                item { Separator("En línea") }
                items(online, key = { it.nombre }) { u ->
                    UserListItem(
                        usuario = u,
                        onClick = { onSeleccion(u.nombre) },
                        escribiendo = state.escribiendo[u.nombre] == true,
                    )
                }
            }
            if (offline.isNotEmpty()) {
                item { Separator("Desconectados") }
                items(offline, key = { it.nombre }) { u ->
                    UserListItem(
                        usuario = u,
                        onClick = { onSeleccion(u.nombre) },
                        escribiendo = state.escribiendo[u.nombre] == true,
                    )
                }
            }
        }
    }
}

@Composable
private fun Separator(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
