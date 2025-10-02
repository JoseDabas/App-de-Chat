package com.app.chat.feature.chat.data

import com.app.chat.core.model.Message
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun streamMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val ref = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
            trySend(list)
        }

        awaitClose { reg.remove() }
    }

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
