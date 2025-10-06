package com.app.chat.core.firebase

import com.app.chat.core.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Acceso a datos de chats en Firestore.
 *
 * Colección utilizada:
 * - "chats": documentos con campos [id, title, lastMessage, participants]
 *
 * Reglas generales:
 * - Se filtra por el usuario autenticado usando el uid en "participants".
 * - Se ofrece una siembra (seed) de un chat de prueba por usuario.
 */
class ChatDataSource {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Obtiene la lista de chats en los que participa el usuario autenticado.
     *
     * Flujo:
     * 1) Obtiene el uid actual; si no hay sesión, retorna lista vacía.
     * 2) Consulta Firestore con whereArrayContains("participants", uid).
     * 3) Convierte cada documento a Chat.
     * 4) Deduplica posibles chats repetidos por mismo título (manteniendo prioridad
     *    para el chat "seed_<uid>" si aparece), y elimina del backend los duplicados.
     *
     * Nota: la eliminación de duplicados se ejecuta en batch sin esperar resultado
     * (best-effort, no bloquea la respuesta).
     */
    suspend fun getChatsForCurrentUser(): List<Chat> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

        // Puente callback -> corrutina: ejecuta la consulta y reanuda con la lista de Chat
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

        // Política de deduplicación:
        // - Agrupa por título normalizado (si el título está vacío, usa el id como clave).
        // - Si en un grupo hay varios documentos, conserva el que tenga id "seed_<uid>"
        //   (si existe); en caso contrario, conserva el primero.
        // - Marca el resto para borrado.
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

        // Eliminación best-effort de duplicados (no bloquea el retorno de la lista deduplicada)
        if (toDeleteIds.isNotEmpty()) {
            val batch = db.batch()
            toDeleteIds.forEach { id ->
                batch.delete(db.collection("chats").document(id))
            }
            batch.commit()
        }

        return dedup
    }

    /**
     * Asegura que el usuario actual tenga un chat de prueba.
     *
     * Crea (si no existe) el documento "seed_<uid>" en la colección "chats" con:
     * - id: "seed_<uid>"
     * - title: "Chat de prueba"
     * - lastMessage: mensaje de bienvenida
     * - participants: [uid, "BOT"]
     */
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

/**
 * Conversión segura de un DocumentSnapshot a modelo Chat.
 * - Si falta "id", se usa el id del propio documento.
 * - "participants" se filtra a lista de String o queda vacía si no corresponde.
 */
private fun DocumentSnapshot.toChat(): Chat {
    return Chat(
        id = getString("id") ?: id,
        title = getString("title") ?: "",
        lastMessage = getString("lastMessage") ?: "",
        participants = (get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    )
}
