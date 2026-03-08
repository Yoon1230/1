package com.example.codexbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexbridge.network.BridgeRepository
import com.example.codexbridge.network.LogItem
import com.example.codexbridge.network.TaskDto
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
    val tasks: List<TaskDto> = emptyList(),
    val selectedTaskId: String? = null,
    val logs: List<LogItem> = emptyList(),
    val lastSeq: Int = 0,
    val loading: Boolean = false,
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
                            message = "登录失败：${resp.code()}"
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
                refreshTasks()
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
                tasks = emptyList(),
                selectedTaskId = null,
                logs = emptyList(),
                lastSeq = 0,
                message = "已退出",
            )
        }
    }

    fun refreshTasks() {
        val token = uiState.value.token ?: return
        viewModelScope.launch {
            try {
                val resp = repository.listTasks(token)
                if (!resp.isSuccessful) {
                    _uiState.update { it.copy(message = "加载任务失败：${resp.code()}") }
                    return@launch
                }
                val tasks = resp.body()?.items.orEmpty()
                _uiState.update { state ->
                    val selectedId = state.selectedTaskId
                    val keepSelected = tasks.any { it.id == selectedId }
                    state.copy(
                        tasks = tasks,
                        selectedTaskId = if (keepSelected) selectedId else null,
                        logs = if (keepSelected) state.logs else emptyList(),
                        lastSeq = if (keepSelected) state.lastSeq else 0,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "加载任务异常：${e.message}") }
            }
        }
    }

    fun submitTask() {
        val token = uiState.value.token ?: return
        val prompt = uiState.value.prompt.trim()
        val cwd = uiState.value.cwd.trim()

        if (prompt.isBlank()) {
            _uiState.update { it.copy(message = "请先输入任务内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val resp = repository.createTask(
                    token = token,
                    prompt = prompt,
                    cwd = if (cwd.isBlank()) null else cwd,
                )
                if (!resp.isSuccessful || resp.body()?.task == null) {
                    _uiState.update {
                        it.copy(loading = false, message = "创建任务失败：${resp.code()}")
                    }
                    return@launch
                }

                val task = resp.body()!!.task
                _uiState.update {
                    it.copy(
                        loading = false,
                        prompt = "",
                        selectedTaskId = task.id,
                        logs = emptyList(),
                        lastSeq = 0,
                        message = "任务已提交",
                    )
                }

                refreshTasks()
                startLogPolling(task.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, message = "提交异常：${e.message}")
                }
            }
        }
    }

    fun selectTask(taskId: String) {
        _uiState.update {
            it.copy(selectedTaskId = taskId, logs = emptyList(), lastSeq = 0)
        }
        startLogPolling(taskId)
    }

    fun cancelSelectedTask() {
        val token = uiState.value.token ?: return
        val taskId = uiState.value.selectedTaskId ?: return

        viewModelScope.launch {
            try {
                val resp = repository.cancelTask(token, taskId)
                if (!resp.isSuccessful) {
                    _uiState.update { it.copy(message = "取消失败：${resp.code()}") }
                    return@launch
                }
                _uiState.update { it.copy(message = "已发送取消请求") }
                refreshTasks()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "取消异常：${e.message}") }
            }
        }
    }

    fun refreshSelectedLogs() {
        val taskId = uiState.value.selectedTaskId ?: return
        startLogPolling(taskId, singleRun = true)
    }

    private fun startLogPolling(taskId: String, singleRun: Boolean = false) {
        val token = uiState.value.token ?: return

        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            do {
                try {
                    val after = uiState.value.lastSeq
                    val resp = repository.getTaskLogs(token, taskId, after)
                    if (resp.isSuccessful) {
                        val body = resp.body()
                        if (body != null) {
                            _uiState.update { state ->
                                state.copy(
                                    logs = state.logs + body.logs,
                                    lastSeq = body.latestSeq,
                                )
                            }
                            refreshTasks()
                            if (body.taskStatus !in listOf("queued", "running") && !singleRun) {
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                if (singleRun) break
                delay(1500)
            } while (isActive)
        }
    }
}
