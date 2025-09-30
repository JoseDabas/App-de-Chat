package com.app.chat.feature.chatlist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.app.chat.core.model.Chat

class ChatAdapter(
    private val onClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Chat>() {
            override fun areItemsTheSame(oldItem: Chat, newItem: Chat) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Chat, newItem: Chat) = oldItem == newItem
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        fun bind(chat: Chat) {
            tvTitle.text = chat.title.ifBlank { "Sin nombre" }
            tvSubtitle.text = chat.lastMessage
            itemView.setOnClickListener { onClick(chat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
