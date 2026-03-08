package com.example.codexbridge.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BridgeRepository {
    private var currentBaseUrl: String = "http://192.168.1.100:8765/"
    private var api: BridgeApi = createApi(currentBaseUrl)

    private fun createApi(baseUrl: String): BridgeApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BridgeApi::class.java)
    }

    fun updateBaseUrl(baseUrlRaw: String) {
        var fixed = baseUrlRaw.trim()
        if (!fixed.endsWith("/")) {
            fixed += "/"
        }
        if (fixed == currentBaseUrl) {
            return
        }
        currentBaseUrl = fixed
        api = createApi(currentBaseUrl)
    }

    suspend fun login(password: String) = api.login(LoginRequest(password = password))

    suspend fun listTasks(token: String) =
        api.listTasks(authorization = bearer(token))

    suspend fun createTask(token: String, prompt: String, cwd: String?) =
        api.createTask(
            authorization = bearer(token),
            request = CreateTaskRequest(prompt = prompt, cwd = cwd),
        )

    suspend fun getTask(token: String, taskId: String) =
        api.getTask(authorization = bearer(token), taskId = taskId)

    suspend fun getTaskLogs(token: String, taskId: String, after: Int) =
        api.getTaskLogs(
            authorization = bearer(token),
            taskId = taskId,
            after = after,
        )

    suspend fun cancelTask(token: String, taskId: String) =
        api.cancelTask(authorization = bearer(token), taskId = taskId)

    private fun bearer(token: String): String = "Bearer $token"
}
