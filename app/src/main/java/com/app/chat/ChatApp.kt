package com.app.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.app.chat.core.PresenceManager
import com.app.chat.core.notifications.NotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ChatApp : Application() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate() {
        super.onCreate()

        // Inicializar Firebase Database de forma más robusta
        try {
            // Primero intentar con la instancia por defecto
            val database = FirebaseDatabase.getInstance()
            // Configurar persistencia offline
            database.setPersistenceEnabled(true)
            Log.d("ChatApp", "Firebase Database inicializado correctamente")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar Firebase Database", e)
            // Intentar con URL específica como fallback
            try {
                FirebaseDatabase.getInstance("https://chat-basico-6147f-default-rtdb.firebaseio.com")
                Log.d("ChatApp", "Firebase Database inicializado con URL específica")
            } catch (e2: Exception) {
                Log.e("ChatApp", "Error crítico al inicializar Firebase Database", e2)
            }
        }

        // Inicializar componentes de forma segura
        try {
            // Inicializa PresenceManager (observa ciclo de vida de la app)
            PresenceManager.init(this)
            Log.d("ChatApp", "PresenceManager inicializado")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar PresenceManager", e)
        }
        
        try {
            // Inicializa el sistema de notificaciones push
            NotificationManager.initialize(this)
            Log.d("ChatApp", "NotificationManager inicializado")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar NotificationManager", e)
        }

        // Cambios de login/logout: ponemos online al iniciar sesión y limpiamos al salir
        try {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    try {
                        // si ya estamos en foreground, esto pondrá online de inmediato
                        PresenceManager.goOnline()
                        // Actualizar token FCM para el usuario autenticado
                        NotificationManager.updateFCMTokenForUser()
                    } catch (e: Exception) {
                        Log.e("ChatApp", "Error en operaciones de usuario autenticado", e)
                    }
                } else {
                    try {
                        PresenceManager.clear()
                        // Limpiar token FCM al cerrar sesión
                        NotificationManager.clearFCMToken()
                    } catch (e: Exception) {
                        Log.e("ChatApp", "Error en operaciones de logout", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al configurar AuthStateListener", e)
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
