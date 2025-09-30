package com.app.chat.core.model

data class Chat(
    val id: String = "",
    val title: String = "",
    val lastMessage: String = "",
    val participants: List<String> = emptyList()
)
