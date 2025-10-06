package com.app.chat.core.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Worker para manejar la sincronización de tokens FCM con reintentos
 * 
 * Utiliza WorkManager para garantizar que los tokens se sincronicen
 * con el servidor incluso si la app está cerrada o hay problemas de conectividad
 */
class TokenSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TokenSyncWorker"
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override suspend fun doWork(): Result {
        return try {
            val userId = inputData.getString("userId") ?: return Result.failure()
            val fcmToken = inputData.getString("fcmToken") ?: return Result.failure()
            val deviceInfo = inputData.getString("deviceInfo") ?: "Unknown Device"
            val appVersion = inputData.getString("appVersion") ?: "unknown"

            Log.d(TAG, "Intentando sincronizar token FCM para usuario: $userId")

            val tokenData = mapOf(
                "fcmToken" to fcmToken,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "localTimestamp" to System.currentTimeMillis(),
                "deviceInfo" to deviceInfo,
                "appVersion" to appVersion,
                "platform" to "android"
            )

            // Intentar actualizar el documento del usuario
            try {
                firestore.collection("users")
                    .document(userId)
                    .update(tokenData)
                    .await()

                Log.d(TAG, "Token FCM sincronizado exitosamente con Firestore")
                Result.success()

            } catch (e: Exception) {
                Log.w(TAG, "Error actualizando, intentando crear documento", e)

                // Si falla la actualización, intentar crear/merge el documento
                firestore.collection("users")
                    .document(userId)
                    .set(tokenData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Token FCM creado exitosamente en nuevo documento")
                Result.success()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando token FCM", e)
            
            // Determinar si debemos reintentar o fallar permanentemente
            when {
                e.message?.contains("network", ignoreCase = true) == true -> {
                    Log.w(TAG, "Error de red, programando reintento")
                    Result.retry()
                }
                runAttemptCount < 3 -> {
                    Log.w(TAG, "Reintentando sincronización (intento ${runAttemptCount + 1}/3)")
                    Result.retry()
                }
                else -> {
                    Log.e(TAG, "Máximo número de reintentos alcanzado, fallando permanentemente")
                    Result.failure()
                }
            }
        }
    }
}