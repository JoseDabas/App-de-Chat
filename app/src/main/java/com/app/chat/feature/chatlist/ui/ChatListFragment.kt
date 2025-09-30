package com.app.chat.feature.chatlist.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private val vm: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar + Logout
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.title = getString(R.string.title_chats)
        toolbar?.menu?.clear()
        toolbar?.inflateMenu(R.menu.menu_chat_list)
        toolbar?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut()
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.chatListFragment, true)
                    .build()
                findNavController().navigate(R.id.loginFragment, null, navOptions)
                true
            } else false
        }

        // Recycler
        val rv = view.findViewById<RecyclerView>(R.id.rvChats)
        val tvEmpty = view.findViewById<View>(R.id.tvEmpty)
        adapter = ChatAdapter { chat ->
            findNavController().navigate(
                R.id.action_chatList_to_chat,
                Bundle().apply { putString("chatId", chat.id) }
            )
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 1) Forzar creación/actualización del chat de prueba y luego cargar
        forceSeedNow {
            vm.load()
        }

        // 2) Observa datos
        viewLifecycleOwner.lifecycleScope.launch {
            vm.chats.collectLatest { list ->
                adapter.submitList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Crea/actualiza SIEMPRE el documento chats/seed_<uid> y llama a onDone() al terminar.
     * Si falla, muestra un Toast con el motivo.
     */
    private fun forceSeedNow(onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "No hay usuario autenticado.", Toast.LENGTH_SHORT).show()
            onDone()
            return
        }
        val db = FirebaseFirestore.getInstance()
        val docId = "seed_$uid"
        val docRef = db.collection("chats").document(docId)

        val data = hashMapOf(
            "id" to docId,
            "title" to "Chat de prueba",
            "lastMessage" to "¡Bienvenido al chat de prueba!",
            "participants" to listOf(uid, "BOT"),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // set() sin condición: si no existe lo crea; si existe, lo sobreescribe.
        docRef.set(data)
            .addOnSuccessListener {
                onDone()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Seed error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
                onDone()
            }
    }
}
