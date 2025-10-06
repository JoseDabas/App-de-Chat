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

    // Referencia perezosa al estado de autenticación de Firebase.
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate() {
        super.onCreate()

        // ——— Inicialización de Realtime Database con persistencia offline ———
        // Se intenta primero con la instancia por defecto y, si falla,
        // se usa un fallback con la URL explícita del proyecto.
        try {
            val database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)
            Log.d("ChatApp", "Firebase Database inicializado correctamente")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar Firebase Database", e)
            try {
                FirebaseDatabase.getInstance("https://chat-basico-6147f-default-rtdb.firebaseio.com")
                Log.d("ChatApp", "Firebase Database inicializado con URL específica")
            } catch (e2: Exception) {
                Log.e("ChatApp", "Error crítico al inicializar Firebase Database", e2)
            }
        }

        // ——— Inicialización de componentes globales de la app ———
        // PresenceManager observa el ciclo de vida del proceso y marca online/offline.
        try {
            PresenceManager.init(this)
            Log.d("ChatApp", "PresenceManager inicializado")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar PresenceManager", e)
        }

        // NotificationManager gestiona FCM (token y notificaciones locales).
        try {
            NotificationManager.initialize(this)
            Log.d("ChatApp", "NotificationManager inicializado")
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al inicializar NotificationManager", e)
        }

        // ——— Reacción a cambios de sesión (login/logout) ———
        // Cuando hay usuario autenticado:
        //   • Se marca presencia en línea (si procede).
        //   • Se asegura que el token FCM esté actualizado.
        // Cuando no hay usuario (logout):
        //   • Se limpia la presencia y el token FCM.
        try {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    try {
                        PresenceManager.goOnline()
                        NotificationManager.updateFCMTokenForUser()
                    } catch (e: Exception) {
                        Log.e("ChatApp", "Error en operaciones de usuario autenticado", e)
                    }
                } else {
                    try {
                        PresenceManager.clear()
                        NotificationManager.clearFCMToken()
                    } catch (e: Exception) {
                        Log.e("ChatApp", "Error en operaciones de logout", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatApp", "Error al configurar AuthStateListener", e)
        }

        // ——— Logs auxiliares del ciclo de vida del proceso ———
        // Útiles para depurar transiciones foreground/background.
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
