package com.app.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.app.chat.core.session.SessionPrefs
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onStart() {
        super.onStart()

        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment).navController

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            if (SessionPrefs.isExpired(this)) {
                // Venció la sesión: cerrar y mandar a login
                FirebaseAuth.getInstance().signOut()
                SessionPrefs.clear(this)
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    navController.navigate(
                        R.id.loginFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
            } else {
                // Sesión vigente: llevar directo a la lista de chats
                val currentDestination = navController.currentDestination?.id
                if (currentDestination == R.id.welcomeFragment || currentDestination == R.id.loginFragment) {
                    navController.navigate(
                        R.id.chatListFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
            }
        } // Si user == null, se queda en welcomeFragment (startDestination)
    }
}
