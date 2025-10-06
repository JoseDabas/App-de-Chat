package com.app.chat.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Modelo de dominio para un mensaje dentro de un chat.
 *
 * Estructura:
 * - id: ID del documento en Firestore. Se inyecta automáticamente gracias a @DocumentId
 *       cuando se deserializa el snapshot.
 * - chatId: Identificador del chat al que pertenece el mensaje (coincide con el id del
 *           documento en la colección "chats").
 * - text: Contenido textual del mensaje. Puede estar vacío si el mensaje es solo imagen.
 * - senderId: UID del usuario que envió el mensaje (FirebaseAuth.getInstance().currentUser.uid).
 * - createdAt: Marca de tiempo de creación. Suele asignarse con FieldValue.serverTimestamp()
 *              desde el cliente; al leerse, se recibe como Timestamp. Es nullable porque
 *              mientras el servidor resuelve el timestamp puede venir como null.
 *
 * Notas:
 * - @DocumentId permite mapear el id del documento a la propiedad 'id' sin estar almacenado
 *   explícitamente en el campo del documento.
 * - @PropertyName("createdAt") fija el nombre exacto del campo en Firestore, útil cuando se
 *   usan getters/setters o cuando se quiere asegurar la compatibilidad con el esquema remoto.
 * - El orden cronológico en consultas debe realizarse con 'orderBy("createdAt")' y, dado que
 *   puede ser null en el primer instante, conviene combinarlo con 'whereNotEqualTo' o
 *   filtrar en cliente si fuera necesario.
 */
data class Message(
    @DocumentId
    val id: String = "",
    val chatId: String = "",
    val text: String = "",
    val senderId: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null
)
