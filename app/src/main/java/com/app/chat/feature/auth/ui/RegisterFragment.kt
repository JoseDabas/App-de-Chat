package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.app.chat.databinding.FragmentRegisterBinding
import com.app.chat.feature.auth.data.AuthRepository
import com.app.chat.core.util.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val authRepo = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentRegisterBinding.bind(view)

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmailReg.text.toString().trim()
            val pass = binding.etPassReg.text.toString()

            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = authRepo.register(email, pass)) {
                    is Result.Success -> {
                        FirebaseAuth.getInstance().signOut()
                        findNavController().navigate(R.id.action_register_to_login)
                    }
                    is Result.Error -> {
                        Toast.makeText(requireContext(), res.exception.message ?: "Register failed", Toast.LENGTH_LONG).show()
                    }
                }

            }
        }
    }
}
