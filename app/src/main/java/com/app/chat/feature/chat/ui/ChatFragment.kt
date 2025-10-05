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
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.app.chat.core.notifications.NotificationManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val rtdb by lazy { 
        val url = getString(R.string.rtdb_url)
        FirebaseDatabase.getInstance(url)
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnImage: ImageButton
    private lateinit var adapter: MessageAdapter
    
    // Referencias a los elementos de la toolbar personalizada
    private lateinit var ivUserIcon: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView

    private val chatId: String by lazy {
        requireArguments().getString("chatId") ?: error("chatId requerido")
    }

    private val usersEmailCache = mutableMapOf<String, String>()
    private val usersDisplayNameCache = mutableMapOf<String, String>()
    private var previousMessageCount = 0 // Para detectar nuevos mensajes
    private var otherUserId: String? = null
    
    // Referencias para el listener de presencia
    private var presenceRef: DatabaseReference? = null
    private var presenceListener: ValueEventListener? = null

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
        
        // Inicializar referencias a los elementos de la toolbar personalizada
        ivUserIcon = view.findViewById(R.id.ivUserIcon)
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserStatus = view.findViewById(R.id.tvUserStatus)

        // Back button
        toolbar.navigationIcon =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = MessageAdapter(currentUid = auth.currentUser?.uid.orEmpty())
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter

        loadChatInfoAndSetupToolbar()
        preloadParticipantsThenListen()

        btnSend.setOnClickListener { sendTextMessage() }
        btnImage.setOnClickListener { pickImage.launch("image/*") }
    }

    private fun loadChatInfoAndSetupToolbar() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { chatSnap ->
                @Suppress("UNCHECKED_CAST")
                val participants = (chatSnap.get("participants") as? List<String>).orEmpty()
                
                // Encontrar el ID del otro usuario (no el actual)
                otherUserId = participants.firstOrNull { it != currentUserId }
                
                otherUserId?.let { userId ->
                    loadUserInfo(userId)
                    setupPresenceListener(userId)
                }
            }
    }

    private fun loadUserInfo(userId: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userSnap ->
                val displayName = userSnap.getString("displayName") 
                    ?: userSnap.getString("email")?.substringBefore("@") 
                    ?: "Usuario"
                
                tvUserName.text = displayName
            }
            .addOnFailureListener {
                tvUserName.text = "Usuario"
            }
    }

    private fun loadUserDisplayName(userId: String, callback: (String) -> Unit) {
        // Si ya está en cache, usar el valor cacheado
        usersDisplayNameCache[userId]?.let { 
            callback(it)
            return 
        }
        
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userSnap ->
                val email = userSnap.getString("email") ?: ""
                val displayName = userSnap.getString("displayName") 
                    ?: email.substringBefore("@").ifEmpty { "Usuario" }
                
                // Cachear tanto email como displayName
                usersEmailCache[userId] = email
                usersDisplayNameCache[userId] = displayName
                
                callback(displayName)
            }
            .addOnFailureListener {
                val fallbackName = "Usuario"
                usersDisplayNameCache[userId] = fallbackName
                callback(fallbackName)
            }
    }

    private fun setupPresenceListener(userId: String) {
        // Limpiar listener anterior si existe
        presenceRef?.let { ref ->
            presenceListener?.let { ref.removeEventListener(it) }
        }
        
        val ref = rtdb.getReference("presence").child(userId)
        ref.keepSynced(true)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Aplicar la misma lógica que en ChatListFragment
                val direct = snapshot.getValue(Boolean::class.java)
                val subOnline = snapshot.child("online").getValue(Boolean::class.java)
                val state = snapshot.child("state").getValue(String::class.java)

                val online = when {
                    direct != null -> direct
                    subOnline != null -> subOnline
                    state != null -> state.equals("online", true)
                    else -> false
                }
                
                val status = if (online) "Online" else "Offline"
                val statusColor = if (online) R.color.turquoise_blue else android.R.color.darker_gray
                
                tvUserStatus.text = status
                tvUserStatus.setTextColor(resources.getColor(statusColor, null))
                
                Log.d("ChatFragment", "Presence [$userId] -> $online")
            }

            override fun onCancelled(error: DatabaseError) {
                tvUserStatus.text = "Offline"
                tvUserStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                Log.w("ChatFragment", "Presence listener cancelled: ${error.message}")
            }
        }
        
        ref.addValueEventListener(listener)
        presenceRef = ref
        presenceListener = listener
    }

    private fun formatTime(ts: com.google.firebase.Timestamp?): String {
        if (ts == null) return ""
        val d: Date = ts.toDate() // Firebase Timestamp ya convierte a zona horaria local
        val pattern = "HH:mm" // Siempre usar formato de 24 horas
        val formatter = java.text.SimpleDateFormat(pattern, Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("GMT-4") // Zona horaria de República Dominicana (UTC-4)
        return formatter.format(d)
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
                val documents = snap?.documents ?: return@addSnapshotListener
                
                // Crear lista temporal de mensajes sin displayName
                val tempMessages = documents.mapNotNull { d ->
                    val senderId = d.getString("senderId") ?: return@mapNotNull null
                    UiMessage(
                        id = d.id,
                        senderId = senderId,
                        senderEmail = usersEmailCache[senderId] ?: (d.getString("senderEmail") ?: senderId),
                        senderDisplayName = usersDisplayNameCache[senderId] ?: "Cargando...",
                        text = d.getString("text") ?: "",
                        imageBase64 = d.getString("imageBase64"),
                        time = formatTime(d.getTimestamp("createdAt"))
                    )
                }
                
                // Cargar displayNames para usuarios que no están en cache
                val uniqueSenderIds = tempMessages.map { it.senderId }.distinct()
                var loadedCount = 0
                val totalToLoad = uniqueSenderIds.count { !usersDisplayNameCache.containsKey(it) }
                
                if (totalToLoad == 0) {
                    // Todos los displayNames ya están cargados
                    processMessages(tempMessages)
                } else {
                    // Cargar displayNames faltantes
                    uniqueSenderIds.forEach { senderId ->
                        if (!usersDisplayNameCache.containsKey(senderId)) {
                            loadUserDisplayName(senderId) { displayName ->
                                loadedCount++
                                if (loadedCount == totalToLoad) {
                                    // Todos los displayNames han sido cargados, actualizar mensajes
                                    val updatedMessages = tempMessages.map { message ->
                                        message.copy(senderDisplayName = usersDisplayNameCache[message.senderId] ?: "Usuario")
                                    }
                                    processMessages(updatedMessages)
                                }
                            }
                        }
                    }
                }
            }
    }
    
    private fun processMessages(messages: List<UiMessage>) {
        // Detectar nuevos mensajes de otros usuarios para mostrar notificaciones
        val currentUserId = auth.currentUser?.uid
        if (messages.size > previousMessageCount && previousMessageCount > 0) {
            // Hay nuevos mensajes, verificar si son de otros usuarios
            val newMessages = messages.drop(previousMessageCount)
            newMessages.forEach { message ->
                // Solo mostrar notificación si el mensaje no es del usuario actual
                if (message.senderId != currentUserId) {
                    val messageText = if (message.text.isNotEmpty()) {
                        message.text
                    } else if (message.imageBase64 != null) {
                        "📷 Imagen"
                    } else {
                        "Nuevo mensaje"
                    }
                    
                    // Mostrar notificación local (cuando la app está en primer plano)
                    NotificationManager.showLocalNotification(
                        requireContext(),
                        message.senderDisplayName,
                        messageText,
                        chatId
                    )
                }
            }
        }
        
        previousMessageCount = messages.size
        adapter.submitList(messages)
        if (messages.isNotEmpty()) rv.scrollToPosition(messages.lastIndex)
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar el listener de presencia
        presenceRef?.let { ref ->
            presenceListener?.let { ref.removeEventListener(it) }
        }
        presenceRef = null
        presenceListener = null
    }
}

data class UiMessage(
    val id: String,
    val senderId: String,
    val senderEmail: String,
    val senderDisplayName: String,
    val text: String,
    val imageBase64: String?,
    val time: String
)
