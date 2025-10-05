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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio FCM que maneja las notificaciones push cuando la app está en segundo plano
 * Extiende FirebaseMessagingService para recibir mensajes de Firebase Cloud Messaging
 */
class ChatFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "chat_messages"
        private const val CHANNEL_NAME = "Mensajes de Chat"
        private const val CHANNEL_DESCRIPTION = "Notificaciones de nuevos mensajes"
        private const val NOTIFICATION_ID = 1001
    }

    /**
     * Se llama cuando se recibe un nuevo token FCM
     * Este token identifica de manera única la instalación de la app
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")
        
        // TODO: Enviar el token al servidor para poder enviar notificaciones dirigidas
        // Por ahora solo lo registramos en los logs
        sendTokenToServer(token)
    }

    /**
     * Se llama cuando se recibe un mensaje push mientras la app está en segundo plano
     * Si la app está en primer plano, este método no se ejecuta
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Mensaje recibido de: ${remoteMessage.from}")
        
        // Verificar si el mensaje contiene datos
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${remoteMessage.data}")
            
            // Extraer información del mensaje
            val senderName = remoteMessage.data["senderName"] ?: "Usuario desconocido"
            val messageText = remoteMessage.data["messageText"] ?: "Nuevo mensaje"
            val chatId = remoteMessage.data["chatId"] ?: ""
            
            // Mostrar notificación personalizada
            showNotification(senderName, messageText, chatId)
        }
        
        // Verificar si el mensaje contiene una notificación (título y cuerpo)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Título de notificación: ${notification.title}")
            Log.d(TAG, "Cuerpo de notificación: ${notification.body}")
            
            // Mostrar notificación simple
            showSimpleNotification(
                notification.title ?: "Nuevo mensaje",
                notification.body ?: "Tienes un nuevo mensaje"
            )
        }
    }

    /**
     * Crea y muestra una notificación personalizada con información del mensaje
     */
    private fun showNotification(senderName: String, messageText: String, chatId: String) {
        // Crear el canal de notificaciones (necesario para Android 8.0+)
        createNotificationChannel()
        
        // Intent para abrir la app cuando se toque la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Agregar datos extra para navegar directamente al chat
            putExtra("chatId", chatId)
            putExtra("openFromNotification", true)
        }
        
        // PendingIntent para manejar el toque en la notificación
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Construir la notificación
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono del sistema Android
            .setContentTitle(senderName) // Nombre del remitente
            .setContentText(messageText) // Texto del mensaje
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText)) // Texto expandible
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridad alta
            .setAutoCancel(true) // Se cierra al tocarla
            .setContentIntent(pendingIntent) // Acción al tocar
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sonido, vibración, luz por defecto
            .build()
        
        // Mostrar la notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Notificación mostrada: $senderName - $messageText")
    }

    /**
     * Muestra una notificación simple con título y cuerpo
     */
    private fun showSimpleNotification(title: String, body: String) {
        createNotificationChannel()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono temporal
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Crea el canal de notificaciones para Android 8.0 (API 26) y superior
     * Los canales permiten al usuario controlar las notificaciones por categoría
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true) // Habilitar luz LED
                enableVibration(true) // Habilitar vibración
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Canal de notificaciones creado: $CHANNEL_ID")
        }
    }

    /**
     * Envía el token FCM al servidor para poder enviar notificaciones dirigidas
     * TODO: Implementar la lógica para enviar el token a tu backend
     */
    private fun sendTokenToServer(token: String) {
        // Aquí deberías enviar el token a tu servidor/base de datos
        // para poder enviar notificaciones dirigidas a este dispositivo
        Log.d(TAG, "Token enviado al servidor: $token")
        
        // Ejemplo de cómo podrías guardarlo en Firestore:
        // val userId = FirebaseAuth.getInstance().currentUser?.uid
        // if (userId != null) {
        //     FirebaseFirestore.getInstance()
        //         .collection("users")
        //         .document(userId)
        //         .update("fcmToken", token)
        // }
    }
}