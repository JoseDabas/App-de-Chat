package com.app.chat.core.session

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

object SessionManager {
    fun isLoggedIn(): Boolean = Firebase.auth.currentUser != null
    fun uid(): String? = Firebase.auth.currentUser?.uid
    fun logout() = Firebase.auth.signOut()
}
