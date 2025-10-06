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
import com.app.chat.core.notifications.NotificationManager as CustomNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
     * Se llama cuando se recibe un mensaje push mientras la app está en segundo plano o cerrada
     * IMPORTANTE: Este método solo se ejecuta si el mensaje FCM contiene SOLO datos (no notification)
     * Si el mensaje contiene tanto 'notification' como 'data', Android maneja automáticamente
     * la notificación cuando la app está cerrada, y este método solo se ejecuta en background
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Mensaje recibido de: ${remoteMessage.from}")
        Log.d(TAG, "Estado de la app: ${if (isAppInForeground()) "Primer plano" else "Background/Cerrada"}")
        
        val isAppInForeground = isAppInForeground()
        
        // Verificar si el mensaje contiene datos
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${remoteMessage.data}")
            
            // Extraer información del mensaje
            val senderName = remoteMessage.data["senderName"] ?: "Usuario desconocido"
            val messageText = remoteMessage.data["messageText"] ?: "Nuevo mensaje"
            val chatId = remoteMessage.data["chatId"] ?: ""
            val title = remoteMessage.data["title"] ?: senderName
            val body = remoteMessage.data["body"] ?: messageText
            
            // SIEMPRE mostrar notificación para mensajes con datos, independientemente del estado de la app
            // Usar notificación de alta prioridad si la app está cerrada/background
            if (isAppInForeground) {
                showNotification(title, body, chatId, senderName, messageText)
            } else {
                // App cerrada o en background: usar notificación de máxima prioridad
                createHighPriorityNotification(title, body, chatId)
            }
        }
        
        // Verificar si el mensaje contiene una notificación (título y cuerpo)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Título de notificación: ${notification.title}")
            Log.d(TAG, "Cuerpo de notificación: ${notification.body}")
            
            val title = notification.title ?: "Nuevo mensaje"
            val body = notification.body ?: "Tienes un nuevo mensaje"
            
            // SIEMPRE mostrar notificación personalizada, incluso si Android ya mostró una
            // Esto asegura que nuestras notificaciones aparezcan con el icono correcto
            if (isAppInForeground) {
                showSimpleNotification(title, body)
            } else {
                // App cerrada: crear notificación de alta prioridad
                createHighPriorityNotification(title, body, "")
            }
        }
        
        // Si no hay datos ni notificación, crear una notificación genérica
        if (remoteMessage.data.isEmpty() && remoteMessage.notification == null) {
            Log.d(TAG, "Mensaje sin datos ni notificación, creando notificación genérica")
            if (!isAppInForeground) {
                createHighPriorityNotification("Nuevo mensaje", "Tienes un nuevo mensaje en TalkNow", "")
            }
        }
    }

    /**
     * Detecta si la aplicación está en primer plano
     * Útil para determinar cómo manejar las notificaciones
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false
        
        for (processInfo in runningAppProcesses) {
            if (processInfo.processName == packageName) {
                return processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return false
    }

    /**
     * Muestra una notificación personalizada con información del mensaje
     * Incluye el nombre del remitente, el texto del mensaje y navegación al chat
     */
    private fun showNotification(
        title: String, 
        body: String, 
        chatId: String, 
        senderName: String = title, 
        messageText: String = body
    ) {
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
            .setSmallIcon(R.drawable.talknow) // Icono personalizado talknow.png
            .setContentTitle(title) // Título de la notificación
            .setContentText(body) // Cuerpo de la notificación
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // Texto expandible
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridad alta
            .setAutoCancel(true) // Se cierra al tocarla
            .setContentIntent(pendingIntent) // Acción al tocar
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sonido, vibración, luz por defecto
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Categoría de mensaje
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // Visibilidad privada
            .build()
        
        // Mostrar la notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Notificación mostrada: $title - $body")
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
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Crea el canal de notificaciones necesario para Android 8.0 (API 26) y superior
     * Los canales permiten al usuario controlar las notificaciones por categoría
     * Configurado con máxima importancia para asegurar que aparezcan cuando la app está cerrada
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Permitir notificaciones incluso en modo No Molestar
                canShowBadge()
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Canal de notificaciones creado con máxima importancia: $CHANNEL_ID")
        }
    }

    /**
     * Crea una notificación de alta prioridad optimizada para cuando la app está cerrada
     * Utiliza configuraciones específicas para asegurar que la notificación aparezca
     */
    private fun createHighPriorityNotification(title: String, body: String, chatId: String) {
        // Crear el canal de notificaciones
        createNotificationChannel()
        
        // Intent para abrir la app cuando se toque la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (chatId.isNotEmpty()) {
                putExtra("chatId", chatId)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Crear notificación con configuraciones optimizadas para app cerrada
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.talknow)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX) // Prioridad máxima
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(false) // Permitir múltiples alertas
            .setShowWhen(true) // Mostrar timestamp
            .setWhen(System.currentTimeMillis())
            .build()
        
        // Mostrar la notificación
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        Log.d(TAG, "Notificación de alta prioridad creada: $title - $body")
    }

    /**
     * Envía el token FCM al servidor para poder enviar notificaciones dirigidas
     * Guarda el token en Firestore asociado al usuario actual
     */
    private fun sendTokenToServer(token: String) {
        Log.d(TAG, "Guardando token FCM: $token")
        
        // Guardar token localmente para persistencia
        val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            
            // Crear datos del token con timestamp
            val tokenData = mapOf(
                "fcmToken" to token,
                "lastUpdated" to System.currentTimeMillis(),
                "deviceInfo" to android.os.Build.MODEL
            )
            
            // Guardar el token en el documento del usuario con reintentos
            saveTokenWithRetry(userId, tokenData, 0)
        } else {
            Log.w(TAG, "No hay usuario autenticado, guardando token localmente")
        }
    }
    
    /**
     * Guarda el token con reintentos en caso de fallo
     */
    private fun saveTokenWithRetry(userId: String, tokenData: Map<String, Any>, attempt: Int) {
        if (attempt >= 3) {
            Log.e(TAG, "Máximo número de reintentos alcanzado para guardar token FCM")
            return
        }
        
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(tokenData)
            .addOnSuccessListener {
                Log.d(TAG, "Token FCM guardado en Firestore para usuario: $userId (intento ${attempt + 1})")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al guardar token FCM en Firestore (intento ${attempt + 1})", exception)
                
                // Si el documento no existe, intentar crearlo
                if (exception.message?.contains("No document to update") == true) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "Token FCM creado en nuevo documento para usuario: $userId")
                        }
                        .addOnFailureListener { createException ->
                            Log.e(TAG, "Error al crear documento con token FCM", createException)
                            // Reintentar después de un delay
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                saveTokenWithRetry(userId, tokenData, attempt + 1)
                            }, 2000L * (attempt + 1))
                        }
                } else {
                    // Reintentar después de un delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        saveTokenWithRetry(userId, tokenData, attempt + 1)
                    }, 2000L * (attempt + 1))
                }
            }
    }
}