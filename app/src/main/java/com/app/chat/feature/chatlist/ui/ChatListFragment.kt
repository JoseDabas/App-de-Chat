package com.app.chat.feature.chatlist.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.app.chat.core.PresenceManager
import com.app.chat.core.session.SessionPrefs
import com.app.chat.core.notifications.NotificationManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla de lista de chats.
 * - Muestra conversaciones (DMs o grupos) en un RecyclerView.
 * - Integra búsqueda local por nombre, email y último mensaje.
 * - Escucha presencia de usuarios en tiempo real desde RTDB (/presence).
 * - Permite crear un nuevo chat por correo con un modal personalizado.
 * - Expone botón de logout en el header (ImageView con id ivLogout).
 */
class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private val vm: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    // Firebase Auth y Firestore para datos del usuario/chats.
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Instancia de RTDB con URL tomada de strings.xml (asegurar que coincide con el proyecto).
    private val rtdb: FirebaseDatabase by lazy {
        val url = getString(R.string.rtdb_url) // p. ej.: https://tu-proyecto-default-rtdb.firebaseio.com
        Log.d("ChatList", "RTDB url = $url")
        FirebaseDatabase.getInstance(url)
    }

    // Cache de filas actuales por chatId (fuente de verdad para la UI).
    private val rowsByChatId = linkedMapOf<String, UiChatRow>()

    // Lista base para soportar filtrado/búsqueda sin perder el conjunto original.
    private val originalChatList = mutableListOf<UiChatRow>()

    // Referencia al campo de búsqueda del layout (si existe en el XML).
    private var searchEditText: EditText? = null

    // Referencias a vistas del modal "Nuevo chat" (overlay + sheet).
    private var newChatModal: LinearLayout? = null
    private var modalOverlay: View? = null
    private var etNewChatEmail: EditText? = null
    private var btnCreateNewChat: TextView? = null
    private var btnCancelNewChat: TextView? = null

    // Listener único sobre /presence (RTDB) para recibir cambios de cualquier usuario.
    private var presenceRootRef: DatabaseReference? = null
    private var presenceRootListener: ChildEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Vistas principales del layout.
        val rv = view.findViewById<RecyclerView>(R.id.rvChats)
        val tvEmpty = view.findViewById<View>(R.id.tvEmpty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add)
        val ivLogout = view.findViewById<ImageView>(R.id.ivLogout)
        searchEditText = view.findViewById<EditText>(R.id.etSearch)

        // Vistas del modal de crear chat.
        newChatModal = view.findViewById<LinearLayout>(R.id.newChatModal)
        modalOverlay = view.findViewById<View>(R.id.modalOverlay)
        etNewChatEmail = view.findViewById<EditText>(R.id.etNewChatEmail)
        btnCreateNewChat = view.findViewById<TextView>(R.id.btnCreateNewChat)
        btnCancelNewChat = view.findViewById<TextView>(R.id.btnCancelNewChat)

        // Logout: marca offline, limpia token FCM, cierra sesión y navega a Login.
        ivLogout?.setOnClickListener {
            PresenceManager.goOffline()
            NotificationManager.clearFCMToken()
            FirebaseAuth.getInstance().signOut()
            SessionPrefs.clear(requireContext())
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.chatListFragment, true)
                .build()
            try {
                findNavController().navigate(R.id.loginFragment, null, navOptions)
            } catch (_: Exception) {
                requireActivity().finish()
            }
        }

        // RecyclerView y adaptador con callback de click para abrir el chat.
        adapter = ChatAdapter { row ->
            findNavController().navigate(
                R.id.action_chatList_to_chat,
                Bundle().apply { putString("chatId", row.chatId) }
            )
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Búsqueda local reactiva (texto -> filtra lista mostrada).
        setupSearchFunctionality(tvEmpty)

        // Garantiza que exista el documento del usuario en /users/{uid}.
        seedMyUserDoc()

        // Solicita al ViewModel que cargue los chats del usuario actual.
        vm.load()

        // Observa la lista de chats del VM y arma las filas base (email, displayName, último msg).
        viewLifecycleOwner.lifecycleScope.launch {
            vm.chats.collectLatest { list ->
                rowsByChatId.clear()

                val myUid = auth.currentUser?.uid
                list.forEach { chat ->
                    // Obtiene otros participantes (para DM, el otro usuario).
                    val participants = try {
                        @Suppress("UNCHECKED_CAST")
                        (chat.participants as? List<String>) ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    if (participants.size < 2 || myUid == null) return@forEach
                    val otherUid = participants.firstOrNull { it != myUid } ?: return@forEach

                    // Fila base con placeholders; se completan asíncronamente.
                    val baseRow = UiChatRow(
                        chatId = chat.id,
                        otherUid = otherUid,
                        otherEmail = "cargando…",
                        otherDisplayName = "cargando…",
                        presence = "Offline",
                        lastMessage = chat.lastMessage,
                        lastMessageTime = "ahora",
                        unreadCount = 0
                    )
                    rowsByChatId[chat.id] = baseRow

                    // Carga info del último mensaje (texto recortado + hora formateada).
                    loadLastMessageInfo(chat.id)

                    // Resuelve email y displayName del otro usuario desde /users/{uid}.
                    db.collection("users").document(otherUid)
                        .get()
                        .addOnSuccessListener { snap ->
                            val email = (snap.getString("email") ?: "").ifEmpty { "usuario@$otherUid" }
                            val displayName = (snap.getString("displayName") ?: "").ifEmpty {
                                email.substringBefore("@").ifEmpty { "Usuario" }
                            }
                            updateRow(chat.id) { it.copy(otherEmail = email, otherDisplayName = displayName) }
                        }
                }

                // Actualiza copia base y refresca la lista mostrada (puede aplicar filtro activo).
                originalChatList.clear()
                originalChatList.addAll(rowsByChatId.values.map { it.copy() })
                updateDisplayedList(tvEmpty)

                // Activa (o reactiva) el listener de presencia en /presence para reflejar Online/Offline.
                attachPresenceRootListener()
            }
        }

        // FAB abre el modal de "Nuevo chat".
        fab.setOnClickListener {
            showNewChatModal()
        }

        // Tocar el overlay cierra el modal.
        modalOverlay?.setOnClickListener {
            hideNewChatModal()
        }

        // Crear chat desde el modal (por correo).
        btnCreateNewChat?.setOnClickListener {
            val email = etNewChatEmail?.text?.toString()?.trim().orEmpty()
            if (email.isNotEmpty()) {
                hideNewChatModal()
                createOrOpenDirectChatByEmail(email)
            }
        }

        // Cancelar y cerrar el modal.
        btnCancelNewChat?.setOnClickListener {
            hideNewChatModal()
        }
    }

    /**
     * Suscribe un ChildEventListener único al nodo /presence en RTDB.
     * Cualquier cambio por usuario (hijo) se mapea a Online/Offline y se refleja en la fila de ese chat.
     */
    private fun attachPresenceRootListener() {
        // Limpia listener previo si existiera.
        presenceRootRef?.let { ref ->
            presenceRootListener?.let { ref.removeEventListener(it) }
        }

        // Referencia raíz de presencia.
        val ref = rtdb.getReference("presence")
        ref.keepSynced(true)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, prevKey: String?) = applyUpdate(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, prevKey: String?) = applyUpdate(snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) = applyUpdate(snapshot, removed = true)
            override fun onChildMoved(snapshot: DataSnapshot, prevKey: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w("ChatList", "RTDB /presence cancelled: ${error.message}")
            }

            // Normaliza múltiples esquemas posibles: boolean directo, {online: bool} o {state: "online"}
            private fun applyUpdate(snapshot: DataSnapshot, removed: Boolean = false) {
                val uid = snapshot.key ?: return

                val direct = snapshot.getValue(Boolean::class.java)
                val subOnline = snapshot.child("online").getValue(Boolean::class.java)
                val state = snapshot.child("state").getValue(String::class.java)

                val online = when {
                    removed -> false
                    direct != null -> direct
                    subOnline != null -> subOnline
                    state != null -> state.equals("online", true)
                    else -> false
                }

                // Actualiza todas las filas cuyo destinatario sea este uid.
                var changed = false
                rowsByChatId.forEach { (chatId, row) ->
                    if (row.otherUid == uid) {
                        rowsByChatId[chatId] = row.copy(presence = if (online) "Online" else "Offline")
                        changed = true
                    }
                }
                if (changed) {
                    adapter.submitList(rowsByChatId.values.map { it.copy() })
                }

                Log.d("ChatList", "Presence [$uid] -> $online")
            }
        }

        ref.addChildEventListener(listener)
        presenceRootRef = ref
        presenceRootListener = listener
    }

    /**
     * Aplica un cambio inmutable a una fila específica y refresca la lista (respetando filtro activo).
     */
    private fun updateRow(chatId: String, transform: (UiChatRow) -> UiChatRow) {
        val current = rowsByChatId[chatId] ?: return
        val updated = transform(current)
        rowsByChatId[chatId] = updated

        // Mantiene sincronizada la copia base para búsquedas.
        val index = originalChatList.indexOfFirst { it.chatId == chatId }
        if (index != -1) {
            originalChatList[index] = updated.copy()
        }

        // Reaplica filtro activo (si hay texto).
        val currentQuery = searchEditText?.text?.toString() ?: ""
        if (currentQuery.isNotEmpty()) {
            filterChats(currentQuery)
        } else {
            adapter.submitList(rowsByChatId.values.map { it.copy() })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Libera listener de /presence al destruir la vista (evita fugas de memoria).
        presenceRootRef?.let { ref ->
            presenceRootListener?.let { ref.removeEventListener(it) }
        }
        presenceRootRef = null
        presenceRootListener = null
    }

    // ---------- Diálogo clásico para crear chat por correo (no usado si está el modal) ----------
    private fun showNewChatDialog() {
        val input = EditText(requireContext()).apply {
            hint = "correo@dominio.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Nuevo chat")
            .setMessage("Escribe el correo del usuario:")
            .setView(input)
            .setPositiveButton("Crear") { d, _ ->
                val email = input.text?.toString()?.trim().orEmpty()
                createOrOpenDirectChatByEmail(email)
                d.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    // ---------- Modal de "Nuevo chat" (UI sobre el layout) ----------
    private fun showNewChatModal() {
        etNewChatEmail?.text?.clear()
        modalOverlay?.visibility = View.VISIBLE
        newChatModal?.visibility = View.VISIBLE
    }

    private fun hideNewChatModal() {
        modalOverlay?.visibility = View.GONE
        newChatModal?.visibility = View.GONE
        etNewChatEmail?.text?.clear()
    }

    // ---------- Diálogo para crear grupo (solo estructura básica; persistencia en Firestore) ----------
    private fun showNewGroupDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Nombre del grupo"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Nuevo grupo")
            .setMessage("Escribe el nombre del grupo:")
            .setView(input)
            .setPositiveButton("Crear") { d, _ ->
                val groupName = input.text?.toString()?.trim().orEmpty()
                if (groupName.isNotEmpty()) {
                    createNewGroup(groupName)
                } else {
                    Toast.makeText(requireContext(), "El nombre del grupo no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    /**
     * Crea un documento de grupo mínimo en /chats/{groupId}.
     * (Futuras extensiones: añadir miembros, foto, admin, etc.).
     */
    private fun createNewGroup(groupName: String) {
        val myUid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Sesión inválida", Toast.LENGTH_SHORT).show()
            return
        }

        val groupId = "group_${System.currentTimeMillis()}_$myUid"
        val groupData = hashMapOf(
            "id" to groupId,
            "title" to groupName,
            "lastMessage" to "",
            "participants" to listOf(myUid),
            "isGroup" to true,
            "createdBy" to myUid,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("chats").document(groupId)
            .set(groupData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Grupo creado: $groupName", Toast.LENGTH_SHORT).show()
                vm.load()
                openChat(groupId)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "No se pudo crear el grupo", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Crea (o abre) un chat 1:1 buscando al destinatario por email en /users.
     * Estructura de id: "dm_<uidMenor>_<uidMayor>" para evitar duplicados.
     */
    private fun createOrOpenDirectChatByEmail(emailRaw: String) {
        val email = emailRaw.lowercase(Locale.getDefault())
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Correo inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val myUid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "Sesión inválida", Toast.LENGTH_SHORT).show()
            return
        }
        val myEmail = auth.currentUser?.email?.lowercase(Locale.getDefault())
        if (email == myEmail) {
            Toast.makeText(requireContext(), "No puedes chatear contigo mismo", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snap ->
                val otherUid = snap.documents.firstOrNull()?.id
                if (otherUid == null) {
                    Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val chatId = dmIdOf(myUid, otherUid)
                val chatRef = db.collection("chats").document(chatId)

                chatRef.get().addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        openChat(chatId)
                    } else {
                        val data = hashMapOf(
                            "id" to chatId,
                            "title" to "DM",
                            "lastMessage" to "",
                            "participants" to listOf(myUid, otherUid)
                        )
                        chatRef.set(data).addOnSuccessListener {
                            Toast.makeText(requireContext(), "Chat creado", Toast.LENGTH_SHORT).show()
                            vm.load()
                            openChat(chatId)
                        }.addOnFailureListener {
                            Toast.makeText(requireContext(), "No se pudo crear el chat", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Error consultando chat", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error buscando usuario", Toast.LENGTH_SHORT).show()
            }
    }

    /** Navega al fragmento de chat para el id indicado. */
    private fun openChat(chatId: String) {
        findNavController().navigate(
            R.id.action_chatList_to_chat,
            Bundle().apply { putString("chatId", chatId) }
        )
    }

    /**
     * Crea el documento del usuario en /users/{uid} si no existe (email + displayName).
     */
    private fun seedMyUserDoc() {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: return
        val display = auth.currentUser?.displayName ?: email.substringBefore("@")

        val ref = db.collection("users").document(uid)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) return@addOnSuccessListener
            val data = hashMapOf(
                "email" to email.lowercase(Locale.getDefault()),
                "displayName" to display
            )
            ref.set(data)
        }
    }

    /**
     * Determina un id determinista para DM ordenando lexicográficamente los UIDs.
     */
    private fun dmIdOf(a: String, b: String): String {
        return if (a < b) "dm_${a}_$b" else "dm_${b}_$a"
    }

    /**
     * Devuelve una representación amigable de tiempo:
     * - < 1 día: HH:mm (24h)
     * - < 1 semana: abreviatura del día (EEE)
     * - >= 1 semana: fecha dd/MM
     */
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 86400_000 -> {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeFormat.timeZone = TimeZone.getTimeZone("GMT-4")
                timeFormat.format(Date(timestamp))
            }
            diff < 604800_000 -> {
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                dayFormat.timeZone = TimeZone.getTimeZone("GMT-4")
                dayFormat.format(Date(timestamp))
            }
            else -> {
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("GMT-4")
                dateFormat.format(Date(timestamp))
            }
        }
    }

    /**
     * Consulta el último mensaje del chat para mostrar preview y hora en la fila.
     */
    private fun loadLastMessageInfo(chatId: String) {
        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val lastMessage = querySnapshot.documents.first()
                    val text = lastMessage.getString("text") ?: ""
                    val timestamp = lastMessage.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val formattedTime = formatTime(timestamp)

                    updateRow(chatId) { row ->
                        row.copy(
                            lastMessage = if (text.length > 30) "${text.take(30)}..." else text,
                            lastMessageTime = formattedTime
                        )
                    }
                }
            }
    }

    /**
     * Configura la búsqueda local sobre la lista: filtra por displayName, email y último mensaje.
     * Además controla el estado vacío (tvEmpty) según resultados.
     */
    private fun setupSearchFunctionality(tvEmpty: View) {
        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    adapter.submitList(originalChatList.map { it.copy() })
                    tvEmpty.visibility = if (originalChatList.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    filterChats(query)
                    updateDisplayedList(tvEmpty)
                }
            }
        })
    }

    /**
     * Aplica filtro por texto a la lista original y actualiza el adaptador.
     */
    private fun filterChats(query: String) {
        val filteredList = originalChatList.filter { chat ->
            chat.otherDisplayName.contains(query, ignoreCase = true) ||
                    chat.otherEmail.contains(query, ignoreCase = true) ||
                    chat.lastMessage.contains(query, ignoreCase = true)
        }
        adapter.submitList(filteredList)
    }

    /**
     * Refresca la lista mostrada respetando el texto de búsqueda actual y
     * actualiza visibilidad del estado vacío.
     */
    private fun updateDisplayedList(tvEmpty: View) {
        val currentQuery = searchEditText?.text?.toString()?.trim() ?: ""
        if (currentQuery.isEmpty()) {
            adapter.submitList(originalChatList.map { it.copy() })
            tvEmpty.visibility = if (originalChatList.isEmpty()) View.VISIBLE else View.GONE
        } else {
            filterChats(currentQuery)
            val filteredList = originalChatList.filter { chat ->
                chat.otherDisplayName.contains(currentQuery, ignoreCase = true) ||
                        chat.otherEmail.contains(currentQuery, ignoreCase = true) ||
                        chat.lastMessage.contains(currentQuery, ignoreCase = true)
            }
            tvEmpty.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}

/**
 * Modelo de fila para el RecyclerView de la lista de chats.
 * - Contiene identificación del chat y del otro usuario.
 * - Información de cabecera (displayName/email/presencia).
 * - Preview del último mensaje + hora formateada.
 * - Contador de no leídos (placeholder para futuras mejoras).
 */
data class UiChatRow(
    val chatId: String,
    val otherUid: String,
    val otherEmail: String,
    val otherDisplayName: String,
    val presence: String,
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0
)
