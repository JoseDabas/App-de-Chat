package com.app.chat.core.firebase

import com.app.chat.core.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatDataSource {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getChatsForCurrentUser(): List<Chat> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

        val raw = suspendCancellableCoroutine<List<Chat>> { cont ->
            db.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .addOnSuccessListener { qs ->
                    val list = qs.documents.map { it.toChat() }
                    cont.resume(list)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        // --- Deduplicar por título, conservando seed_<uid> si existe ---
        val keepId = "seed_$uid"
        val grouped = raw.groupBy { it.title.trim().ifBlank { it.id } }
        val dedup = mutableListOf<Chat>()
        val toDeleteIds = mutableListOf<String>()

        grouped.values.forEach { chats ->
            if (chats.size == 1) {
                dedup += chats.first()
            } else {
                val keep = chats.find { it.id == keepId } ?: chats.first()
                dedup += keep
                toDeleteIds += chats.filter { it.id != keep.id }.map { it.id }
            }
        }

        // borrar duplicados (si los hay) en background
        if (toDeleteIds.isNotEmpty()) {
            val batch = db.batch()
            toDeleteIds.forEach { id ->
                batch.delete(db.collection("chats").document(id))
            }
            batch.commit() // no necesitamos callbacks aquí
        }

        return dedup
    }

    fun seedTestChatIfMissing() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docId = "seed_$uid"
        val ref = db.collection("chats").document(docId)

        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) return@addOnSuccessListener
            val data = mapOf(
                "id" to docId,
                "title" to "Chat de prueba",
                "lastMessage" to "¡Bienvenido al chat de prueba!",
                "participants" to listOf(uid, "BOT")
            )
            ref.set(data)
        }
    }
}

private fun DocumentSnapshot.toChat(): Chat {
    return Chat(
        id = getString("id") ?: id,
        title = getString("title") ?: "",
        lastMessage = getString("lastMessage") ?: "",
        participants = (get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    )
}
