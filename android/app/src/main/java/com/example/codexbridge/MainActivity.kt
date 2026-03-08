package com.example.codexbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.codexbridge.network.ChatMessageDto
import com.example.codexbridge.ui.AppUiState
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
            ChatPanel(
                state = state,
                onPromptChanged = vm::onPromptChanged,
                onCwdChanged = vm::onCwdChanged,
                onSend = vm::sendMessage,
                onRefresh = { vm.refreshChat(singleRun = true) },
                onNewChat = vm::createNewConversation,
                onCancelTask = vm::cancelRunningTask,
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
private fun ChatPanel(
    state: AppUiState,
    onPromptChanged: (String) -> Unit,
    onCwdChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onNewChat: () -> Unit,
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
            TextButton(onClick = onNewChat) { Text("新会话") }
            TextButton(onClick = onRefresh) { Text("刷新") }
            TextButton(onClick = onLogout) { Text("退出") }
        }
    }

    if (!state.conversationId.isNullOrBlank()) {
        Text(
            text = "会话: ${state.conversationId.take(8)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF546E7A),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.chatMessages.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("开始聊点什么吧", color = Color(0xFF8EA0B3))
                }
            }
        } else {
            items(state.chatMessages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
    }

    OutlinedTextField(
        value = state.cwd,
        onValueChange = onCwdChanged,
        label = { Text("工作目录（可选）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    OutlinedTextField(
        value = state.prompt,
        onValueChange = {
            onPromptChanged(it)
            onClearMessage()
        },
        label = { Text("输入消息") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 6,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSend,
            modifier = Modifier,
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("发送")
            }
        }
        Button(
            onClick = onCancelTask,
            modifier = Modifier,
            enabled = state.runningTaskId != null,
        ) {
            Text("取消运行")
        }
    }

    if (state.message.isNotBlank()) {
        Text(state.message, color = MaterialTheme.colorScheme.primary)
    }
}


@Composable
private fun ChatBubble(message: ChatMessageDto) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) Color(0xFFDFF3FF) else Color(0xFF101418)
    val textColor = if (isUser) Color(0xFF0D1B2A) else Color(0xFFE4F8E7)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.95f)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isUser) "你" else "Codex",
                    color = if (isUser) Color(0xFF275B84) else Color(0xFF9FD7B8),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${message.status} · ${message.taskId.take(8)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8A9BA8),
        )
    }
}
