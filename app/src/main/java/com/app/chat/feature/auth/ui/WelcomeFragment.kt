package com.app.chat.feature.auth.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.app.chat.R
import com.app.chat.databinding.FragmentWelcomeBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla de bienvenida con dos acciones: ir a Registro o ir a Login.
 *
 * Comportamiento especial:
 * - En `onStart()` verifica si ya existe un usuario autenticado en FirebaseAuth.
 *   Si lo hay, navega directamente a la lista de chats y elimina esta pantalla
 *   del back stack para evitar volver a la bienvenida con el botón Atrás.
 *
 * Navegación:
 * - Usa IDs de destinos del nav graph (`R.id.chatListFragment`, `R.id.welcomeFragment`)
 *   para navegar de forma directa y robusta.
 * - En los botones, utiliza acciones definidas en el gráfico para ir a
 *   Registro (`action_welcome_to_register`) o Login (`action_welcome_to_login`).
 */
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    /**
     * Revisión de sesión activa cada vez que el fragmento entra en foreground.
     * Si `FirebaseAuth.currentUser` no es nulo, el usuario ya está autenticado,
     * por lo que se redirige de inmediato a la lista de chats.
     */
    override fun onStart() {
        super.onStart()

        // Obtiene el usuario actual; si no hay, no hace nada y muestra la bienvenida.
        val user = FirebaseAuth.getInstance().currentUser ?: return

        // Evita intentar navegar si este fragmento no es el destino actual.
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.welcomeFragment) return

        // Construye opciones de navegación para limpiar la bienvenida del back stack.
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.welcomeFragment, /* inclusive = */ true)
            .build()

        // Navega directo por ID al fragmento de lista de chats.
        // El try-catch protege de inconsistencias en el nav graph en tiempo de ejecución.
        try {
            nav.navigate(R.id.chatListFragment, null, options)
        } catch (_: Exception) {
            // Si el destino no existe en el gráfico actual, se ignora silenciosamente.
        }
    }

    /**
     * Inicializa binding y listeners de los botones:
     * - `btnRegister` → navega a la pantalla de registro.
     * - `btnLogin` → navega a la pantalla de inicio de sesión.
     */
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
