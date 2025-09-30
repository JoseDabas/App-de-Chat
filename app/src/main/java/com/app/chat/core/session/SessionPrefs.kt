package com.app.chat.core.session

import android.content.Context
import kotlin.math.abs

object SessionPrefs {
    private const val PREFS = "session_prefs"
    private const val KEY_LOGIN_AT = "login_at" // epoch millis
    private const val DEFAULT_EXPIRY_DAYS = 30
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun markLoggedNow(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LOGIN_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOGIN_AT)
            .apply()
    }

    fun isExpired(ctx: Context, maxDays: Int = DEFAULT_EXPIRY_DAYS): Boolean {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LOGIN_AT, 0L)
        if (saved == 0L) return true
        val diff = abs(System.currentTimeMillis() - saved)
        return diff > maxDays * DAY_MILLIS
    }
}
