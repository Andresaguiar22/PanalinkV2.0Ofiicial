package com.example.ai

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "https://panalink.app",
        @Body body: ChatRequest
    ): ChatResponse

    @Multipart
    @POST("audio/transcriptions")
    suspend fun audioTranscription(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "https://panalink.app",
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = RequestBody.create("text/plain".toMediaTypeOrNull(), "openai/whisper-1")
    ): AudioTranscriptionResponse

    data class ChatRequest(
        val model: String,
        val messages: List<Message>
    )
    data class Message(val role: String, val content: String)
    data class ChatResponse(val choices: List<Choice>)
    data class Choice(val message: MessageContent)
    data class MessageContent(val content: String)
    data class AudioTranscriptionResponse(val text: String, val segments: List<Segment> = emptyList())
    data class Segment(val start: Double, val end: Double, val text: String)
}

object OpenRouterService {
    private val apiKey: String by lazy {
        System.getenv("OPENROUTER_API_KEY") ?: ""
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
    val api: OpenRouterApi by lazy { retrofit.create(OpenRouterApi::class.java) }

    suspend fun generateChat(prompt: String, model: String = "mistralai/mistral-7b-instruct"): String {
        return try {
            val resp = api.chatCompletions(
                auth = "Bearer $apiKey",
                body = OpenRouterApi.ChatRequest(
                    model = model,
                    messages = listOf(OpenRouterApi.Message("user", prompt))
                )
            )
            resp.choices.firstOrNull()?.message?.content ?: prompt
        } catch (e: Exception) { prompt }
    }

    suspend fun transcribeAudio(filePart: MultipartBody.Part, model: String = "openai/whisper-1"): String {
        return try {
            val resp = api.audioTranscription(auth = "Bearer $apiKey", file = filePart)
            resp.text
        } catch (e: Exception) { "" }
    }
}
