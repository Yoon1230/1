package com.example.codexbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexbridge.network.BridgeRepository
import com.example.codexbridge.network.ChatMessageDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


data class AppUiState(
    val baseUrl: String = "http://192.168.1.100:8765/",
    val password: String = "",
    val token: String? = null,
    val prompt: String = "",
    val cwd: String = "",
    val conversationId: String? = null,
    val chatMessages: List<ChatMessageDto> = emptyList(),
    val runningTaskId: String? = null,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val message: String = "",
)


class AppViewModel : ViewModel() {
    private val repository = BridgeRepository()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    private var pollJob: Job? = null

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onPromptChanged(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    fun onCwdChanged(value: String) {
        _uiState.update { it.copy(cwd = value) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = "") }
    }

    fun login() {
        val baseUrl = uiState.value.baseUrl
        val password = uiState.value.password

        if (password.isBlank()) {
            _uiState.update { it.copy(message = "请输入桥接服务密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, message = "") }
            try {
                repository.updateBaseUrl(baseUrl)
                val resp = repository.login(password)
                if (!resp.isSuccessful || resp.body()?.token.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            message = "登录失败：${resp.code()}",
                        )
                    }
                    return@launch
                }

                val token = resp.body()?.token.orEmpty()
                _uiState.update {
                    it.copy(
                        token = token,
                        loading = false,
                        message = "登录成功",
                    )
                }
                createNewConversationInternal(showMessage = false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, message = "连接失败：${e.message}")
                }
            }
        }
    }

    fun logout() {
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                token = null,
                conversationId = null,
                chatMessages = emptyList(),
                runningTaskId = null,
                prompt = "",
                message = "已退出",
            )
        }
    }

    fun createNewConversation() {
        createNewConversationInternal(showMessage = true)
    }

    private fun createNewConversationInternal(showMessage: Boolean) {
        val token = uiState.value.token ?: return
        pollJob?.cancel()

        viewModelScope.launch {
            try {
                val resp = repository.newChat(token)
                if (!resp.isSuccessful || resp.body() == null) {
                    _uiState.update { it.copy(message = "新建会话失败：${resp.code()}") }
                    return@launch
                }

                val conversationId = resp.body()!!.conversationId
                _uiState.update {
                    it.copy(
                        conversationId = conversationId,
                        chatMessages = emptyList(),
                        runningTaskId = null,
                        prompt = "",
                        message = if (showMessage) "已创建新会话" else "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "新建会话异常：${e.message}") }
            }
        }
    }

    fun sendMessage() {
        val token = uiState.value.token ?: return
        val prompt = uiState.value.prompt.trim()
        val cwd = uiState.value.cwd.trim()
        val conversationId = uiState.value.conversationId

        if (prompt.isBlank()) {
            _uiState.update { it.copy(message = "请先输入消息") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, message = "") }
            try {
                val resp = repository.sendChatMessage(
                    token = token,
                    message = prompt,
                    cwd = if (cwd.isBlank()) null else cwd,
                    conversationId = conversationId,
                )
                if (!resp.isSuccessful || resp.body() == null) {
                    _uiState.update {
                        it.copy(sending = false, message = "发送失败：${resp.code()}")
                    }
                    return@launch
                }

                val body = resp.body()!!
                _uiState.update {
                    it.copy(
                        sending = false,
                        prompt = "",
                        conversationId = body.conversationId,
                        message = "已发送",
                    )
                }

                refreshChat(singleRun = true)
                startChatPolling()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(sending = false, message = "发送异常：${e.message}")
                }
            }
        }
    }

    fun refreshChat(singleRun: Boolean = true) {
        val token = uiState.value.token ?: return
        val conversationId = uiState.value.conversationId ?: return

        viewModelScope.launch {
            loadConversation(token, conversationId)

            if (!singleRun) {
                startChatPolling()
            }
        }
    }

    fun cancelRunningTask() {
        val token = uiState.value.token ?: return
        val taskId = uiState.value.runningTaskId ?: run {
            _uiState.update { it.copy(message = "当前没有运行中的任务") }
            return
        }

        viewModelScope.launch {
            try {
                val resp = repository.cancelTask(token, taskId)
                if (!resp.isSuccessful) {
                    _uiState.update { it.copy(message = "取消失败：${resp.code()}") }
                    return@launch
                }
                _uiState.update { it.copy(message = "已发送取消请求") }
                refreshChat(singleRun = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "取消异常：${e.message}") }
            }
        }
    }

    private fun startChatPolling() {
        val token = uiState.value.token ?: return
        val conversationId = uiState.value.conversationId ?: return

        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            do {
                val stillRunning = loadConversation(token, conversationId)
                if (!stillRunning) {
                    break
                }
                delay(1500)
            } while (isActive)
        }
    }

    private suspend fun loadConversation(token: String, conversationId: String): Boolean {
        return try {
            val resp = repository.getChatMessages(token, conversationId)
            if (!resp.isSuccessful || resp.body() == null) {
                _uiState.update { it.copy(message = "拉取会话失败：${resp.code()}") }
                return false
            }

            val body = resp.body()!!
            val runningTaskId = body.messages
                .lastOrNull { it.role == "assistant" && it.status in listOf("queued", "running") }
                ?.taskId

            _uiState.update {
                it.copy(
                    conversationId = body.conversationId,
                    chatMessages = body.messages,
                    runningTaskId = runningTaskId,
                )
            }

            runningTaskId != null
        } catch (e: Exception) {
            _uiState.update { it.copy(message = "拉取会话异常：${e.message}") }
            false
        }
    }
}
