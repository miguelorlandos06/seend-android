package com.seend.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seend.app.ServiceLocator
import com.seend.app.data.model.ChatPreviewLocal
import com.seend.app.data.repo.SeendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatListUiState(
    val yo: String? = null,
    val chats: List<ChatPreviewLocal> = emptyList(),
    val cargando: Boolean = true,
)

class ChatListViewModel : ViewModel() {

    private val repo: SeendRepository = ServiceLocator.repo

    private val _ui = MutableStateFlow(ChatListUiState())
    val ui: StateFlow<ChatListUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.yo.collectLatest { yo ->
                _ui.value = _ui.value.copy(yo = yo)
                if (yo != null) {
                    repo.chatsFlow(yo).collectLatest { chats ->
                        _ui.value = _ui.value.copy(chats = chats, cargando = false)
                    }
                }
            }
        }
    }
}
