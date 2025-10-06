package com.app.chat.feature.chat.data

import com.app.chat.core.model.Message
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Acceso a datos del chat (mensajes) sobre Firestore.
 *
 * Responsabilidades:
 * - Escuchar en tiempo real los mensajes de un chat (ordenados por fecha).
 * - Enviar (persistir) un mensaje de texto al sub-collection `messages` del chat.
 *
 * Notas:
 * - Se usa `callbackFlow` para exponer los updates de Firestore como un `Flow`.
 * - La colección de mensajes se asume en `chats/{chatId}/messages`.
 * - Los documentos de mensaje respetan el modelo `Message`.
 */
class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Devuelve un flujo con la lista de mensajes de un chat dado.
     *
     * Implementación:
     * - Crea un listener (`addSnapshotListener`) sobre `chats/{chatId}/messages`
     *   ordenado por `createdAt` (ASC).
     * - Cada cambio en Firestore emite la lista completa mapeada a `Message`.
     * - Si hay error en el listener, se emite una lista vacía.
     * - `awaitClose` asegura remover el listener cuando el colector del Flow se cancele.
     *
     * @param chatId ID del chat cuyo timeline de mensajes se observará.
     * @return Flow que emite la lista de mensajes en orden cronológico ascendente.
     */
    fun streamMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val ref = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                // En caso de error, emitimos una lista vacía para no romper el colector.
                trySend(emptyList())
                return@addSnapshotListener
            }
            // Mapeo seguro de documentos → modelo Message (puede haber nulos).
            val list = snap?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
            trySend(list)
        }

        // Limpieza del listener cuando el flujo deja de observarse.
        awaitClose { reg.remove() }
    }

    /**
     * Envía un mensaje de texto al chat indicado.
     *
     * Implementación:
     * - Obtiene el UID del usuario autenticado.
     * - Crea un mapa con campos del mensaje: `chatId`, `text` (trim), `senderId`, `createdAt`.
     * - Inserta el documento en `chats/{chatId}/messages`.
     *
     * Consideraciones:
     * - Si no hay usuario autenticado, la función retorna sin hacer nada.
     * - El lado de la UI/VM debe validar que `text` no esté vacío (opcional).
     *
     * @param chatId ID del chat de destino.
     * @param text Contenido del mensaje a enviar.
     */
    suspend fun sendMessage(chatId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "chatId" to chatId,
            "text" to text.trim(),
            "senderId" to uid,
            "createdAt" to Timestamp.now()
        )
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(data)
    }
}
