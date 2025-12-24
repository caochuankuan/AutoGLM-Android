package com.yifeng.autogml.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 3000,
    val temperature: Double = 0.0,
    @SerializedName("top_p") val topP: Double = 0.85,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double = 0.2,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: Any // Can be String or List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: MessageResponse
)

data class MessageResponse(
    val content: String
)
