package com.app.chat.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.example.appchat.BackgroundService

/**
 * Receiver que se ejecuta cuando el dispositivo se reinicia
 * Asegura que el token FCM se mantenga actualizado después del reinicio
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver ejecutado: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Dispositivo reiniciado, reinicializando FCM y iniciando servicio background")
                
                // Iniciar el servicio en background
                try {
                    BackgroundService.startService(context)
                    Log.d(TAG, "BackgroundService iniciado después del reinicio")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar BackgroundService", e)
                }
                
                // Reinicializar el token FCM después del reinicio
                try {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w(TAG, "Error al obtener token FCM después del reinicio", task.exception)
                            return@addOnCompleteListener
                        }
                        
                        val token = task.result
                        Log.d(TAG, "Token FCM reinicializado después del reinicio: $token")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al reinicializar FCM después del reinicio", e)
                }
            }
        }
    }
}