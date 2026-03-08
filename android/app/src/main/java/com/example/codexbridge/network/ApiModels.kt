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
