package com.app.chat.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilidad simple para persistir y consultar el “estado” de sesión a nivel local.
 *
 * Qué guarda:
 * - Un timestamp (en milisegundos) del primer login exitoso del usuario en este
 *   dispositivo/app: KEY_FIRST_LOGIN_AT.
 *
 * Para qué sirve:
 * - Permite comprobar si han transcurrido 30 días desde ese primer login
 *   (criterio de “expiración” local) sin depender de red.
 * - No autentica por sí misma; sólo registra/borra una marca local complementaria
 *   a FirebaseAuth (útil para flujos de UX o lógicas de recordatorio).
 *
 * Consideraciones:
 * - Los datos se almacenan en SharedPreferences privadas de la app.
 * - La “expiración” es local al dispositivo: borrar datos de la app o hacer logout
 *   restablece el contador.
 */
object SessionManager {

    /** Nombre del archivo de preferencias privadas. */
    private const val PREFS = "session_prefs"

    /** Clave bajo la que se persiste el timestamp del primer login. */
    private const val KEY_FIRST_LOGIN_AT = "first_login_at"

    /** Ventana de validez local: 30 días expresados en milisegundos. */
    private const val MILLIS_30_DAYS = 30L * 24L * 60L * 60L * 1000L

    /**
     * Acceso perezoso al SharedPreferences de esta utilidad.
     * Modo PRIVATE: sólo la app puede leer/escribir.
     */
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Marca el instante del primer login si aún no se había registrado.
     *
     * Uso típico:
     * - Llamar inmediatamente después de un login exitoso (FirebaseAuth signIn/ signUp).
     * - No sobreescribe si ya existe; sólo fija la primera vez.
     */
    fun ensureLoginTimestamp(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_FIRST_LOGIN_AT)) {
            p.edit().putLong(KEY_FIRST_LOGIN_AT, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Indica si la marca local de sesión ha “expirado” (>= 30 días desde el primer login).
     *
     * Detalles:
     * - Si nunca se registró el primer login (valor 0), no se considera expirado.
     * - La comparación se hace contra el reloj del dispositivo.
     */
    fun isExpired(context: Context): Boolean {
        val first = prefs(context).getLong(KEY_FIRST_LOGIN_AT, 0L)
        if (first == 0L) return false
        return System.currentTimeMillis() - first >= MILLIS_30_DAYS
    }

    /**
     * Elimina la marca local de sesión.
     *
     * Uso típico:
     * - Invocar al hacer logout para que, en un siguiente inicio, vuelva a contarse
     *   el ciclo de 30 días desde cero.
     */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_FIRST_LOGIN_AT).apply()
    }
}
