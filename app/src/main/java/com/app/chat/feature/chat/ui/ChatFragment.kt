package com.app.chat.feature.chat.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnImage: ImageButton
    private lateinit var adapter: MessageAdapter

    private val chatId: String by lazy {
        requireArguments().getString("chatId") ?: error("chatId requerido")
    }

    private val usersEmailCache = mutableMapOf<String, String>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { encodeAndSendBase64(it) }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbarChat)
        rv = view.findViewById(R.id.rvMessages)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        btnImage = view.findViewById(R.id.btnImage)

        // Back button
        toolbar.navigationIcon =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = MessageAdapter(currentUid = auth.currentUser?.uid.orEmpty())
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter

        preloadParticipantsThenListen()

        btnSend.setOnClickListener { sendTextMessage() }
        btnImage.setOnClickListener { pickImage.launch("image/*") }
    }

    private fun formatTime(ts: com.google.firebase.Timestamp?): String {
        if (ts == null) return ""
        val d: Date = ts.toDate()
        val is24 = DateFormat.is24HourFormat(requireContext())
        val pattern = if (is24) "HH:mm" else "hh:mm a"
        return java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(d)
    }

    private fun preloadParticipantsThenListen() {
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { chatSnap ->
                @Suppress("UNCHECKED_CAST")
                val parts = (chatSnap.get("participants") as? List<String>).orEmpty()
                if (parts.isEmpty()) {
                    attachMessagesListener()
                    return@addOnSuccessListener
                }
                var done = 0
                parts.forEach { uid ->
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userSnap ->
                            val email = (userSnap.getString("email") ?: "").ifEmpty { "usuario@$uid" }
                            usersEmailCache[uid] = email
                        }
                        .addOnCompleteListener {
                            done++
                            if (done == parts.size) attachMessagesListener()
                        }
                }
            }
            .addOnFailureListener { attachMessagesListener() }
    }

    private fun attachMessagesListener() {
        db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { d ->
                    val senderId = d.getString("senderId") ?: return@mapNotNull null
                    UiMessage(
                        id = d.id,
                        senderId = senderId,
                        senderEmail = usersEmailCache[senderId] ?: (d.getString("senderEmail") ?: senderId),
                        text = d.getString("text") ?: "",
                        imageBase64 = d.getString("imageBase64"),
                        time = formatTime(d.getTimestamp("createdAt"))
                    )
                }.orEmpty()

                adapter.submitList(list)
                if (list.isNotEmpty()) rv.scrollToPosition(list.lastIndex)
            }
    }

    private fun sendTextMessage() {
        val txt = etMessage.text.toString().trim()
        if (txt.isEmpty()) return
        val me = auth.currentUser ?: return

        val payload = hashMapOf(
            "senderId" to me.uid,
            "senderEmail" to (me.email ?: ""),
            "text" to txt,
            "imageBase64" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("chats").document(chatId)
            .collection("messages")
            .add(payload)
            .addOnSuccessListener { etMessage.setText("") }
    }

    // --- Image -> Base64 (resizes to keep Firestore doc small) ---
    private fun encodeAndSendBase64(uri: Uri) {
        val me = auth.currentUser ?: return
        val base64 = contentUriToBase64Jpeg(uri) ?: return

        val payload = hashMapOf(
            "senderId" to me.uid,
            "senderEmail" to (me.email ?: ""),
            "text" to "",
            "imageBase64" to base64,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("chats").document(chatId)
            .collection("messages")
            .add(payload)
    }

    private fun contentUriToBase64Jpeg(uri: Uri): String? {
        // 1) Decode bounds
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        // 2) Compute inSampleSize to roughly cap the longest side
        val maxSide = 1024 // px (keeps size reasonable)
        var sample = 1
        var outW = opts.outWidth
        var outH = opts.outHeight
        while (outW / sample > maxSide || outH / sample > maxSide) sample *= 2

        // 3) Decode with sample
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) } ?: return null

        // 4) Extra downscale if still huge
        val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
            val ratio = bmp.width.coerceAtLeast(bmp.height).toFloat() / maxSide
            val w = (bmp.width / ratio).toInt().coerceAtLeast(1)
            val h = (bmp.height / ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, w, h, true).also { if (it != bmp) bmp.recycle() }
        } else bmp

        // 5) Compress with adaptive quality to stay < ~350 KB
        val baos = ByteArrayOutputStream()
        var quality = 80
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        var bytes = baos.toByteArray()

        while (bytes.size > 350_000 && quality > 40) {
            baos.reset()
            quality -= 10
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            bytes = baos.toByteArray()
        }
        if (scaled != bmp) scaled.recycle()

        // 6) Encode
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun openStream(uri: Uri): InputStream? =
        requireContext().contentResolver.openInputStream(uri)
}

data class UiMessage(
    val id: String,
    val senderId: String,
    val senderEmail: String,
    val text: String,
    val imageBase64: String?,
    val time: String
)
