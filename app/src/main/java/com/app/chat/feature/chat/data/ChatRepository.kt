package com.app.chat.feature.chatlist.data

import com.app.chat.core.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun getMyChats(): List<Chat> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        // Colección sugerida: "chats" con campo "participants": [userIds]
        val snap = db.collection("chats")
            .whereArrayContains("participants", uid)
            .get()
            .await()

        return snap.documents.map { d ->
            Chat(
                id = d.id,
                title = d.getString("title") ?: "Chat",
                lastMessage = d.getString("lastMessage") ?: "",
                participants = (d.get("participants") as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
            )
        }
    }
}
