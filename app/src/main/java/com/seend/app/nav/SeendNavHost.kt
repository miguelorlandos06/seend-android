package com.seend.app.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seend.app.ServiceLocator
import com.seend.app.data.repo.SesionValidation
import com.seend.app.ui.auth.AuthScreen
import com.seend.app.ui.chatlist.ChatListScreen
import com.seend.app.ui.conversation.ConversationScreen
import com.seend.app.ui.profile.MyProfileScreen
import com.seend.app.ui.profile.OtherProfileScreen
import com.seend.app.ui.users.UsersScreen

/**
 * SeendNavHost: grafo de navegación. Decide pantalla inicial según si
 * hay sesión persistida (CHAT_LIST) o no (AUTH). El
 * [ConnectionViewModel] valida la sesión contra el servidor al arrancar
 * y publica el resultado en [SesionValidation]; lo observamos aquí para
 * navegar al estado correcto si cambia.
 */
@Composable
fun SeendNavHost() {
    val nav = rememberNavController()
    // El ViewModel se crea para que arranque el WS y valide la sesión
    // en su init; no necesitamos referenciarlo directamente.
    @Suppress("UNUSED_VARIABLE")
    val connVm: ConnectionViewModel = viewModel()
    val session by ServiceLocator.repo.session.collectAsState(initial = null)
    val validacion by ServiceLocator.repo.sesionValidation.collectAsState(initial = null)

    // Reaccionar a la validación de sesión.
    LaunchedEffect(validacion) {
        when (validacion) {
            is SesionValidation.Valida -> {
                if (nav.currentDestination?.route != Rutas.CHAT_LIST) {
                    nav.navigate(Rutas.CHAT_LIST) {
                        popUpTo(Rutas.AUTH) { inclusive = true }
                    }
                }
            }
            is SesionValidation.Invalida -> {
                if (nav.currentDestination?.route != Rutas.AUTH) {
                    nav.navigate(Rutas.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            null -> Unit
        }
    }

    val startRoute = if (session != null) Rutas.CHAT_LIST else Rutas.AUTH

    NavHost(
        navController = nav,
        startDestination = startRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Rutas.AUTH) {
            AuthScreen(
                onAuthOk = { nav.navigate(Rutas.CHAT_LIST) {
                    popUpTo(Rutas.AUTH) { inclusive = true }
                } },
            )
        }
        composable(Rutas.CHAT_LIST) {
            ChatListScreen(
                onNuevoChat = { nav.navigate(Rutas.USERS) },
                onAbrirChat = { otro -> nav.navigate(Rutas.conversation(otro)) },
                onMiPerfil = { nav.navigate(Rutas.MY_PROFILE) },
            )
        }
        composable(Rutas.USERS) {
            UsersScreen(
                onBack = { nav.popBackStack() },
                onSeleccion = { otro ->
                    nav.navigate(Rutas.conversation(otro)) {
                        popUpTo(Rutas.USERS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Rutas.CONVERSATION,
            arguments = listOf(navArgument(Rutas.ARG_OTRO) { type = NavType.StringType }),
        ) { entry ->
            val otro = entry.arguments?.getString(Rutas.ARG_OTRO).orEmpty()
            ConversationScreen(
                otro = otro,
                onBack = { nav.popBackStack() },
                onVerPerfil = { nav.navigate(Rutas.otherProfile(it)) },
            )
        }
        composable(Rutas.MY_PROFILE) {
            MyProfileScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Rutas.OTHER_PROFILE,
            arguments = listOf(navArgument(Rutas.ARG_OTRO) { type = NavType.StringType }),
        ) { entry ->
            val otro = entry.arguments?.getString(Rutas.ARG_OTRO).orEmpty()
            OtherProfileScreen(otro = otro, onBack = { nav.popBackStack() })
        }
    }
}
