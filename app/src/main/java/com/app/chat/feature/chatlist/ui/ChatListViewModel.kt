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

/**
 * ViewModel para la pantalla de lista de chats.
 * - Expone un flujo de chats del usuario autenticado.
 * - Puede sembrar un chat de prueba (idempotente).
 * - Incluye una utilidad para limpiar duplicados de chats de prueba.
 */
class ChatListViewModel : ViewModel() {

    // Acceso a Firestore y usuario autenticado actual.
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Estado observable con la lista de chats que la UI consume (RecyclerView).
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    // Guardas para evitar ejecutar tareas más de una vez por ciclo de vida del VM.
    private var seeded = false
    private var listening = false

    /**
     * Inserta un chat de prueba para el usuario si aún no existe.
     * Útil durante desarrollo/demos. No se vuelve a ejecutar si ya corrió.
     */
    fun seedOnce() {
        if (seeded) return
        val uid = auth.currentUser?.uid ?: return
        seeded = true

        val docId = "seed_$uid"
        val docRef = db.collection("chats").document(docId)

        // Solo crea si no existe ya un documento con ese id.
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
            // Si falla no se propaga error: la UI simplemente seguirá sin ese chat de prueba.
        }
    }

    /**
     * Comienza a escuchar en tiempo real los chats donde participa el usuario actual.
     * Actualiza el StateFlow cada vez que hay cambios en la colección.
     */
    fun load() {
        if (listening) return
        val uid = auth.currentUser?.uid ?: return
        listening = true

        db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { qs, _ ->
                if (qs == null) {
                    _chats.value = emptyList()
                    return@addSnapshotListener
                }

                // Mapea cada documento a modelo Chat y ordena la lista.
                val list = qs.documents.mapNotNull { d ->
                    Chat(
                        id = d.getString("id") ?: d.id,
                        title = d.getString("title") ?: "Sin nombre",
                        lastMessage = d.getString("lastMessage") ?: "",
                        participants = d.get("participants") as? List<String> ?: emptyList()
                    )
                }.sortedByDescending { it.title } // Cambiar a updatedAt si se quiere orden por actividad

                _chats.value = list
            }
    }

    /**
     * Herramienta para limpieza: elimina duplicados del chat de prueba,
     * conservando el documento cuyo id es "seed_<uid>". Ejecutar una sola vez si se necesita.
     */
    fun cleanupDuplicatesOnce() {
        val uid = auth.currentUser?.uid ?: return
        val keepId = "seed_$uid"

        viewModelScope.launch {
            db.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .addOnSuccessListener { snap ->
                    // Marca para borrar cualquier "seed_*" o "Chat de prueba" que no sea el principal.
                    val toDelete = snap.documents.filter {
                        it.id != keepId &&
                                (it.getString("title") == "Chat de prueba" || it.id.startsWith("seed_"))
                    }
                    if (toDelete.isEmpty()) return@addOnSuccessListener

                    val batch = db.batch()
                    toDelete.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
        }
    }
}
