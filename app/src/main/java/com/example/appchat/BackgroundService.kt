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

/**
 * Servicio en primer plano cuyo objetivo es:
 * 1) Mantener un proceso ligero en ejecución para favorecer la recepción de FCM.
 * 2) Verificar periódicamente el token FCM y reflejar cambios en Firestore.
 *
 * El servicio se arranca desde la MainActivity y muestra una notificación persistente.
 */
class BackgroundService : Service() {

    companion object {
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_service_channel"
        private const val CHANNEL_NAME = "Background Service"
        private const val TOKEN_CHECK_INTERVAL = 30 * 60 * 1000L // 30 minutos

        /**
         * Arranca el servicio en primer plano. En Android O+ debe usarse startForegroundService.
         */
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Detiene el servicio si está en ejecución.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            context.stopService(intent)
        }
    }

    // Programador para tareas periódicas (verificación del token FCM).
    private val handler = Handler(Looper.getMainLooper())
    private var tokenCheckRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundService creado")
        createNotificationChannel()

        // Verificación inicial del token FCM.
        checkAndUpdateFCMToken()

        // Agenda verificaciones futuras del token.
        startTokenCheckSchedule()
    }

    /**
     * Pone el servicio en primer plano con una notificación persistente.
     * START_STICKY indica al sistema que intente recrearlo si es finalizado.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Limpia programación pendiente al destruir el servicio.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BackgroundService destruido")
        tokenCheckRunnable?.let { handler.removeCallbacks(it) }
    }

    /**
     * Si el usuario remueve la tarea (swipe out), intenta reiniciar el servicio.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartServiceIntent = Intent(applicationContext, BackgroundService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }

    /**
     * Crea el canal de notificación requerido por Android O+ para notificaciones persistentes.
     */
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

    /**
     * Construye la notificación del servicio con prioridad baja y comportamiento continuo.
     * Al tocarla, abre la MainActivity.
     */
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
     * Inicia la rutina periódica para revisar si el token FCM cambió.
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
     * Obtiene el token FCM actual y, si es distinto al guardado localmente,
     * lo actualiza en Firestore y persiste el nuevo valor en SharedPreferences.
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
     * Refleja el token FCM en el documento del usuario en Firestore.
     * Si el documento no existe, lo crea con fusión (merge).
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

                    // Si no existe el documento, se crea con merge para no sobrescribir otros campos.
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
