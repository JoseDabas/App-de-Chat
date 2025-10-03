package com.app.chat.feature.chatlist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R

class ChatAdapter(
    private val onClick: (UiChatRow) -> Unit
) : ListAdapter<UiChatRow, ChatAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_row, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View, private val onClick: (UiChatRow) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        private val tvPresence: TextView = itemView.findViewById(R.id.tvPresence)

        private var current: UiChatRow? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(row: UiChatRow) {
            current = row
            tvEmail.text = row.otherEmail
            tvPresence.text = row.presence
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UiChatRow>() {
            override fun areItemsTheSame(oldItem: UiChatRow, newItem: UiChatRow): Boolean =
                oldItem.chatId == newItem.chatId

            override fun areContentsTheSame(oldItem: UiChatRow, newItem: UiChatRow): Boolean =
                oldItem == newItem
        }
    }
}
