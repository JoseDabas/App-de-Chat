package com.example.appchat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BackgroundService : Service() {
    
    companion object {
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_service_channel"
        private const val CHANNEL_NAME = "Background Service"
        private const val TOKEN_CHECK_INTERVAL = 30 * 60 * 1000L // 30 minutos
        
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            context.stopService(intent)
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var tokenCheckRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundService creado")
        createNotificationChannel()
        
        // Inicializar token FCM
        checkAndUpdateFCMToken()
        
        // Programar verificaciones periódicas del token
        startTokenCheckSchedule()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Retornar START_STICKY para que el servicio se reinicie si es terminado
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BackgroundService destruido")
        
        // Cancelar verificaciones programadas
        tokenCheckRunnable?.let { handler.removeCallbacks(it) }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Reiniciar el servicio cuando la tarea es removida
        val restartServiceIntent = Intent(applicationContext, BackgroundService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la aplicación activa para recibir mensajes"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, com.app.chat.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TalkNow activo")
            .setContentText("Listo para recibir mensajes")
            .setSmallIcon(com.app.chat.R.drawable.talknow)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Inicia la programación de verificaciones periódicas del token FCM
     */
    private fun startTokenCheckSchedule() {
        tokenCheckRunnable = object : Runnable {
            override fun run() {
                checkAndUpdateFCMToken()
                handler.postDelayed(this, TOKEN_CHECK_INTERVAL)
            }
        }
        handler.postDelayed(tokenCheckRunnable!!, TOKEN_CHECK_INTERVAL)
        Log.d(TAG, "Programación de verificación de token FCM iniciada")
    }
    
    /**
     * Verifica y actualiza el token FCM si es necesario
     */
    private fun checkAndUpdateFCMToken() {
        Log.d(TAG, "Verificando token FCM...")
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Error al obtener token FCM", task.exception)
                return@addOnCompleteListener
            }
            
            val newToken = task.result
            Log.d(TAG, "Token FCM actual: $newToken")
            
            // Verificar si el token ha cambiado
            val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val savedToken = sharedPrefs.getString("fcm_token", null)
            
            if (savedToken != newToken) {
                Log.d(TAG, "Token FCM ha cambiado, actualizando...")
                updateTokenInFirestore(newToken)
                sharedPrefs.edit().putString("fcm_token", newToken).apply()
            } else {
                Log.d(TAG, "Token FCM no ha cambiado")
            }
        }
    }
    
    /**
     * Actualiza el token en Firestore
     */
    private fun updateTokenInFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            
            val tokenData = mapOf(
                "fcmToken" to token,
                "lastUpdated" to System.currentTimeMillis(),
                "deviceInfo" to android.os.Build.MODEL,
                "updatedBy" to "BackgroundService"
            )
            
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update(tokenData)
                .addOnSuccessListener {
                    Log.d(TAG, "Token FCM actualizado en Firestore desde BackgroundService")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al actualizar token FCM desde BackgroundService", exception)
                    
                    // Si el documento no existe, crearlo
                    if (exception.message?.contains("No document to update") == true) {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "Documento de usuario creado con token FCM desde BackgroundService")
                            }
                            .addOnFailureListener { createException ->
                                Log.e(TAG, "Error al crear documento con token FCM desde BackgroundService", createException)
                            }
                    }
                }
        } else {
            Log.w(TAG, "No hay usuario autenticado para actualizar token FCM")
        }
    }
}