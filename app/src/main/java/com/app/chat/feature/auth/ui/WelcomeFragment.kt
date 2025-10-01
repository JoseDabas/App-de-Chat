package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.app.chat.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

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