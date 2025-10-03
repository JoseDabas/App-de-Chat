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

object PresenceManager : DefaultLifecycleObserver {

    private val auth by lazy { FirebaseAuth.getInstance() }

    // Usa RTDB con URL explícita para evitar que apunte al proyecto equivocado
    private val rtdb by lazy {
        FirebaseDatabase.getInstance("https://chat-basico-6147f-default-rtdb.firebaseio.com").reference
    }

    private val fs by lazy { FirebaseFirestore.getInstance() }

    fun init(app: Application) {
        Log.d("PresenceManager", "init()")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.d("PresenceManager", "App foreground → goOnline()")
        goOnline()
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d("PresenceManager", "App background → goOffline()")
        goOffline()
    }

    private fun rtdbPresenceRef(uid: String?) = rtdb.child("presence").child(uid ?: "none")
    private fun fsPresenceRef(uid: String?) = fs.collection("presence").document(uid ?: "none")

    /** Marca ONLINE en RTDB y también refleja en Firestore */
    fun goOnline() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "goOnline() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)
        // Si la app muere, onDisconnect pondrá false automáticamente
        r.onDisconnect().setValue(false)
        r.setValue(true).addOnSuccessListener {
            Log.d("PresenceManager", "RTDB presence[$uid] = true")
        }.addOnFailureListener {
            Log.w("PresenceManager", "No se pudo escribir RTDB online", it)
        }

        // Reflejo (opcional) Firestore
        fsPresenceRef(uid).set(
            mapOf(
                "online" to true,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnFailureListener { Log.w("PresenceManager", "No se pudo reflejar Firestore online", it) }
    }

    /** Marca OFFLINE explícitamente (útil en background/logout) y refleja en Firestore */
    fun goOffline() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "goOffline() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)
        r.setValue(false).addOnSuccessListener {
            Log.d("PresenceManager", "RTDB presence[$uid] = false")
        }.addOnFailureListener {
            Log.w("PresenceManager", "No se pudo escribir RTDB offline", it)
        }
        r.onDisconnect().cancel()

        fsPresenceRef(uid).set(
            mapOf(
                "online" to false,
                "lastSeen" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnFailureListener { Log.w("PresenceManager", "No se pudo reflejar Firestore offline", it) }
    }

    /** Limpia en logout */
    fun clear() {
        val uid = auth.currentUser?.uid ?: run {
            Log.d("PresenceManager", "clear() sin usuario, ignorado")
            return
        }
        val r = rtdbPresenceRef(uid)
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
