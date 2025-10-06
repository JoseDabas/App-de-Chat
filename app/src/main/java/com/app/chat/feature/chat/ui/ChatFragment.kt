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

/**
 * Pantalla de conversación.
 *
 * - Muestra mensajes (texto e imagen) ordenados por fecha.
 * - Permite enviar texto e imágenes (como Base64 en Firestore).
 * - Pinta el nombre y estado (Online/Offline) del otro participante usando RTDB.
 * - Lanza notificaciones locales cuando llegan mensajes nuevos de otra persona.
 */
class ChatFragment : Fragment(R.layout.fragment_chat) {

    // Autenticación y acceso a Firestore/RTDB
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val rtdb by lazy {
        // La URL de RTDB se toma de strings.xml para apuntar explícitamente al proyecto correcto.
        val url = getString(R.string.rtdb_url)
        FirebaseDatabase.getInstance(url)
    }

    // Referencias a vistas
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnImage: ImageButton
    private lateinit var adapter: MessageAdapter

    // Vistas de la cabecera personalizada del chat
    private lateinit var ivUserIcon: ImageView
    private lateinit var tvUserName: TextView
    private lateinit var tvUserStatus: TextView

    // ID del chat actual, recibido por argumentos de navegación
    private val chatId: String by lazy {
        requireArguments().getString("chatId") ?: error("chatId requerido")
    }

    // Caches para evitar lecturas repetidas de datos de usuario
    private val usersEmailCache = mutableMapOf<String, String>()
    private val usersDisplayNameCache = mutableMapOf<String, String>()

    // Control de conteo de mensajes para detectar nuevos y disparar notificaciones locales
    private var previousMessageCount = 0

    // UID del otro participante (no el usuario actual)
    private var otherUserId: String? = null

    // Referencias para el listener de presencia en RTDB
    private var presenceRef: DatabaseReference? = null
    private var presenceListener: ValueEventListener? = null

