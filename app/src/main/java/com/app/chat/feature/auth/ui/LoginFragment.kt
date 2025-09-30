package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.app.chat.core.session.SessionPrefs
import com.app.chat.databinding.FragmentLoginBinding
import com.app.chat.feature.auth.data.AuthRepository
import com.app.chat.core.util.Result
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val authRepo = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoginBinding.bind(view)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPass.text.toString()

            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = authRepo.login(email, pass)) {
                    is Result.Success -> {
                        // Guardar timestamp para sesión válida por 30 días
                        SessionPrefs.markLoggedNow(requireContext())

                        // Ir a la lista de chats y limpiar el backstack
                        findNavController().navigate(
                            R.id.chatListFragment,
                            null,
                            NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true)
                                .build()
                        )
                    }
                    is Result.Error -> {
                        // Mensaje genérico para no depender de campos del Result
                        Toast.makeText(requireContext(), "Login falló", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }
}
