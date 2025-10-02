package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class RegisterFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_register, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<EditText>(R.id.etEmailReg)
        val etPass = view.findViewById<EditText>(R.id.etPassReg)
        val btnRegister = view.findViewById<Button>(R.id.btnRegister)
        val btnBack = view.findViewById<Button>(R.id.btnBackReg)

        btnRegister.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    btnRegister.isEnabled = true
                    if (task.isSuccessful) {
                        seedMyUserDoc(
                            onDone = {
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.welcomeFragment, true)
                                    .build()
                                findNavController().navigate(R.id.chatListFragment, null, navOptions)
                            },
                            onError = {
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.welcomeFragment, true)
                                    .build()
                                findNavController().navigate(R.id.chatListFragment, null, navOptions)
                            }
                        )
                    } else {
                        Toast.makeText(requireContext(), task.exception?.localizedMessage ?: "Error al registrar", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        btnBack.setOnClickListener {
            findNavController().navigate(R.id.welcomeFragment)
        }
    }

    private fun seedMyUserDoc(onDone: () -> Unit, onError: () -> Unit) {
        val user = auth.currentUser ?: return onError()
        val uid = user.uid
        val emailLower = (user.email ?: return onError()).lowercase(Locale.getDefault())
        val display = user.displayName ?: emailLower.substringBefore("@")

        val ref = db.collection("users").document(uid)
        ref.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onDone(); return@addOnSuccessListener
                }
                val data = hashMapOf(
                    "email" to emailLower,
                    "displayName" to display
                )
                ref.set(data)
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { onError() }
            }
            .addOnFailureListener { onError() }
    }
}
