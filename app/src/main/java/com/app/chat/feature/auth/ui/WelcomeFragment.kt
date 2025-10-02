package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.app.chat.databinding.FragmentWelcomeBinding
import com.google.firebase.auth.FirebaseAuth

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onStart() {
        super.onStart()
        // Si ya hay usuario autenticado, saltar a la lista de chats y limpiar back stack.
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.welcomeFragment) return

        val options = NavOptions.Builder()
            .setPopUpTo(R.id.welcomeFragment, /* inclusive = */ true)
            .build()

        // Navegamos DIRECTO al destino por id (sin usar acciones que no existen).
        try {
            nav.navigate(R.id.chatListFragment, null, options)
        } catch (_: Exception) {
            // Si por alguna razón ese id no está en el gráfico, no hacemos nada.
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentWelcomeBinding.bind(view)

        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_register)
        }

        binding.btnLogin.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_login)
        }
    }
}
