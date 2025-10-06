package com.app.chat.core.model

/**
 * Modelo de dominio para un chat.
 *
 * Campos:
 * - id: Identificador único del chat (document id en Firestore).
 * - title: Título visible del chat. En DMs puede ser derivado del otro usuario.
 * - lastMessage: Último mensaje enviado (texto plano para previsualización en lista).
 * - participants: UIDs de los usuarios que pertenecen al chat.
 *
 * Notas de uso:
 * - Este modelo es inmutable; cada cambio implica crear una nueva instancia.
 * - En Firestore se espera que los campos tengan el mismo nombre para serializar/deserializar.
 */
data class Chat(
    val id: String = "",
    val title: String = "",
    val lastMessage: String = "",
    val participants: List<String> = emptyList()
)
