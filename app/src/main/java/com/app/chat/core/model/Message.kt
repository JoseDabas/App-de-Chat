package com.app.chat.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Message(
    @DocumentId
    val id: String = "",
    val chatId: String = "",
    val text: String = "",
    val senderId: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Timestamp? = null
)
