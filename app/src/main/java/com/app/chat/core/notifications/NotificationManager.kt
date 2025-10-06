package com.app.chat.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.chat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Utilitario de notificaciones locales y registro de token FCM.
 *  - Inicializa canales.
 *  - Solicita/obtiene token y lo sube a Firestore.
 *  - Muestra notificaciones locales (cuando la app está en foreground).
 */
object NotificationManager {

    private const val TAG = "NotificationManager"
    private const val CH_ID = "chat_messages"
    private const val CH_NAME = "Mensajes de Chat"
    private const val CH_DESC = "Notificaciones de nuevos mensajes de chat"

    /**
     * Llamar en Application.onCreate() para dejar el canal listo.
     */
    fun initialize(context: Context) {
        createChannelIfNeeded(context)
    }

    /**
     * Actualiza el token del usuario autenticado (llamar después de login).
     */
    fun updateFCMTokenForUser() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "No se pudo obtener token FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            val user = FirebaseAuth.getInstance().currentUser ?: return@addOnCompleteListener

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Token FCM actualizado en Firestore") }
                .addOnFailureListener { Log.e(TAG, "No se pudo actualizar token FCM", it) }
        }
    }

    /**
     * Limpia el token en Firestore (llamar en logout).
     */
    fun clearFCMToken() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(mapOf("fcmToken" to null), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Token FCM limpiado en Firestore") }
            .addOnFailureListener { Log.e(TAG, "No se pudo limpiar token FCM", it) }
    }

    /**
     * Muestra notificación local simple (para cuando la app está activa).
     */
    fun showLocalNotification(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Permiso POST_NOTIFICATIONS no concedido; no se muestra notificación")
                return
            }
        }

        createChannelIfNeeded(context)

        val notif = NotificationCompat.Builder(context, CH_ID)
            .setSmallIcon(R.drawable.talknow)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CH_ID, CH_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CH_DESC
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }
}
