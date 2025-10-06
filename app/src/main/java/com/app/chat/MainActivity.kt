package com.app.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.app.chat.core.session.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import com.example.appchat.BackgroundService

// Activity raíz que hospeda el NavHostFragment y decide a qué pantalla navegar
// según el estado de la sesión y la autenticación de Firebase.
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        // Obtiene el NavController del host declarado en activity_main.xml
        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment).navController

        // Usuario actual de Firebase (null si no hay sesión iniciada)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Hay sesión de Firebase: verificar expiración manual de la sesión local.
            if (SessionPrefs.isExpired(this)) {
                // Si nuestra política local de sesión expiró:
                // 1) cerrar sesión de Firebase
                // 2) limpiar marca local
                // 3) navegar a login limpiando el back stack del grafo
                FirebaseAuth.getInstance().signOut()
                SessionPrefs.clear(this)
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    navController.navigate(
                        R.id.loginFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true) // elimina historial hasta el startDestination del grafo
                            .build()
                    )
                }
            } else {
                // Sesión válida: si estamos en pantallas de bienvenida o login, saltar a la lista de chats.
                val currentDestination = navController.currentDestination?.id
                if (currentDestination == R.id.welcomeFragment || currentDestination == R.id.loginFragment) {
                    navController.navigate(
                        R.id.chatListFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true) // limpia historial para evitar volver a auth con "atrás"
                            .build()
                    )
                }
            }
        }
        // Si user == null no se navega aquí: el gráfico empieza en welcomeFragment (startDestination).
    }
}
