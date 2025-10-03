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

private const val TYPE_MINE = 1
private const val TYPE_OTHER = 2

class MessageAdapter(private val currentUid: String) :
    ListAdapter<UiMessage, RecyclerView.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<UiMessage>() {
        override fun areItemsTheSame(oldItem: UiMessage, newItem: UiMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UiMessage, newItem: UiMessage) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int {
        val m = getItem(position)
        return if (m.senderId == currentUid) TYPE_MINE else TYPE_OTHER
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
        val item = getItem(position)
        if (holder is MineVH) holder.bind(item) else (holder as OtherVH).bind(item)
    }

    class MineVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsgMine)
        private val iv: ImageView = v.findViewById(R.id.ivImageMine)
        fun bind(m: UiMessage) {
            tv.text = buildBubbleText("Tú · ${m.time}", m.text, tv)
            bindImage(iv, m.imageBase64)
        }
    }

    class OtherVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsgOther)
        private val iv: ImageView = v.findViewById(R.id.ivImageOther)
        fun bind(m: UiMessage) {
            tv.text = buildBubbleText("${m.senderDisplayName} · ${m.time}", m.text, tv)
            bindImage(iv, m.imageBase64)
        }
    }
}

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

        // Abrir a pantalla completa
        iv.setOnClickListener { v ->
            val ctx = v.context
            val intent = Intent(ctx, ImageViewerActivity::class.java)
            intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_BASE64, base64)
            ctx.startActivity(intent)
        }
    } catch (_: Throwable) {
        iv.visibility = View.GONE
        iv.setImageDrawable(null)
        iv.setOnClickListener(null)
    }
}

private fun buildBubbleText(header: String, body: String, tv: TextView): CharSequence {
    val gray = ContextCompat.getColor(tv.context, android.R.color.darker_gray)
    val sb = SpannableStringBuilder()

    val startH = sb.length
    sb.append(header)
    val endH = sb.length
    sb.setSpan(RelativeSizeSpan(0.8f), startH, endH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(ForegroundColorSpan(gray), startH, endH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    if (body.isNotEmpty()) {
        sb.append("\n")
        sb.append(body)
    }
    return sb
}
