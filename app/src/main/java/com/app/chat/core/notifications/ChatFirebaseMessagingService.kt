package com.app.chat.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.chat.MainActivity
import com.app.chat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Servicio FCM:
 *  - onNewToken: almacena local y refleja en Firestore el token.
 *  - onMessageReceived: muestra notificaciones para mensajes de tipo "data".
 * Se diseñó defensivo para evitar crashes por nulos/errores en background.
 */
class ChatFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ChatFCMService"

        // Canales de notificación (Android O+).
        private const val CH_MESSAGES_ID = "chat_messages"
        private const val CH_MESSAGES_NAME = "Mensajes de Chat"
        private const val CH_GENERAL_ID = "general_notifications"
        private const val CH_GENERAL_NAME = "Notificaciones Generales"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fs by lazy { FirebaseFirestore.getInstance() }

    /**
     * Se dispara cuando FCM rota o emite un token nuevo para este dispositivo.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")

        scope.launch {
            saveTokenSafely(token)
        }
    }

    /**
     * Recibe mensajes de tipo "data". Los "notification" puros los maneja el sistema.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM de: ${remoteMessage.from}")

        // Evitar cualquier crash: procesar en hilo IO y con try-catch.
        scope.launch {
            try {
                handleDataMessage(remoteMessage.data)
            } catch (e: Exception) {
                Log.e(TAG, "Error manejando mensaje FCM", e)
            }
        }
    }

    /**
     * Guarda el token en Firestore bajo /users/{uid}, con merge.
     */
    private suspend fun saveTokenSafely(token: String) {
        val user = runCatching { auth.currentUser }.getOrNull() ?: return
        val doc = fs.collection("users").document(user.uid)

        val payload = mapOf(
            "fcmToken" to token,
            "fcmLastUpdated" to FieldValue.serverTimestamp(),
            "platform" to "android"
        )

        runCatching { doc.set(payload, com.google.firebase.firestore.SetOptions.merge()).await() }
            .onSuccess { Log.d(TAG, "Token FCM reflejado en Firestore") }
            .onFailure { Log.e(TAG, "No se pudo guardar token en Firestore", it) }
    }

    /**
     * Construye y muestra la notificación según los datos recibidos.
     * Estructura esperada (data message):
     *  - type: "chat_message" | "system" (default: chat_message)
     *  - title, body
     *  - chatId, senderId, senderName (para chat_message)
     */
    private fun handleDataMessage(data: Map<String, String>) {
        if (data.isEmpty()) {
            Log.w(TAG, "Mensaje FCM sin data, se ignora")
            return
        }

        val type = data["type"] ?: "chat_message"
        val title = data["title"] ?: "Nuevo mensaje"
        val body = data["body"] ?: "Tienes un mensaje"
        ensureChannels()

        when (type) {
            "chat_message" -> {
                val chatId = data["chatId"].orEmpty()
                val senderId = data["senderId"].orEmpty()
                val senderName = data["senderName"] ?: "Usuario"
                showChatNotification(
                    title = senderName,
                    body = body,
                    chatId = chatId,
                    senderId = senderId
                )
            }
            "system" -> {
                showGeneralNotification(
                    title = title,
                    body = body
                )
            }
            else -> {
                // Por compatibilidad, si no reconocemos el tipo, tratamos como general.
                showGeneralNotification(
                    title = title,
                    body = body
                )
            }
        }
    }

    /**
     * Notificación para mensajes de chat; al tocarla abre MainActivity con extras del chat.
     */
    private fun showChatNotification(title: String, body: String, chatId: String, senderId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", chatId)
            putExtra("senderId", senderId)
            putExtra("openFromNotification", true)
        }

        val pending = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CH_MESSAGES_ID)
            .setSmallIcon(R.drawable.talknow) // usa tu ícono válido
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(chatId.hashCode(), notif)
    }

    /**
     * Notificación genérica (avisos del sistema u otros).
     */
    private fun showGeneralNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            this,
            /*id*/ System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CH_GENERAL_ID)
            .setSmallIcon(R.drawable.talknow)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    /**
     * Crea los canales si hacen falta (idempotente).
     */
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val chMessages = NotificationChannel(
            CH_MESSAGES_ID,
            CH_MESSAGES_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de nuevos mensajes"
            enableVibration(true)
            setShowBadge(true)
        }

        val chGeneral = NotificationChannel(
            CH_GENERAL_ID,
            CH_GENERAL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones generales"
            enableVibration(true)
            setShowBadge(true)
        }

        nm.createNotificationChannel(chMessages)
        nm.createNotificationChannel(chGeneral)
    }
}
