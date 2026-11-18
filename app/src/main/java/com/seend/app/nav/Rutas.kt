package com.seend.app.nav

/**
 * Rutas de Compose Navigation. Centralizadas para evitar strings
 * mágicos. El argumento `otro` se pasa como query param.
 */
object Rutas {
    const val AUTH = "auth"
    const val CHAT_LIST = "chat_list"
    const val USERS = "users"
    const val CONVERSATION = "conversation/{otro}"
    const val MY_PROFILE = "my_profile"
    const val OTHER_PROFILE = "other_profile/{otro}"

    fun conversation(otro: String) = "conversation/$otro"
    fun otherProfile(otro: String) = "other_profile/$otro"

    const val ARG_OTRO = "otro"
}
