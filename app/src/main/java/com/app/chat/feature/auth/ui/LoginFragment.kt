package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.app.chat.R
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
                when (authRepo.login(email, pass)) {
                    is Result.Success -> {
                        findNavController().navigate(R.id.action_login_to_chatList)
                    }
                    is Result.Error -> {
                        Toast.makeText(requireContext(), "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
