package com.app.chat.core

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Administra el estado de presencia del usuario autenticado.
 *
 * Responsabilidades:
 * - Escuchar el ciclo de vida global del proceso (foreground/background) para
 *   marcar automáticamente el estado en línea / fuera de línea.
 * - Escribir el estado de presencia en **Realtime Database (RTDB)** y reflejarlo
 *   también en **Cloud Firestore** (campo `online` + `lastSeen`).
 * - Configurar `onDisconnect()` en RTDB para que, si la app se cierra abruptamente,
 *   el servidor marque `false` de forma automática.
 *
 * Uso:
 * - Llamar a [init] desde la clase `Application` para registrar el observer de proceso.
 * - Invocar [goOnline], [goOffline] o [clear] en momentos específicos (login, logout, etc.)
 *   si se necesita forzar el estado explícitamente.
 *
 * Notas:
 * - Si no hay usuario autenticado (`auth.currentUser == null`), las operaciones se omiten.
 * - La escritura en Firestore es informativa (reflejo) para otras partes de la app.
 */
object PresenceManager : DefaultLifecycleObserver {

    /** Acceso a la sesión de Firebase Auth. */
    private val auth by lazy { FirebaseAuth.getInstance() }

    /**
     * Referencia raíz a RTDB (URL explícita para asegurar el proyecto correcto).
     * Estructura esperada: `/presence/{uid} = true|false`
     */
    private val rtdb by lazy {
        FirebaseDatabase.getInstance("https://chat-basico-6147f-default-rtdb.firebaseio.com").reference
    }

    /** Instancia de Firestore para reflejar el estado en la colección `presence`. */
    private val fs by lazy { FirebaseFirestore.getInstance() }

    /**
     * Registra este objeto como observador del ciclo de vida del proceso.
     * Debe llamarse una sola vez, idealmente en `Application.onCreate()`.
     */
    fun init(app: Application) {
        Log.d("PresenceManager", "init()")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Callback cuando la app entra en primer plano (foreground).
     * Marca al usuario como **online**.
     */
    override fun onStart(owner: LifecycleOwner) {
        Log.d("PresenceManager", "App foreground → goOnline()")
        goOnline()
    }

    /**
     * Callback cuando la app pasa a segundo plano (background).
     * Marca al usuario como **offline**.
     */
    override fun onStop(owner: LifecycleOwner) {
        Log.d("PresenceManager", "App background → goOffline()")
        goOffline()
    }

    /** Construye la ruta `/presence/{uid}` en RTDB. */
    private fun rtdbPresenceRef(uid: String?) = rtdb.child("presence").child(uid ?: "none")

    /** Referencia al documento `/presence/{uid}` en Firestore. */
    private fun fsPresenceRef(uid: String?) = fs.collection("presence").document(uid ?: "none")

    /**
     * Marca **ONLINE** en RTDB y lo refleja en Firestore.
     *
     * Detalles:
     * - Configura `onDisconnect().setValue(false)` para revertir el estado en caso
     *   de desconexión inesperada (app muerta, pérdida de red, etc.).
     */
    fun goOnline() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "goOnline() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)

        // En caso de cierre abrupto, el servidor marcará offline automáticamente.
        r.onDisconnect().setValue(false)

        // Marca online en RTDB.
        r.setValue(true).addOnSuccessListener {
            Log.d("PresenceManager", "RTDB presence[$uid] = true")
        }.addOnFailureListener {
            Log.w("PresenceManager", "No se pudo escribir RTDB online", it)
        }

        // Reflejo en Firestore (documento presence/{uid}).
        fsPresenceRef(uid).set(
            mapOf(
                "online" to true,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnFailureListener {
            Log.w("PresenceManager", "No se pudo reflejar Firestore online", it)
        }
    }

    /**
     * Marca **OFFLINE** explícitamente y actualiza Firestore.
     *
     * Contextos típicos:
     * - Cuando la app se va a background.
     * - Antes de cerrar sesión (logout).
     */
    fun goOffline() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "goOffline() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)

        // Marca offline en RTDB y cancela cualquier onDisconnect pendiente.
        r.setValue(false).addOnSuccessListener {
            Log.d("PresenceManager", "RTDB presence[$uid] = false")
        }.addOnFailureListener {
            Log.w("PresenceManager", "No se pudo escribir RTDB offline", it)
        }
        r.onDisconnect().cancel()

        // Reflejo en Firestore.
        fsPresenceRef(uid).set(
            mapOf(
                "online" to false,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnFailureListener {
            Log.w("PresenceManager", "No se pudo reflejar Firestore offline", it)
        }
    }

    /**
     * Limpia el estado de presencia al realizar **logout**.
     *
     * Efectos:
     * - Fuerza `false` en RTDB para el usuario actual.
     * - Cancela `onDisconnect()`.
     * - Actualiza Firestore con `online=false` y `lastSeen`.
     */
    fun clear() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "clear() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)

        // Asegura dejar el estado en false al salir.
        r.setValue(false)
        r.onDisconnect().cancel()

        fsPresenceRef(uid).set(
            mapOf(
                "online" to false,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        Log.d("PresenceManager", "clear() hecho para $uid")
    }
}
