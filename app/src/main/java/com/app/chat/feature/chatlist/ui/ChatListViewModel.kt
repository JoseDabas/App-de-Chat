package com.app.chat.feature.chatlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.chat.core.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private var seeded = false
    private var listening = false

    /** Crea un chat de prueba si no existe (idempotente). */
    fun seedOnce() {
        if (seeded) return
        val uid = auth.currentUser?.uid ?: return
        seeded = true

        val docId = "seed_$uid"
        val docRef = db.collection("chats").document(docId)

        // Verifica si ya existe
        docRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) return@addOnSuccessListener

            val now = FieldValue.serverTimestamp()
            val seedData = mapOf(
                "id" to docId,
                "title" to "Chat de prueba",
                "lastMessage" to "¡Bienvenido al chat de prueba!",
                "participants" to listOf(uid, "BOT"),
                "updatedAt" to now
            )

            docRef.set(seedData)
        }.addOnFailureListener {
            // Silencioso: si falla, la lista simplemente quedará vacía
        }
    }

    /** Empieza a escuchar los chats del usuario. */
    fun load() {
        if (listening) return
        val uid = auth.currentUser?.uid ?: return
        listening = true

        // Si tienes múltiples campos, podrías querer ordenar por updatedAt.
        db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { qs, _ ->
                if (qs == null) {
                    _chats.value = emptyList()
                    return@addSnapshotListener
                }
                val list = qs.documents.mapNotNull { d ->
                    Chat(
                        id = d.getString("id") ?: d.id,
                        title = d.getString("title") ?: "Sin nombre",
                        lastMessage = d.getString("lastMessage") ?: "",
                        participants = d.get("participants") as? List<String> ?: emptyList()
                    )
                }.sortedByDescending { it.title } // orden simple; cambia si usas updatedAt
                _chats.value = list
            }
    }

    /** Útil si quieres limpiar duplicados manualmente una sola vez. */
    fun cleanupDuplicatesOnce() {
        val uid = auth.currentUser?.uid ?: return
        val keepId = "seed_$uid"

        viewModelScope.launch {
            db.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .addOnSuccessListener { snap ->
                    val toDelete = snap.documents.filter {
                        it.id != keepId && (it.getString("title") == "Chat de prueba" || it.id.startsWith("seed_"))
                    }
                    if (toDelete.isEmpty()) return@addOnSuccessListener
                    val batch = db.batch()
                    toDelete.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
        }
    }
}
