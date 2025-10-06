package com.app.chat.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
 * Firebase Cloud Messaging Service
 * 
 * Implementa las mejores prácticas de FCM para Android:
 * - Manejo correcto de tokens con persistencia y timestamp
 * - Procesamiento eficiente de mensajes sin bloquear el hilo principal
 * - Notificaciones optimizadas para diferentes estados de la app
 * - Gestión robusta de errores con reintentos
 * - Compatibilidad con Android 13+ (permisos de notificación)
 */
class ChatFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ChatFCMService"
        
        // Configuración de canales de notificación
        private const val CHANNEL_ID_MESSAGES = "chat_messages"
        private const val CHANNEL_ID_GENERAL = "general_notifications"
        private const val CHANNEL_NAME_MESSAGES = "Mensajes de Chat"
        private const val CHANNEL_NAME_GENERAL = "Notificaciones Generales"
        
        // Configuración de SharedPreferences
        private const val PREFS_NAME = "fcm_token_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_DEVICE_INFO = "device_info"
        
        // Configuración de reintentos
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_BASE = 2000L // 2 segundos base
        
        // Configuración de expiración de tokens (30 días)
        private const val TOKEN_EXPIRATION_TIME = 30L * 24L * 60L * 60L * 1000L // 30 días en ms
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val sharedPrefs by lazy { 
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) 
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /**
     * Se llama cuando se genera un nuevo token FCM
     * 
     * Escenarios cuando se llama:
     * 1. Primera instalación de la app
     * 2. Restauración de la app en un nuevo dispositivo
     * 3. Desinstalación/reinstalación de la app
     * 4. Limpieza de datos de la app
     * 5. Rotación de tokens por seguridad de FCM
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM generado")
        
        // Procesar el token de forma asíncrona para no bloquear el hilo principal
        serviceScope.launch {
            try {
                handleNewToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando nuevo token FCM", e)
            }
        }
    }

    /**
     * Se llama cuando se recibe un mensaje FCM
     * 
     * IMPORTANTE: Solo se ejecuta para mensajes de tipo 'data'
     * Los mensajes con 'notification' son manejados automáticamente por el sistema
     * cuando la app está en background/cerrada
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Mensaje FCM recibido de: ${remoteMessage.from}")
        
        // Procesar el mensaje de forma asíncrona
        serviceScope.launch {
            try {
                processIncomingMessage(remoteMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando mensaje FCM", e)
            }
        }
    }

    /**
     * Se llama cuando se eliminan mensajes en el servidor
     * Ocurre cuando hay más de 100 mensajes pendientes o el dispositivo
     * no se ha conectado a FCM en más de un mes
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "Mensajes FCM eliminados en el servidor")
        
        // Notificar al usuario que puede haber perdido mensajes
        serviceScope.launch {
            showSystemNotification(
                title = "Mensajes perdidos",
                body = "Es posible que hayas perdido algunos mensajes. Abre la app para sincronizar.",
                channelId = CHANNEL_ID_GENERAL
            )
        }
    }

    /**
     * Maneja un nuevo token FCM con persistencia local y sincronización con servidor
     */
    private suspend fun handleNewToken(token: String) {
        Log.d(TAG, "Procesando nuevo token FCM")
        
        // Verificar si el token ha cambiado
        val previousToken = sharedPrefs.getString(KEY_FCM_TOKEN, null)
        if (token == previousToken) {
            Log.d(TAG, "Token FCM no ha cambiado, actualizando timestamp")
            updateTokenTimestamp()
            return
        }
        
        // Guardar token localmente con información del dispositivo
        saveTokenLocally(token)
        
        // Sincronizar con el servidor
        syncTokenWithServer(token)
    }

    /**
     * Procesa un mensaje FCM entrante
     */
    private suspend fun processIncomingMessage(remoteMessage: RemoteMessage) {
        val messageData = remoteMessage.data
        
        if (messageData.isEmpty()) {
            Log.w(TAG, "Mensaje FCM sin datos recibido")
            return
        }
        
        Log.d(TAG, "Procesando mensaje con datos: ${messageData.keys}")
        
        // Extraer información del mensaje
        val messageType = messageData["type"] ?: "chat_message"
        val title = messageData["title"] ?: "Nuevo mensaje"
        val body = messageData["body"] ?: "Tienes un nuevo mensaje"
        val chatId = messageData["chatId"] ?: ""
        val senderId = messageData["senderId"] ?: ""
        val senderName = messageData["senderName"] ?: "Usuario"
        val messageText = messageData["messageText"] ?: body
        val timestamp = messageData["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        
        // Determinar el estado de la app
        val isAppInForeground = isAppInForeground()
        
        Log.d(TAG, "App en primer plano: $isAppInForeground")
        Log.d(TAG, "Tipo de mensaje: $messageType")
        
        // Procesar según el tipo de mensaje
        when (messageType) {
            "chat_message" -> {
                handleChatMessage(
                    title = title,
                    body = messageText,
                    chatId = chatId,
                    senderId = senderId,
                    senderName = senderName,
                    timestamp = timestamp,
                    isAppInForeground = isAppInForeground
                )
            }
            "system_notification" -> {
                handleSystemNotification(title, body, isAppInForeground)
            }
            else -> {
                Log.w(TAG, "Tipo de mensaje desconocido: $messageType")
                handleGenericMessage(title, body, isAppInForeground)
            }
        }
    }

    /**
     * Maneja mensajes de chat
     */
    private suspend fun handleChatMessage(
        title: String,
        body: String,
        chatId: String,
        senderId: String,
        senderName: String,
        timestamp: Long,
        isAppInForeground: Boolean
    ) {
        // Si la app está en primer plano, podríamos enviar un broadcast
        // en lugar de mostrar notificación (dependiendo de la UI)
        if (isAppInForeground) {
            Log.d(TAG, "App en primer plano, considerando broadcast en lugar de notificación")
            // TODO: Implementar broadcast para actualizar UI en tiempo real
        }
        
        // Crear notificación de mensaje de chat
        createChatNotification(
            title = senderName,
            body = body,
            chatId = chatId,
            senderId = senderId,
            timestamp = timestamp,
            isHighPriority = !isAppInForeground
        )
    }

    /**
     * Maneja notificaciones del sistema
     */
    private suspend fun handleSystemNotification(
        title: String,
        body: String,
        isAppInForeground: Boolean
    ) {
        showSystemNotification(
            title = title,
            body = body,
            channelId = CHANNEL_ID_GENERAL,
            isHighPriority = !isAppInForeground
        )
    }

    /**
     * Maneja mensajes genéricos
     */
    private suspend fun handleGenericMessage(
        title: String,
        body: String,
        isAppInForeground: Boolean
    ) {
        showSystemNotification(
            title = title,
            body = body,
            channelId = CHANNEL_ID_GENERAL,
            isHighPriority = !isAppInForeground
        )
    }

    /**
     * Crea una notificación específica para mensajes de chat
     */
    private suspend fun createChatNotification(
        title: String,
        body: String,
        chatId: String,
        senderId: String,
        timestamp: Long,
        isHighPriority: Boolean
    ) {
        createNotificationChannels()
        
        // Intent para abrir el chat específico
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", chatId)
            putExtra("senderId", senderId)
            putExtra("openFromNotification", true)
            putExtra("notificationTimestamp", timestamp)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(), // Usar chatId como requestCode único
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Configurar prioridad según el estado de la app
        val priority = if (isHighPriority) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.talknow)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setGroup("chat_messages") // Agrupar notificaciones de chat
            .apply {
                if (isHighPriority) {
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                    setVibrate(longArrayOf(0, 300, 200, 300))
                }
            }
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(chatId.hashCode(), notification)
        
        Log.d(TAG, "Notificación de chat creada: $title - $body")
    }

    /**
     * Muestra una notificación del sistema
     */
    private suspend fun showSystemNotification(
        title: String,
        body: String,
        channelId: String,
        isHighPriority: Boolean = false
    ) {
        createNotificationChannels()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val priority = if (isHighPriority) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.talknow)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .apply {
                if (isHighPriority) {
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
            }
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        Log.d(TAG, "Notificación del sistema creada: $title")
    }

    /**
     * Crea los canales de notificación necesarios para Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para mensajes de chat
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de nuevos mensajes de chat"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            
            // Canal para notificaciones generales
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                CHANNEL_NAME_GENERAL,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones generales de la aplicación"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(generalChannel)
            
            Log.d(TAG, "Canales de notificación creados")
        }
    }

    /**
     * Detecta si la aplicación está en primer plano
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false
        
        return runningAppProcesses.any { processInfo ->
            processInfo.processName == packageName &&
            processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    /**
     * Guarda el token FCM localmente con información del dispositivo
     */
    private fun saveTokenLocally(token: String) {
        val timestamp = System.currentTimeMillis()
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        
        sharedPrefs.edit()
            .putString(KEY_FCM_TOKEN, token)
            .putLong(KEY_TOKEN_TIMESTAMP, timestamp)
            .putString(KEY_DEVICE_INFO, deviceInfo)
            .apply()
        
        Log.d(TAG, "Token FCM guardado localmente con timestamp: $timestamp")
    }

    /**
     * Actualiza solo el timestamp del token actual
     */
    private fun updateTokenTimestamp() {
        sharedPrefs.edit()
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Timestamp del token FCM actualizado")
    }

    /**
     * Sincroniza el token con el servidor (Firestore)
     */
    private suspend fun syncTokenWithServer(token: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No hay usuario autenticado, token guardado solo localmente")
            return
        }
        
        val userId = currentUser.uid
        val timestamp = System.currentTimeMillis()
        val deviceInfo = sharedPrefs.getString(KEY_DEVICE_INFO, "Unknown Device") ?: "Unknown Device"
        
        val tokenData = mapOf(
            "fcmToken" to token,
            "lastUpdated" to FieldValue.serverTimestamp(),
            "localTimestamp" to timestamp,
            "deviceInfo" to deviceInfo,
            "appVersion" to getAppVersion(),
            "platform" to "android"
        )
        
        try {
            // Intentar actualizar el documento del usuario
            firestore.collection("users")
                .document(userId)
                .update(tokenData)
                .await()
            
            Log.d(TAG, "Token FCM sincronizado con Firestore para usuario: $userId")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error actualizando token, intentando crear documento", e)
            
            try {
                // Si falla la actualización, intentar crear/merge el documento
                firestore.collection("users")
                    .document(userId)
                    .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                
                Log.d(TAG, "Token FCM creado en nuevo documento para usuario: $userId")
                
            } catch (createException: Exception) {
                Log.e(TAG, "Error crítico guardando token FCM", createException)
                
                // Programar reintento usando WorkManager para mayor confiabilidad
                scheduleTokenSyncRetry(userId, token)
            }
        }
    }

    /**
     * Programa un reintento de sincronización del token usando WorkManager
     */
    private fun scheduleTokenSyncRetry(userId: String, token: String) {
        val workData = workDataOf(
            "userId" to userId,
            "fcmToken" to token,
            "deviceInfo" to (sharedPrefs.getString(KEY_DEVICE_INFO, "Unknown Device") ?: "Unknown Device"),
            "appVersion" to getAppVersion()
        )
        
        val retryWork = OneTimeWorkRequestBuilder<TokenSyncWorker>()
            .setInputData(workData)
            .build()
        
        WorkManager.getInstance(this).enqueue(retryWork)
        
        Log.d(TAG, "Reintento de sincronización de token programado con WorkManager")
    }

    /**
     * Obtiene la versión de la aplicación
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Verifica si el token local ha expirado
     */
    fun isTokenExpired(): Boolean {
        val tokenTimestamp = sharedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        return (currentTime - tokenTimestamp) > TOKEN_EXPIRATION_TIME
    }

    /**
     * Obtiene el token FCM actual guardado localmente
     */
    fun getCurrentToken(): String? {
        return sharedPrefs.getString(KEY_FCM_TOKEN, null)
    }
}