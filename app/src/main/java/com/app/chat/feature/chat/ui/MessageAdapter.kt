package com.app.chat.feature.chat.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R

// Tipos de ítems para diferenciar mensajes propios y del otro usuario
private const val TYPE_MINE = 1
private const val TYPE_OTHER = 2

/**
 * Adapter de mensajes para el RecyclerView del chat.
 *
 * - Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * - Renderiza dos tipos de vista: mensajes propios (derecha) y del otro (izquierda).
 * - Muestra texto, hora y, si aplica, una imagen que se puede abrir a pantalla completa.
 */
class MessageAdapter(private val currentUid: String) :
    ListAdapter<UiMessage, RecyclerView.ViewHolder>(Diff) {

    /**
     * Callback del DiffUtil para comparar ítems por id y por contenido.
     * Permite animaciones/actualizaciones sin recargar toda la lista.
     */
    object Diff : DiffUtil.ItemCallback<UiMessage>() {
        override fun areItemsTheSame(oldItem: UiMessage, newItem: UiMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UiMessage, newItem: UiMessage) = oldItem == newItem
    }

    /** Devuelve el tipo de vista según si el mensaje es del usuario actual o del otro. */
    override fun getItemViewType(position: Int): Int {
        val m = getItem(position)
        return if (m.senderId == currentUid) TYPE_MINE else TYPE_OTHER
    }

    /** Infla el layout correspondiente al tipo de mensaje. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineVH(inf.inflate(R.layout.item_message_mine, parent, false))
        } else {
            OtherVH(inf.inflate(R.layout.item_message_other, parent, false))
        }
    }

    /** Enlaza datos con el ViewHolder apropiado. */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is MineVH) holder.bind(item) else (holder as OtherVH).bind(item)
    }

    /**
     * ViewHolder para mensajes propios.
     * - Muestra "Tú · hora" y el texto.
     * - Si hay imagen, la renderiza y habilita apertura a pantalla completa.
     */
    class MineVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsgMine)
        private val iv: ImageView = v.findViewById(R.id.ivImageMine)
        fun bind(m: UiMessage) {
            tv.text = buildBubbleText("Tú · ${m.time}", m.text, tv)
            bindImage(iv, m.imageBase64)
        }
    }

    /**
     * ViewHolder para mensajes del otro usuario.
     * - Muestra "DisplayName · hora" y el texto.
     * - Si hay imagen, la renderiza y habilita apertura a pantalla completa.
     */
    class OtherVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsgOther)
        private val iv: ImageView = v.findViewById(R.id.ivImageOther)
        fun bind(m: UiMessage) {
            tv.text = buildBubbleText("${m.senderDisplayName} · ${m.time}", m.text, tv)
            bindImage(iv, m.imageBase64)
        }
    }
}

/**
 * Renderiza una imagen codificada en Base64 en el ImageView.
 *
 * Comportamiento:
 * - Si no hay base64: oculta la vista y limpia listeners.
 * - Si hay base64 válido: decodifica a Bitmap, lo asigna y prepara un click
 *   para abrir la imagen a pantalla completa en ImageViewerActivity.
 */
private fun bindImage(iv: ImageView, base64: String?) {
    if (base64.isNullOrEmpty()) {
        iv.visibility = View.GONE
        iv.setImageDrawable(null)
        iv.setOnClickListener(null)
        return
    }
    try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        iv.visibility = View.VISIBLE
        iv.setImageBitmap(bmp)

        // Al tocar la imagen se abre el visor en pantalla completa
        iv.setOnClickListener { v ->
            val ctx = v.context
            val intent = Intent(ctx, ImageViewerActivity::class.java)
            intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_BASE64, base64)
            ctx.startActivity(intent)
        }
    } catch (_: Throwable) {
        // Si la decodificación falla, se oculta la imagen para evitar mostrar un estado inconsistente
        iv.visibility = View.GONE
        iv.setImageDrawable(null)
        iv.setOnClickListener(null)
    }
}

/**
 * Construye el texto de la burbuja combinando encabezado (autor · hora) y cuerpo (mensaje).
 *
 * Detalles:
 * - El encabezado se muestra más pequeño y en color gris.
 * - Si el cuerpo no está vacío, se agrega en una nueva línea con tamaño normal.
 */
private fun buildBubbleText(header: String, body: String, tv: TextView): CharSequence {
    val gray = ContextCompat.getColor(tv.context, android.R.color.darker_gray)
    val sb = SpannableStringBuilder()

    // Encabezado con estilo reducido y color gris
    val startH = sb.length
    sb.append(header)
    val endH = sb.length
    sb.setSpan(RelativeSizeSpan(0.8f), startH, endH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(ForegroundColorSpan(gray), startH, endH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    // Cuerpo del mensaje (si lo hay) en una nueva línea
    if (body.isNotEmpty()) {
        sb.append("\n")
        sb.append(body)
    }
    return sb
}
