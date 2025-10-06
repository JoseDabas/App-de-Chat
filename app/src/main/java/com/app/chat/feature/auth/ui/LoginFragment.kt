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
 * Pantalla de inicio de sesión.
 *
 * Responsabilidades:
 * - Autenticar con correo/contraseña usando FirebaseAuth.
 * - Registrar la marca de sesión local (para mantener la sesión).
 * - Garantizar que exista el documento del usuario en /users/{uid}.
 * - Actualizar el token de FCM tras un login exitoso.
 * - Navegar a la lista de chats limpiando el back stack de autenticación.
 */
class LoginFragment : Fragment() {

    // Instancias perezosas de Firebase (Auth y Firestore) para evitar costos hasta su uso.
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    /**
     * Infla el layout de la vista asociado al fragment.
     * No hace lógica de negocio; solo crea la jerarquía de vistas.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_login, container, false)

    /**
     * Configura listeners y lógica de interacción con la UI.
     * Aquí se capturan los clicks y se disparan las operaciones de login.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias a controles del layout
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPass = view.findViewById<EditText>(R.id.etPass)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = view.findViewById<Button>(R.id.btnGoToRegister)

        // Acción de iniciar sesión con FirebaseAuth
        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Evita dobles taps mientras se procesa la autenticación
            btnLogin.isEnabled = false
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    // Rehabilita el botón cuando termina el proceso
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        // Marca la sesión como activa (persistencia local con SharedPreferences)
                        SessionPrefs.markLoggedNow(requireContext())

                        // Asegura que exista el documento del usuario en Firestore
                        seedMyUserDoc(
                            onDone = {
                                // Actualiza token FCM (para notificaciones push)
                                NotificationManager.updateFCMTokenForUser()

                                // Navegación a ChatList limpiando pantallas previas de auth
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
                        // Muestra la causa del fallo si está disponible
                        Toast.makeText(
                            requireContext(),
                            task.exception?.localizedMessage ?: "Error de login",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Vuelve a la pantalla de bienvenida (no registra ni autentica)
        btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.welcomeFragment)
        }
    }

    /**
     * Crea el documento del usuario en /users/{uid} si aún no existe.
     *
     * Motivo:
     * - La app consulta datos de usuarios desde Firestore (email/displayName). Este método
     *   garantiza que el usuario autenticado tenga su registro mínimo creado.
     *
     * Flujo:
     * - Lee el documento actual; si existe, finaliza con éxito.
     * - Si no existe, crea un documento con email (en minúsculas) y un displayName por defecto.
     * - Ejecuta [onDone] si todo va bien o [onError] ante cualquier fallo.
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
