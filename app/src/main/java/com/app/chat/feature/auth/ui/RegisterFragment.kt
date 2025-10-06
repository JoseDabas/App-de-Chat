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
import com.app.chat.core.session.SessionPrefs
import com.app.chat.core.notifications.NotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

/**
 * Pantalla de registro de cuentas.
 *
 * Funciones principales:
 * - Crear un usuario con correo y contraseña usando FirebaseAuth.
 * - Guardar una marca local de sesión para conservar el login.
 * - Asegurar el documento de usuario en Firestore (/users/{uid}).
 * - Actualizar el token FCM tras el registro exitoso (para notificaciones).
 * - Navegar a la lista de chats limpiando el back stack de autenticación.
 */
class RegisterFragment : Fragment() {

    // Proveedores de backend. Se inicializan bajo demanda.
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    /**
     * Infla el layout asociado al fragmento. No incluye lógica de negocio.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_register, container, false)

    /**
     * Inicializa referencias a vistas y listeners de UI.
     * Maneja el flujo de registro y la navegación posterior.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias de UI
        val etEmail = view.findViewById<EditText>(R.id.etEmailReg)
        val etPass = view.findViewById<EditText>(R.id.etPassReg)
        val btnRegister = view.findViewById<Button>(R.id.btnRegister)
        val btnBack = view.findViewById<Button>(R.id.btnBackReg)

        // Acción de registro con email/contraseña
        btnRegister.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString()?.trim().orEmpty()

            // Validación mínima en cliente
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Evita taps repetidos durante la llamada de red
            btnRegister.isEnabled = false
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    // Reactiva el botón al finalizar
                    btnRegister.isEnabled = true

                    if (task.isSuccessful) {
                        // Marca de sesión local para mantener al usuario autenticado
                        SessionPrefs.markLoggedNow(requireContext())

                        // Garantiza documento del usuario en Firestore
                        seedMyUserDoc(
                            onDone = {
                                // Refresca/almacena token FCM del usuario recién creado
                                NotificationManager.updateFCMTokenForUser()

                                // Navega a la lista de chats eliminando pantallas previas de auth
                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.welcomeFragment, true)
                                    .build()
                                findNavController().navigate(R.id.chatListFragment, null, navOptions)
                            },
                            onError = {
                                // Incluso si falla el seed, se intenta actualizar token y continuar
                                NotificationManager.updateFCMTokenForUser()

                                val navOptions = NavOptions.Builder()
                                    .setPopUpTo(R.id.welcomeFragment, true)
                                    .build()
                                findNavController().navigate(R.id.chatListFragment, null, navOptions)
                            }
                        )
                    } else {
                        // Muestra el motivo del fallo si está disponible
                        Toast.makeText(
                            requireContext(),
                            task.exception?.localizedMessage ?: "Error al registrar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Vuelve a la pantalla de bienvenida (no realiza autenticación)
        btnBack.setOnClickListener {
            findNavController().navigate(R.id.welcomeFragment)
        }
    }

    /**
     * Crea el documento del usuario en /users/{uid} si aún no existe.
     *
     * Por qué es necesario:
     * - La app usa Firestore para obtener información de perfil (email, displayName).
     *   Este método asegura que, tras registrarse, el usuario tenga su ficha creada.
     *
     * Detalles:
     * - Lee el doc; si ya existe, termina con éxito.
     * - Si no existe, escribe email en minúsculas y un displayName por defecto (parte local del correo).
     * - Llama a [onDone] si todo va bien; de lo contrario, ejecuta [onError].
     */
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
