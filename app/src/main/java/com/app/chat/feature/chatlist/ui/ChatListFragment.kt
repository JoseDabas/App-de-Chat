package com.app.chat.feature.chatlist.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.EditText
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private val vm: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Views del layout que MANTENEMOS tal cual tu XML
        val rv = view.findViewById<RecyclerView>(R.id.rvChats)
        val tvEmpty = view.findViewById<View>(R.id.tvEmpty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add)
        val contextMenu = view.findViewById<LinearLayout>(R.id.context_menu)
        val menuChat = view.findViewById<TextView>(R.id.menu_chat)
        val menuContact = view.findViewById<TextView>(R.id.menu_contact)
        val menuGroup = view.findViewById<TextView>(R.id.menu_group)
        val menuBroadcast = view.findViewById<TextView>(R.id.menu_broadcast)
        val menuTeam = view.findViewById<TextView>(R.id.menu_team)

        // ***** Agregar entrada "Logout" al context_menu sin tocar tu XML *****
        val menuLogout = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = "Logout"
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            setTextColor(requireContext().getColor(android.R.color.black))
            textSize = 14f
            setBackgroundResource(android.R.drawable.list_selector_background)
            typeface = Typeface.DEFAULT
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Cerrar sesión y volver a login
                FirebaseAuth.getInstance().signOut()
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.chatListFragment, true)
                    .build()
                try {
                    findNavController().navigate(R.id.loginFragment, null, navOptions)
                } catch (_: Exception) {
                    requireActivity().finish()
                }
            }
        }
        // Lo añadimos al final del menú contextual
        contextMenu.addView(menuLogout)

        // Adapter
        adapter = ChatAdapter { chat ->
            findNavController().navigate(
                R.id.action_chatList_to_chat,
                Bundle().apply { putString("chatId", chat.id) }
            )
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Sembrar/asegurar documento del usuario actual en /users/{uid}
        seedMyUserDoc()

        // Cargar lista
        vm.load()

        // Observa cambios
        viewLifecycleOwner.lifecycleScope.launch {
            vm.chats.collectLatest { list ->
                adapter.submitList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // FAB abre/cierra el menú contextual
        fab.setOnClickListener {
            contextMenu.isVisible = !contextMenu.isVisible
        }

        // Al tocar fuera, cerramos menú contextual
        view.setOnClickListener { v ->
            if (v.id != R.id.context_menu && contextMenu.isVisible) {
                contextMenu.isVisible = false
            }
        }

        // Acciones del menú (puedes personalizarlas después)
        menuChat.setOnClickListener {
            contextMenu.isVisible = false
            showNewChatDialog()
        }
        menuContact.setOnClickListener {
            contextMenu.isVisible = false
            Toast.makeText(requireContext(), "Contact (pendiente)", Toast.LENGTH_SHORT).show()
        }
        menuGroup.setOnClickListener {
            contextMenu.isVisible = false
            Toast.makeText(requireContext(), "Group (pendiente)", Toast.LENGTH_SHORT).show()
        }
        menuBroadcast.setOnClickListener {
            contextMenu.isVisible = false
            Toast.makeText(requireContext(), "Broadcast (pendiente)", Toast.LENGTH_SHORT).show()
        }
        menuTeam.setOnClickListener {
            contextMenu.isVisible = false
            Toast.makeText(requireContext(), "Team (pendiente)", Toast.LENGTH_SHORT).show()
        }
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

        // Buscar el usuario por email en /users
        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
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
                        val title = "Chat con $email"
                        val data = hashMapOf(
                            "id" to chatId,
                            "title" to title,
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

    private fun dp(value: Int): Int {
        val d = requireContext().resources.displayMetrics.density
        return (value * d).toInt()
    }
}
