package com.app.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.app.chat.core.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ChatApp : Application() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate() {
        super.onCreate()

        // **Importantísimo**: fuerza la URL exacta de tu RTDB
        try {
            FirebaseDatabase.getInstance("https://chat-basico-6147f-default-rtdb.firebaseio.com")
        } catch (e: Exception) {
            Log.w("ChatApp", "No se pudo inicializar RTDB con URL explícita", e)
        }

        // Inicializa PresenceManager (observa ciclo de vida de la app)
        PresenceManager.init(this)

        // Cambios de login/logout: ponemos online al iniciar sesión y limpiamos al salir
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // si ya estamos en foreground, esto pondrá online de inmediato
                PresenceManager.goOnline()
            } else {
                PresenceManager.clear()
            }
        }

        // Extra: por si quieres logs de foreground/background (PresenceManager ya lo hace)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d("ChatApp", "App al FOREGROUND")
            }
            override fun onStop(owner: LifecycleOwner) {
                Log.d("ChatApp", "App al BACKGROUND")
            }
        })
    }
}
