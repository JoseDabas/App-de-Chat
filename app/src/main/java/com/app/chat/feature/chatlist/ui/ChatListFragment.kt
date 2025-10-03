package com.app.chat.feature.chatlist.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.text.InputType
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

class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private val vm: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // RTDB con URL explícita desde strings.xml (¡verifica que coincida con la consola!)
    private val rtdb: FirebaseDatabase by lazy {
        val url = getString(R.string.rtdb_url) // ej: https://chat-basico-6147f-default-rtdb.firebaseio.com
        Log.d("ChatList", "RTDB url = $url")
        FirebaseDatabase.getInstance(url)
    }

    // Cache de filas por chatId
    private val rowsByChatId = linkedMapOf<String, UiChatRow>()

    // ÚNICO listener al nodo raíz /presence
    private var presenceRootRef: DatabaseReference? = null
    private var presenceRootListener: ChildEventListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvChats)
        val tvEmpty = view.findViewById<View>(R.id.tvEmpty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add)
        val contextMenu = view.findViewById<LinearLayout>(R.id.context_menu)
        val menuChat = view.findViewById<TextView>(R.id.menu_chat)
        val menuGroup = view.findViewById<TextView>(R.id.menu_group)
        val ivLogout = view.findViewById<ImageView>(R.id.ivLogout)

        ivLogout?.setOnClickListener {
            PresenceManager.goOffline()
            // Limpiar token FCM antes del logout
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

        adapter = ChatAdapter { row ->
            findNavController().navigate(
                R.id.action_chatList_to_chat,
                Bundle().apply { putString("chatId", row.chatId) }
            )
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        seedMyUserDoc()
        vm.load()

        // 1) Construimos filas base (email) desde Firestore
        viewLifecycleOwner.lifecycleScope.launch {
            vm.chats.collectLatest { list ->
                rowsByChatId.clear()

                val myUid = auth.currentUser?.uid
                list.forEach { chat ->
                    val participants = try {
                        @Suppress("UNCHECKED_CAST")
                        (chat.participants as? List<String>) ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    if (participants.size < 2 || myUid == null) return@forEach
                    val otherUid = participants.firstOrNull { it != myUid } ?: return@forEach

                    val baseRow = UiChatRow(
                        chatId = chat.id,
                        otherUid = otherUid,
                        otherEmail = "cargando…",
                        otherDisplayName = "cargando…",
                        presence = "Offline",
                        lastMessage = chat.lastMessage,
                        lastMessageTime = "ahora", // Por ahora, luego implementaremos el tiempo real
                        unreadCount = 0 // Por ahora, luego implementaremos el conteo real
                    )
                    rowsByChatId[chat.id] = baseRow

                    // Cargar información del último mensaje
                    loadLastMessageInfo(chat.id)

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

                // Enviamos la lista base
                adapter.submitList(rowsByChatId.values.map { it.copy() })
                tvEmpty.visibility = if (rowsByChatId.isEmpty()) View.VISIBLE else View.GONE

                // 2) (Re)conectamos el listener de RTDB raíz para presencias
                attachPresenceRootListener()
            }
        }

        fab.setOnClickListener { contextMenu.isVisible = !contextMenu.isVisible }
        view.setOnClickListener { v ->
            if (v.id != R.id.context_menu && contextMenu.isVisible) contextMenu.isVisible = false
        }
        menuChat.setOnClickListener {
            contextMenu.isVisible = false
            showNewChatDialog()
        }
        menuGroup.setOnClickListener {
            contextMenu.isVisible = false
            Toast.makeText(requireContext(), "Group (pendiente)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun attachPresenceRootListener() {
        // Limpia anterior si existía
        presenceRootRef?.let { ref ->
            presenceRootListener?.let { ref.removeEventListener(it) }
        }

        // Referencia al nodo /presence
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

            private fun applyUpdate(snapshot: DataSnapshot, removed: Boolean = false) {
                val uid = snapshot.key ?: return

                // Soportar boolean directo y/o subestructura
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

                // Actualiza cualquier fila cuyo otherUid == uid
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

    private fun updateRow(chatId: String, transform: (UiChatRow) -> UiChatRow) {
        val current = rowsByChatId[chatId] ?: return
        rowsByChatId[chatId] = transform(current)
        adapter.submitList(rowsByChatId.values.map { it.copy() })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presenceRootRef?.let { ref ->
            presenceRootListener?.let { ref.removeEventListener(it) }
        }
        presenceRootRef = null
        presenceRootListener = null
    }

    // ---------- NUEVO CHAT POR CORREO ----------
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

    private fun openChat(chatId: String) {
        findNavController().navigate(
            R.id.action_chatList_to_chat,
            Bundle().apply { putString("chatId", chatId) }
        )
    }

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

    private fun dmIdOf(a: String, b: String): String {
        return if (a < b) "dm_${a}_$b" else "dm_${b}_$a"
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 86400_000 -> { // menos de 1 día - mostrar hora en formato 24h
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeFormat.timeZone = TimeZone.getDefault() // Usar zona horaria local
                timeFormat.format(Date(timestamp))
            }
            diff < 604800_000 -> { // menos de 1 semana - mostrar día de la semana
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                dayFormat.timeZone = TimeZone.getDefault() // Usar zona horaria local
                dayFormat.format(Date(timestamp))
            }
            else -> { // más de 1 semana - mostrar fecha
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getDefault() // Usar zona horaria local
                dateFormat.format(Date(timestamp))
            }
        }
    }

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
}

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
