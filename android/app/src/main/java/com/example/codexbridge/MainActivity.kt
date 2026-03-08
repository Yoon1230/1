package com.example.codexbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size`r`nimport androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codexbridge.network.TaskDto
import com.example.codexbridge.ui.AppViewModel


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BridgeApp()
                }
            }
        }
    }
}


@Composable
fun BridgeApp(vm: AppViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Codex Bridge", style = MaterialTheme.typography.headlineSmall)

        if (state.token == null) {
            LoginPanel(
                baseUrl = state.baseUrl,
                password = state.password,
                loading = state.loading,
                message = state.message,
                onBaseUrlChanged = vm::onBaseUrlChanged,
                onPasswordChanged = vm::onPasswordChanged,
                onLogin = vm::login,
                onClearMessage = vm::clearMessage,
            )
        } else {
            TaskPanel(
                state = state,
                onPromptChanged = vm::onPromptChanged,
                onCwdChanged = vm::onCwdChanged,
                onSubmitTask = vm::submitTask,
                onRefreshTasks = vm::refreshTasks,
                onSelectTask = vm::selectTask,
                onRefreshLogs = vm::refreshSelectedLogs,
                onCancelTask = vm::cancelSelectedTask,
                onLogout = vm::logout,
                onClearMessage = vm::clearMessage,
            )
        }
    }
}


@Composable
private fun LoginPanel(
    baseUrl: String,
    password: String,
    loading: Boolean,
    message: String,
    onBaseUrlChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onClearMessage: () -> Unit,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = {
            onBaseUrlChanged(it)
            onClearMessage()
        },
        label = { Text("电脑桥接地址 (例如 http://192.168.1.10:8765/)") },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = password,
        onValueChange = {
            onPasswordChanged(it)
            onClearMessage()
        },
        label = { Text("桥接密码") },
        modifier = Modifier.fillMaxWidth(),
    )

    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text("登录")
        }
    }

    if (message.isNotBlank()) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}


@Composable
private fun TaskPanel(
    state: com.example.codexbridge.ui.AppUiState,
    onPromptChanged: (String) -> Unit,
    onCwdChanged: (String) -> Unit,
    onSubmitTask: () -> Unit,
    onRefreshTasks: () -> Unit,
    onSelectTask: (String) -> Unit,
    onRefreshLogs: () -> Unit,
    onCancelTask: () -> Unit,
    onLogout: () -> Unit,
    onClearMessage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("已连接", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefreshTasks) { Text("刷新") }
            TextButton(onClick = onLogout) { Text("退出") }
        }
    }

    OutlinedTextField(
        value = state.prompt,
        onValueChange = {
            onPromptChanged(it)
            onClearMessage()
        },
        label = { Text("让 Codex 执行什么任务") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )

    OutlinedTextField(
        value = state.cwd,
        onValueChange = onCwdChanged,
        label = { Text("工作目录（可选）") },
        modifier = Modifier.fillMaxWidth(),
    )

    Button(onClick = onSubmitTask, modifier = Modifier.fillMaxWidth()) {
        Text("提交任务")
    }

    if (state.message.isNotBlank()) {
        Text(state.message, color = MaterialTheme.colorScheme.primary)
    }

    HorizontalDivider()
    Text("任务列表", style = MaterialTheme.typography.titleMedium)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFFF6F8FA), RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.tasks, key = { it.id }) { task ->
            TaskItem(
                task = task,
                selected = task.id == state.selectedTaskId,
                onClick = { onSelectTask(task.id) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRefreshLogs) { Text("刷新日志") }
        Button(onClick = onCancelTask) { Text("取消任务") }
    }

    Text("日志", style = MaterialTheme.typography.titleMedium)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(Color(0xFF101418), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.logs, key = { "${it.taskId}-${it.seq}" }) { log ->
            Text(
                text = "[${log.seq}] ${log.line}",
                color = Color(0xFFB8F7D4),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.logs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无日志", color = Color(0xFF8EA0B3))
                }
            }
        }
    }
}


@Composable
private fun TaskItem(task: TaskDto, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFFE3F2FD) else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(task.id.take(8) + "  " + task.status, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(task.prompt.take(80), style = MaterialTheme.typography.bodySmall)
    }
}

