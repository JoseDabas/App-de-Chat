package com.app.chat.core.util

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS = "session_prefs"
    private const val KEY_FIRST_LOGIN_AT = "first_login_at"
    private const val MILLIS_30_DAYS = 30L * 24L * 60L * 60L * 1000L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Registra timestamp de login si aún no existe. */
    fun ensureLoginTimestamp(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_FIRST_LOGIN_AT)) {
            p.edit().putLong(KEY_FIRST_LOGIN_AT, System.currentTimeMillis()).apply()
        }
    }

    /** Devuelve true si ya pasaron 30 días desde el primer login. */
    fun isExpired(context: Context): Boolean {
        val first = prefs(context).getLong(KEY_FIRST_LOGIN_AT, 0L)
        if (first == 0L) return false
        return System.currentTimeMillis() - first >= MILLIS_30_DAYS
    }

    /** Limpia la marca de sesión (se llama al hacer logout). */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_FIRST_LOGIN_AT).apply()
    }
}
