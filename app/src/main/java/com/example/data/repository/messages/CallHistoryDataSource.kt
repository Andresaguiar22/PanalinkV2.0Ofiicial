package com.example.data.repository.messages

import com.example.data.database.MessageEntity
import com.example.data.database.PanalinkDatabase
import com.example.data.model.CallLog
import com.example.PanaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Historial de llamadas: extrae CallLogs de los mensajes de tipo call persistidos
 * en Room (mensaje.content = JSON CallLog) y expone operaciones de borrado.
 *
 * Extraido de [MessagesRepository] (grupo Calls). La API de la fachada
 * delega aqui sin cambios de comportamiento.
 */
class CallHistoryDataSource {

    private val db by lazy { PanalinkDatabase.getDatabase(PanaApplication.instance) }
    private val messageDao by lazy { db.messageDao() }
    private val repositoryScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val callLogMoshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
        .adapter(CallLog::class.java)

    fun observeCallHistory(): Flow<List<CallLog>> =
        kotlinx.coroutines.flow.flow {
            messageDao.observeCallMessages().collect { entities ->
                val logs = entities.mapNotNull { e ->
                    try {
                        callLogMoshi.fromJson(e.content ?: "")
                    } catch (_: Exception) { null }
                }
                emit(logs)
            }
        }

    fun clearCallHistory() {
        repositoryScope.launch {
            try { messageDao.clearCallHistory() } catch (_: Exception) {}
        }
    }

    fun deleteCallLog(messageId: String) {
        repositoryScope.launch {
            try { messageDao.deleteMessageById(messageId) } catch (_: Exception) {}
        }
    }
}