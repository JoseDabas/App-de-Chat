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

            // Validación de campos vacíos
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor ingresa tu email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor ingresa tu contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validación de formato de email
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Por favor ingresa un email válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = authRepo.login(email, pass)) {
                    is Result.Success -> {
                        Toast.makeText(requireContext(), "¡Inicio de sesión exitoso!", Toast.LENGTH_SHORT).show()
                        
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
                        // Manejo específico de errores de Firebase
                        val errorMessage = when {
                            res.exception.message?.contains("password is invalid") == true -> 
                                "Contraseña incorrecta"
                            res.exception.message?.contains("no user record") == true -> 
                                "No existe una cuenta con este email"
                            res.exception.message?.contains("badly formatted") == true -> 
                                "Email mal formateado"
                            res.exception.message?.contains("network error") == true -> 
                                "Error de conexión. Verifica tu internet"
                            else -> "Error de inicio de sesión: ${res.exception.message ?: "Error desconocido"}"
                        }
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }
}
