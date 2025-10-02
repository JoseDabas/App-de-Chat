package com.app.chat.feature.chat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.app.chat.core.model.Message
import com.google.firebase.auth.FirebaseAuth

private const val TYPE_MINE = 1
private const val TYPE_OTHER = 2

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(Diff) {

    private val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid

    object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int {
        val m = getItem(position)
        return if (m.senderId == myUid) TYPE_MINE else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineVH(inf.inflate(R.layout.item_message_mine, parent, false))
        } else {
            OtherVH(inf.inflate(R.layout.item_message_other, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = getItem(position)
        when (holder) {
            is MineVH -> holder.bind(m)
            is OtherVH -> holder.bind(m)
        }
    }

    class MineVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tvMsgMine)
        fun bind(m: Message) { tv.text = m.text }
    }

    class OtherVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tvMsgOther)
        fun bind(m: Message) { tv.text = m.text }
    }
}
