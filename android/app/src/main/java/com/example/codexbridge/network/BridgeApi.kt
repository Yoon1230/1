package com.example.codexbridge.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BridgeApi {
    @POST("/api/auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): Response<LoginResponse>

    @GET("/api/tasks")
    suspend fun listTasks(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50,
    ): Response<TaskListResponse>

    @POST("/api/tasks")
    suspend fun createTask(
        @Header("Authorization") authorization: String,
        @Body request: CreateTaskRequest,
    ): Response<CreateTaskResponse>

    @GET("/api/tasks/{taskId}")
    suspend fun getTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
    ): Response<TaskResponse>

    @GET("/api/tasks/{taskId}/logs")
    suspend fun getTaskLogs(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
        @Query("after") after: Int,
        @Query("limit") limit: Int = 200,
    ): Response<TaskLogsResponse>

    @POST("/api/tasks/{taskId}/cancel")
    suspend fun cancelTask(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String,
    ): Response<MessageResponse>

    @POST("/api/chat/new")
    suspend fun newChat(
        @Header("Authorization") authorization: String,
    ): Response<ChatNewResponse>

    @POST("/api/chat/send")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String,
        @Body request: ChatSendRequest,
    ): Response<ChatSendResponse>

    @GET("/api/chat/messages")
    suspend fun getChatMessages(
        @Header("Authorization") authorization: String,
        @Query("conversation_id") conversationId: String,
        @Query("limit") limit: Int = 100,
    ): Response<ChatMessagesResponse>
}
