package com.app.chat.feature.chat.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.chat.R
import com.app.chat.feature.chat.data.ChatRepository
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val repo = ChatRepository()
    private lateinit var adapter: MessageAdapter
    private var chatId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar con botón de volver
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarChat)
        toolbar?.title = getString(R.string.app_name)
        toolbar?.setNavigationIcon(R.drawable.ic_arrow_back_24)
        toolbar?.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        chatId = arguments?.getString("chatId")
        if (chatId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Chat inválido", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvMessages)
        val et = view.findViewById<EditText>(R.id.etMessage)
        val btn = view.findViewById<ImageButton>(R.id.btnSend)

        adapter = MessageAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rv.adapter = adapter

        // Stream de mensajes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.streamMessages(chatId!!).collectLatest { list ->
                    adapter.submitList(list) {
                        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
                    }
                }
            }
        }

        // Enviar mensaje
        btn.setOnClickListener {
            val text = et.text.toString()
            if (text.isBlank()) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.sendMessage(chatId!!, text)
                    et.setText("")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "No se pudo enviar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
