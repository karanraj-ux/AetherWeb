package com.aetherweb.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ChatRepository(
    private val chatDao: ChatDao,
    private val callLogDao: CallLogDao
) {
    val allMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()

    suspend fun insertMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun insertCallLog(callLog: CallLogEntity): Long = withContext(Dispatchers.IO) {
        callLogDao.insertCallLog(callLog)
    }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) {
        chatDao.deleteAllMessages()
    }

    suspend fun deleteAllCallLogs() = withContext(Dispatchers.IO) {
        callLogDao.deleteAllCallLogs()
    }

    /**
     * Emergency Panic Wipe:
     * Instant zero-trace purge of all local chat databases, call records, and transferred files.
     */
    suspend fun emergencyPanicWipe(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            chatDao.deleteAllMessages()
            callLogDao.deleteAllCallLogs()

            // Purge downloaded and shared files
            val sharedDir = File(context.filesDir, "shared_files")
            if (sharedDir.exists()) {
                sharedDir.deleteRecursively()
            }
            context.cacheDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
