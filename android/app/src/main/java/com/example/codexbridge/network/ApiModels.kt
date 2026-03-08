package com.example.codexbridge.network

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("password") val password: String,
)

data class LoginResponse(
    @SerializedName("token") val token: String?,
)

data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("prompt") val prompt: String,
    @SerializedName("status") val status: String,
    @SerializedName("cwd") val cwd: String?,
    @SerializedName("conversation_id") val conversationId: String?,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?,
    @SerializedName("exit_code") val exitCode: Int?,
    @SerializedName("error_message") val errorMessage: String?,
)

data class TaskListResponse(
    @SerializedName("items") val items: List<TaskDto>,
)

data class CreateTaskRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("cwd") val cwd: String?,
)

data class CreateTaskResponse(
    @SerializedName("task") val task: TaskDto,
)

data class TaskResponse(
    @SerializedName("task") val task: TaskDto,
)

data class LogItem(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("seq") val seq: Int,
    @SerializedName("line") val line: String,
    @SerializedName("created_at") val createdAt: String,
)

data class TaskLogsResponse(
    @SerializedName("logs") val logs: List<LogItem>,
    @SerializedName("latest_seq") val latestSeq: Int,
    @SerializedName("task_status") val taskStatus: String,
)

data class MessageResponse(
    @SerializedName("message") val message: String?,
    @SerializedName("error") val error: String?,
)

data class ChatNewResponse(
    @SerializedName("conversation_id") val conversationId: String,
)

data class ChatSendRequest(
    @SerializedName("message") val message: String,
    @SerializedName("cwd") val cwd: String?,
    @SerializedName("conversation_id") val conversationId: String?,
)

data class ChatSendResponse(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("task") val task: TaskDto,
    @SerializedName("resumed_session_id") val resumedSessionId: String?,
)

data class ChatMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("status") val status: String,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("session_id") val sessionId: String?,
)

data class ChatMessagesResponse(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("messages") val messages: List<ChatMessageDto>,
)
