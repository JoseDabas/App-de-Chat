package com.app.chat.core.session

import android.content.Context
import kotlin.math.abs

/**
 * Utilidad mínima para persistir una “marca de sesión” con SharedPreferences.
 *
 * ¿Qué guarda?
 * - Un timestamp (epoch millis) del último login exitoso: [KEY_LOGIN_AT].
 *
 * ¿Para qué sirve?
 * - Permite determinar localmente si una sesión “caducó” tras cierto número
 *   de días sin necesidad de red ni de Firebase.
 *
 * Notas:
 * - Esto NO autentica; sólo complementa la UX/flujo de sesión.
 * - El criterio de caducidad es local al dispositivo (borrar datos o cerrar
 *   sesión reinicia el contador).
 */
object SessionPrefs {

    /** Nombre del archivo de preferencias privadas. */
    private const val PREFS = "session_prefs"

    /** Clave bajo la que se persiste el instante del último login (epoch millis). */
    private const val KEY_LOGIN_AT = "login_at" // epoch millis

    /** Número de días por defecto para considerar expirada la sesión. */
    private const val DEFAULT_EXPIRY_DAYS = 30

    /** Milisegundos que tiene un día (24h). */
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Registra “ahora” como el instante del último login exitoso.
     *
     * Uso típico: llamar justo después de un sign-in/sign-up satisfactorio.
     * No valida nada ni consulta red, solo escribe en SharedPreferences.
     */
    fun markLoggedNow(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LOGIN_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Elimina la marca de último login.
     *
     * Uso típico: invocar al hacer logout para forzar que, en la próxima
     * sesión, no exista una marca previa.
     */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOGIN_AT)
            .apply()
    }

    /**
     * Indica si la sesión local debe considerarse “expirada”.
     *
     * @param maxDays número máximo de días de validez antes de caducar (30 por defecto).
     * @return `true` si han pasado más de [maxDays] días desde la marca guardada,
     *         o si no existe marca (valor 0).
     *
     * Detalles:
     * - La comparación se hace contra el reloj del dispositivo.
     * - Se usa [abs] para evitar problemas por ajustes de hora del SO.
     */
    fun isExpired(ctx: Context, maxDays: Int = DEFAULT_EXPIRY_DAYS): Boolean {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LOGIN_AT, 0L)
        if (saved == 0L) return true
        val diff = abs(System.currentTimeMillis() - saved)
        return diff > maxDays * DAY_MILLIS
    }
}
