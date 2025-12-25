package com.yifeng.autogml.database

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.util.UUID

/**
 * 聊天记录管理器，使用MMKV进行持久化存储
 */
class ChatHistoryManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ChatHistoryManager? = null
        
        fun getInstance(): ChatHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatHistoryManager().also { INSTANCE = it }
            }
        }
        
        private const val MMKV_ID = "chat_history"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_MESSAGES_PREFIX = "messages_"
        private const val KEY_CURRENT_SESSION = "current_session"
        private const val PAGE_SIZE = 20
    }
    
    private val mmkv: MMKV by lazy { MMKV.mmkvWithID(MMKV_ID) }
    private val gson = Gson()
    
    fun initialize(context: Context) {
        MMKV.initialize(context)
    }
    
    // ==================== 会话管理 ====================
    
    /**
     * 创建新的聊天会话
     */
    fun createSession(title: String = "新对话"): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        val sessions = getSessions().toMutableList()
        sessions.add(0, session) // 添加到开头
        saveSessions(sessions)
        setCurrentSession(session.id)
        
        return session
    }
    
    /**
     * 获取所有会话列表
     */
    fun getSessions(): List<ChatSession> {
        val json = mmkv.decodeString(KEY_SESSIONS, "[]")
        return try {
            val type = object : TypeToken<List<ChatSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取分页会话列表
     */
    fun getSessionsPaged(page: Int = 0): PagedResult<ChatSession> {
        val allSessions = getSessions()
        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, allSessions.size)
        
        return if (startIndex >= allSessions.size) {
            PagedResult(emptyList(), false, allSessions.size)
        } else {
            val items = allSessions.subList(startIndex, endIndex)
            val hasMore = endIndex < allSessions.size
            PagedResult(items, hasMore, allSessions.size)
        }
    }
    
    /**
     * 更新会话信息
     */
    fun updateSession(sessionId: String, title: String? = null) {
        val sessions = getSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            val session = sessions[index]
            val messageCount = getMessageCount(sessionId)
            sessions[index] = session.copy(
                title = title ?: session.title,
                updatedAt = System.currentTimeMillis(),
                messageCount = messageCount
            )
            saveSessions(sessions)
        }
    }
    
    /**
     * 删除会话及其所有消息
     */
    fun deleteSession(sessionId: String) {
        // 删除会话消息
        mmkv.removeValueForKey("$KEY_MESSAGES_PREFIX$sessionId")
        
        // 从会话列表中移除
        val sessions = getSessions().toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessions(sessions)
        
        // 如果删除的是当前会话，清除当前会话
        if (getCurrentSessionId() == sessionId) {
            clearCurrentSession()
        }
    }
    
    /**
     * 设置当前会话
     */
    fun setCurrentSession(sessionId: String) {
        mmkv.encode(KEY_CURRENT_SESSION, sessionId)
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return mmkv.decodeString(KEY_CURRENT_SESSION)
    }
    
    /**
     * 清除当前会话
     */
    fun clearCurrentSession() {
        mmkv.removeValueForKey(KEY_CURRENT_SESSION)
    }
    
    private fun saveSessions(sessions: List<ChatSession>) {
        val json = gson.toJson(sessions)
        mmkv.encode(KEY_SESSIONS, json)
    }
    
    // ==================== 消息管理 ====================
    
    /**
     * 添加消息到指定会话
     */
    fun addMessage(sessionId: String, role: String, content: String, hasImage: Boolean = false): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            hasImage = hasImage
        )
        
        val messages = getMessages(sessionId).toMutableList()
        messages.add(message)
        saveMessages(sessionId, messages)
        
        // 更新会话的更新时间和消息数量
        updateSession(sessionId)
        
        return message
    }
    
    /**
     * 获取指定会话的所有消息
     */
    fun getMessages(sessionId: String): List<ChatMessage> {
        val json = mmkv.decodeString("$KEY_MESSAGES_PREFIX$sessionId", "[]")
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取指定会话的分页消息（倒序分页：最新消息在前）
     */
    fun getMessagesPaged(sessionId: String, page: Int = 0): PagedResult<ChatMessage> {
        val allMessages = getMessages(sessionId).sortedBy { it.timestamp } // 按时间正序排列
        val totalCount = allMessages.size
        
        if (totalCount == 0) {
            return PagedResult(emptyList(), false, 0)
        }
        
        if (page == 0) {
            // 第一页：返回最新的PAGE_SIZE条消息
            val startIndex = maxOf(0, totalCount - PAGE_SIZE)
            val items = allMessages.subList(startIndex, totalCount)
            val hasMore = startIndex > 0
            return PagedResult(items, hasMore, totalCount)
        } else {
            // 后续页：返回更早的消息
            val endIndex = totalCount - page * PAGE_SIZE
            val startIndex = maxOf(0, endIndex - PAGE_SIZE)
            
            return if (startIndex >= endIndex || endIndex <= 0) {
                PagedResult(emptyList(), false, totalCount)
            } else {
                val items = allMessages.subList(startIndex, endIndex)
                val hasMore = startIndex > 0
                PagedResult(items, hasMore, totalCount)
            }
        }
    }
    
    /**
     * 获取指定会话的最新N条消息
     */
    fun getRecentMessages(sessionId: String, limit: Int = PAGE_SIZE): List<ChatMessage> {
        val allMessages = getMessages(sessionId)
        return if (allMessages.size <= limit) {
            allMessages
        } else {
            allMessages.takeLast(limit)
        }
    }
    
    /**
     * 清空指定会话的所有消息
     */
    fun clearMessages(sessionId: String) {
        mmkv.removeValueForKey("$KEY_MESSAGES_PREFIX$sessionId")
        updateSession(sessionId)
    }
    
    /**
     * 获取指定会话的消息数量
     */
    fun getMessageCount(sessionId: String): Int {
        return getMessages(sessionId).size
    }
    
    private fun saveMessages(sessionId: String, messages: List<ChatMessage>) {
        val json = gson.toJson(messages)
        mmkv.encode("$KEY_MESSAGES_PREFIX$sessionId", json)
    }
    
    // ==================== 搜索功能 ====================
    
    /**
     * 在指定会话中搜索消息
     */
    fun searchMessages(sessionId: String, query: String): List<ChatMessage> {
        if (query.isBlank()) return emptyList()
        
        return getMessages(sessionId).filter { message ->
            message.content.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * 在所有会话中搜索消息
     */
    fun searchAllMessages(query: String): List<ChatMessage> {
        if (query.isBlank()) return emptyList()
        
        val allMessages = mutableListOf<ChatMessage>()
        getSessions().forEach { session ->
            allMessages.addAll(searchMessages(session.id, query))
        }
        return allMessages.sortedByDescending { it.timestamp }
    }
    
    // ==================== 数据管理 ====================
    
    /**
     * 清空所有数据
     */
    fun clearAllData() {
        mmkv.clearAll()
    }
    
    /**
     * 获取存储大小（字节）
     */
    fun getStorageSize(): Long {
        return mmkv.totalSize()
    }
    
    /**
     * 导出数据为JSON字符串
     */
    fun exportData(): String {
        val sessions = getSessions()
        val exportData = mutableMapOf<String, Any>()
        exportData["sessions"] = sessions
        
        val messagesData = mutableMapOf<String, List<ChatMessage>>()
        sessions.forEach { session ->
            messagesData[session.id] = getMessages(session.id)
        }
        exportData["messages"] = messagesData
        
        return gson.toJson(exportData)
    }
}