package com.app.chat.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.chat.MainActivity
import com.app.chat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manager para manejar todas las operaciones relacionadas con notificaciones
 * Incluye configuración de FCM, creación de canales y envío de notificaciones locales
 */
object NotificationManager {
    
    private const val TAG = "NotificationManager"
    private const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "Mensajes de Chat"
    private const val CHANNEL_DESCRIPTION = "Notificaciones de nuevos mensajes de chat"
    
    /**
     * Inicializa el sistema de notificaciones
     * Debe llamarse al iniciar la aplicación
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Inicializando sistema de notificaciones")
        
        // Crear canal de notificaciones
        createNotificationChannel(context)
        
        // Configurar token FCM
        setupFCMToken()
        
        Log.d(TAG, "Sistema de notificaciones inicializado")
    }
    
    /**
     * Crea el canal de notificaciones para Android 8.0+
     * Los canales permiten al usuario controlar las notificaciones por categoría
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true) // Luz LED cuando llegue notificación
                enableVibration(true) // Vibración cuando llegue notificación
                setShowBadge(true) // Mostrar badge en el icono de la app
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Canal de notificaciones creado: $CHANNEL_ID")
        }
    }
    
    /**
     * Configura el token FCM y lo guarda en Firestore
     * El token identifica de manera única esta instalación de la app
     */
    private fun setupFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Error al obtener token FCM", task.exception)
                return@addOnCompleteListener
            }
            
            // Obtener el token FCM
            val token = task.result
            Log.d(TAG, "Token FCM obtenido: $token")
            
            // Guardar el token en Firestore para el usuario actual
            saveTokenToFirestore(token)
        }
    }
    
    /**
     * Guarda el token FCM en Firestore asociado al usuario actual
     * Esto permite enviar notificaciones dirigidas a dispositivos específicos
     */
    private fun saveTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            
            // Guardar el token en el documento del usuario
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "Token FCM guardado en Firestore para usuario: $userId")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al guardar token FCM en Firestore", exception)
                }
        } else {
            Log.w(TAG, "No hay usuario autenticado, no se puede guardar el token FCM")
        }
    }
    
    /**
     * Muestra una notificación local cuando la app está en primer plano
     * Se usa cuando se recibe un mensaje mientras el usuario está usando la app
     */
    fun showLocalNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatId: String = ""
    ) {
        // Verificar permisos de notificación (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Permiso de notificaciones no concedido")
                return
            }
        }
        
        // Intent para abrir la app al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", chatId)
            putExtra("openFromNotification", true)
        }
        
        // PendingIntent para manejar el toque
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // ID único basado en timestamp
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Construir la notificación
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono del sistema Android
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText)) // Texto expandible
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Categoría de mensaje
            .setAutoCancel(true) // Se cierra automáticamente al tocar
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sonido, vibración, luz
            .setWhen(System.currentTimeMillis()) // Timestamp de la notificación
            .build()
        
        // Mostrar la notificación
        try {
            NotificationManagerCompat.from(context).notify(
                System.currentTimeMillis().toInt(), // ID único para cada notificación
                notification
            )
            Log.d(TAG, "Notificación local mostrada: $senderName - $messageText")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al mostrar notificación", e)
        }
    }
    
    /**
     * Verifica si las notificaciones están habilitadas
     * Útil para mostrar diálogos pidiendo permisos
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    
    /**
     * Cancela todas las notificaciones activas
     * Útil cuando el usuario abre la app
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
        Log.d(TAG, "Todas las notificaciones canceladas")
    }
    
    /**
     * Actualiza el token FCM cuando el usuario inicia sesión
     * Debe llamarse después de un login exitoso
     */
    fun updateFCMTokenForUser() {
        Log.d(TAG, "Actualizando token FCM para usuario autenticado")
        setupFCMToken()
    }
    
    /**
     * Limpia el token FCM cuando el usuario cierra sesión
     * Debe llamarse al hacer logout
     */
    fun clearFCMToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            
            // Remover el token del documento del usuario
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", null)
                .addOnSuccessListener {
                    Log.d(TAG, "Token FCM removido de Firestore para usuario: $userId")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al remover token FCM de Firestore", exception)
                }
        }
    }
}