package com.app.chat.feature.chatlist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R

/**
 * Adapter del listado de chats (ChatList).
 *
 * - Usa ListAdapter + DiffUtil para aplicar cambios de forma eficiente.
 * - Emite un callback [onClick] cuando el usuario toca una fila.
 * - Cada fila muestra: nombre/identificador del otro usuario, hora del último mensaje,
 *   preview del último mensaje y (si aplica) contador de no leídos.
 */
class ChatAdapter(
    private val onClick: (UiChatRow) -> Unit
) : ListAdapter<UiChatRow, ChatAdapter.VH>(DIFF) {

    /**
     * Infla el layout de la fila del chat y crea el ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_row, parent, false)
        return VH(v, onClick)
    }

    /**
     * Vincula los datos de la posición actual con el ViewHolder.
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder para una fila de chat.
     *
     * Mantiene referencias a las vistas del layout y expone un método [bind]
     * para renderizar una [UiChatRow].
     */
    class VH(itemView: View, private val onClick: (UiChatRow) -> Unit) : RecyclerView.ViewHolder(itemView) {
        // Vistas del layout (título/nombre, hora, preview y badge de no leídos)
        private val tvDisplayName: TextView = itemView.findViewById(R.id.tvDisplayName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tvUnreadCount)

        // Referencia al elemento actualmente vinculado (para usar en el click)
        private var current: UiChatRow? = null

        init {
            // Propaga el click de la fila con el elemento actual (si existe)
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        /**
         * Renderiza los datos de una fila:
         * - Nombre/identificador del destinatario.
         * - Hora del último mensaje.
         * - Texto del último mensaje.
         * - Badge con cantidad de mensajes no leídos (si > 0).
         */
        fun bind(row: UiChatRow) {
            current = row
            tvDisplayName.text = row.otherDisplayName
            tvTime.text = row.lastMessageTime
            tvLastMessage.text = row.lastMessage

            // Muestra/oculta el contador de no leídos según corresponda
            if (row.unreadCount > 0) {
                tvUnreadCount.text = row.unreadCount.toString()
                tvUnreadCount.visibility = View.VISIBLE
            } else {
                tvUnreadCount.visibility = View.GONE
            }
        }
    }

    companion object {
        /**
         * DiffUtil para calcular mínimas actualizaciones entre listas viejas/nuevas.
         * - areItemsTheSame: compara por id de chat.
         * - areContentsTheSame: compara por igualdad estructural de la fila.
         */
        private val DIFF = object : DiffUtil.ItemCallback<UiChatRow>() {
            override fun areItemsTheSame(oldItem: UiChatRow, newItem: UiChatRow): Boolean =
                oldItem.chatId == newItem.chatId

            override fun areContentsTheSame(oldItem: UiChatRow, newItem: UiChatRow): Boolean =
                oldItem == newItem
        }
    }
}
