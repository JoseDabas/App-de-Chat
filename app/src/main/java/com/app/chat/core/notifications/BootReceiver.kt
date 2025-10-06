package com.app.chat.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.example.appchat.BackgroundService

/**
 * Se ejecuta tras reinicios del dispositivo para:
 *  - Reactivar el servicio en primer plano que mantiene FCM estable.
 *  - Forzar la reobtención del token (por si el SO mató procesos).
 */
class BootReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "BootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "BootReceiver: $action")

        // Distintos vendors usan distintas acciones; cubrimos las comunes.
        val isBoot =
            action == Intent.ACTION_BOOT_COMPLETED ||
                    action == "android.intent.action.QUICKBOOT_POWERON" ||
                    action == Intent.ACTION_LOCKED_BOOT_COMPLETED

        if (!isBoot) return

        // 1) Levantar servicio de fondo (FCM estable / token refresco periódico).
        runCatching { BackgroundService.startService(context) }
            .onFailure { Log.e(TAG, "No se pudo iniciar BackgroundService tras boot", it) }

        // 2) Forzar obtención de token FCM (no guarda, solo verifica que esté vivo).
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d(TAG, "FCM token tras boot: ${it.result}")
                } else {
                    Log.w(TAG, "No se pudo obtener token tras boot", it.exception)
                }
            }
        }.onFailure { Log.e(TAG, "Error obteniendo token tras boot", it) }
    }
}