    // Launcher para seleccionar imagen de la galería (Activity Result API)
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { encodeAndSendBase64(it) }
        }

    /**
     * Inicializa vistas, toolbar, adapter y listeners al crear la vista.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind de vistas
        toolbar = view.findViewById(R.id.toolbarChat)
        rv = view.findViewById(R.id.rvMessages)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        btnImage = view.findViewById(R.id.btnImage)

        // Elementos de cabecera (nombre/estado del otro usuario)
        ivUserIcon = view.findViewById(R.id.ivUserIcon)
        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserStatus = view.findViewById(R.id.tvUserStatus)

        // Botón de volver en la toolbar
        toolbar.navigationIcon =
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Configuración del RecyclerView de mensajes
        adapter = MessageAdapter(currentUid = auth.currentUser?.uid.orEmpty())
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter

        // Carga info del chat (para identificar al otro usuario) y engancha el listener de presencia
        loadChatInfoAndSetupToolbar()

        // Precarga emails de participantes y recién entonces escucha mensajes (evita "parpadeo" en UI)
        preloadParticipantsThenListen()

        // Acciones de envío de texto e imagen
        btnSend.setOnClickListener { sendTextMessage() }
        btnImage.setOnClickListener { pickImage.launch("image/*") }
    }

    /**
     * Lee el documento del chat, obtiene al otro participante y configura:
     * - Nombre a mostrar en el encabezado.
     * - Listener de presencia en RTDB para "Online / Offline".
     */
    private fun loadChatInfoAndSetupToolbar() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { chatSnap ->
                @Suppress("UNCHECKED_CAST")
                val participants = (chatSnap.get("participants") as? List<String>).orEmpty()

                // Determina el otro UID (distinto del actual)
                otherUserId = participants.firstOrNull { it != currentUserId }

                otherUserId?.let { userId ->
                    loadUserInfo(userId)
                    setupPresenceListener(userId)
                }
            }
    }

    /**
     * Carga y pinta el nombre del otro usuario en la cabecera del chat.
     * Prioriza displayName; si no existe, usa el email antes de la arroba.
     */
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

    /**
     * Obtiene (y cachea) el displayName de un usuario y lo devuelve por callback.
     * Si falla, retorna "Usuario".
     */
    private fun loadUserDisplayName(userId: String, callback: (String) -> Unit) {
        usersDisplayNameCache[userId]?.let {
            callback(it)
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { userSnap ->
                val email = userSnap.getString("email") ?: ""
                val displayName = userSnap.getString("displayName")
                    ?: email.substringBefore("@").ifEmpty { "Usuario" }

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

    /**
     * Se suscribe a cambios de presencia en RTDB para el otro usuario.
     * Soporta las variantes:
     * - nodo booleano directo: presence/{uid} = true|false
     * - subnodo online: presence/{uid}/online = true|false
     * - subnodo state: presence/{uid}/state = "online"|"offline"
     */
    private fun setupPresenceListener(userId: String) {
        // Limpia listener previo si lo hubiera
        presenceRef?.let { ref ->
            presenceListener?.let { ref.removeEventListener(it) }
        }

        val ref = rtdb.getReference("presence").child(userId)
        ref.keepSynced(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Lectura flexible del estado según estructura disponible
                val direct = snapshot.getValue(Boolean::class.java)
                val subOnline = snapshot.child("online").getValue(Boolean::class.java)
                val state = snapshot.child("state").getValue(String::class.java)

                val online = when {
                    direct != null -> direct
                    subOnline != null -> subOnline
                    state != null -> state.equals("online", true)
                    else -> false
                }

                // Pinta texto y color según estado
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

    /**
     * Formateo de hora a texto (24h), tomando Timestamp de Firestore.
     * La zona horaria se fija a UTC-4.
     */
    private fun formatTime(ts: com.google.firebase.Timestamp?): String {
        if (ts == null) return ""
        val d: Date = ts.toDate()
        val pattern = "HH:mm"
        val formatter = java.text.SimpleDateFormat(pattern, Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("GMT-4")
        return formatter.format(d)
    }

    /**
     * Precarga emails de los participantes del chat y, una vez hecho,
     * conecta el listener de mensajes para renderizar con datos completos.
     */
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

    /**
     * Escucha en tiempo real los mensajes del chat, mapea a UiMessage
     * (incluyendo hora formateada) y actualiza el adapter.
     * También dispara notificaciones locales si entran mensajes de terceros.
     */
    private fun attachMessagesListener() {
        db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val documents = snap?.documents ?: return@addSnapshotListener

                // Construye lista temporal de mensajes (displayName se resuelve después si hace falta)
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

                // Obtiene los senderIds únicos y carga displayNames faltantes (si los hay)
                val uniqueSenderIds = tempMessages.map { it.senderId }.distinct()
                var loadedCount = 0
                val totalToLoad = uniqueSenderIds.count { !usersDisplayNameCache.containsKey(it) }

                if (totalToLoad == 0) {
                    // Nada que cargar: procesar directamente
                    processMessages(tempMessages)
                } else {
                    // Cargar y, cuando terminen, refrescar con los nombres correctos
                    uniqueSenderIds.forEach { senderId ->
                        if (!usersDisplayNameCache.containsKey(senderId)) {
                            loadUserDisplayName(senderId) {
                                loadedCount++
                                if (loadedCount == totalToLoad) {
                                    val updated = tempMessages.map { m ->
                                        m.copy(senderDisplayName = usersDisplayNameCache[m.senderId] ?: "Usuario")
                                    }
                                    processMessages(updated)
                                }
                            }
                        }
                    }
                }
            }
    }

    /**
     * Actualiza la UI con la lista de mensajes y, si detecta nuevos
     * provenientes de otros usuarios, muestra una notificación local.
     */
    private fun processMessages(messages: List<UiMessage>) {
        val currentUserId = auth.currentUser?.uid

        // Si aumentó el largo y no es la primera carga, hay nuevos mensajes
        if (messages.size > previousMessageCount && previousMessageCount > 0) {
            val newMessages = messages.drop(previousMessageCount)
            newMessages.forEach { message ->
                // Notificar solo si el mensaje no es del usuario actual
                if (message.senderId != currentUserId) {
                    val messageText = if (message.text.isNotEmpty()) {
                        message.text
                    } else if (message.imageBase64 != null) {
                        "📷 Imagen"
                    } else {
                        "Nuevo mensaje"
                    }

                    NotificationManager.showLocalNotification(
                        requireContext(),
                        message.senderDisplayName,
                        messageText
                    )
                }
            }
        }

        previousMessageCount = messages.size
        adapter.submitList(messages)
        if (messages.isNotEmpty()) rv.scrollToPosition(messages.lastIndex)
    }

    /**
     * Envía un mensaje de texto al chat actual.
     * El timestamp se genera con `FieldValue.serverTimestamp()` en Firestore.
     */
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

    /**
     * Convierte una imagen (URI) a JPEG Base64 (reduciendo tamaño para mantener
     * los documentos de Firestore razonables) y la guarda como mensaje.
     */
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

    /**
     * Decodifica y reescala la imagen apuntada por [uri], comprimiendo a JPEG
     * con calidad adaptativa hasta quedar por debajo de ~350 KB. Devuelve la
     * cadena Base64 (sin saltos de línea) o `null` si falla.
     */
    private fun contentUriToBase64Jpeg(uri: Uri): String? {
        // 1) Leer dimensiones sin decodificar completamente
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        // 2) Calcular sample para limitar el lado mayor a ~1024px
        val maxSide = 1024
        var sample = 1
        var outW = opts.outWidth
        var outH = opts.outHeight
        while (outW / sample > maxSide || outH / sample > maxSide) sample *= 2

        // 3) Decodificar con el sample calculado
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) } ?: return null

        // 4) Reducción adicional si aún excede el límite
        val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
            val ratio = bmp.width.coerceAtLeast(bmp.height).toFloat() / maxSide
            val w = (bmp.width / ratio).toInt().coerceAtLeast(1)
            val h = (bmp.height / ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, w, h, true).also { if (it != bmp) bmp.recycle() }
        } else bmp

        // 5) Compresión adaptativa hasta ~350 KB
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

        // 6) Codificación Base64 sin saltos
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Abre un InputStream para el contenido apuntado por [uri].
     */
    private fun openStream(uri: Uri): InputStream? =
        requireContext().contentResolver.openInputStream(uri)

    /**
     * Limpieza de listeners de presencia al destruir la vista.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        presenceRef?.let { ref ->
            presenceListener?.let { ref.removeEventListener(it) }
        }
        presenceRef = null
        presenceListener = null
    }
}

/**
 * Modelo de mensaje preparado para la UI.
 * - Incluye displayName ya resuelto (o marcador "Cargando...")
 * - time es un texto formateado (HH:mm)
 * - imageBase64 es `null` si el mensaje es solo texto
 */
data class UiMessage(
    val id: String,
    val senderId: String,
    val senderEmail: String,
    val senderDisplayName: String,
    val text: String,
    val imageBase64: String?,
    val time: String
)
