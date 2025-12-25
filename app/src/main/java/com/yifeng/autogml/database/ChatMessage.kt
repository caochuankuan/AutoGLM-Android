package com.yifeng.autogml.database

import com.google.gson.annotations.SerializedName

/**
 * 聊天消息数据模型
 */
data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    @SerializedName("session_id") val sessionId: String,
    val hasImage: Boolean = false
)

/**
 * 聊天会话数据模型
 */
data class ChatSession(
    val id: String,
    val title: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("message_count") val messageCount: Int = 0
)

/**
 * 分页数据模型
 */
data class PagedResult<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val totalCount: Int
)