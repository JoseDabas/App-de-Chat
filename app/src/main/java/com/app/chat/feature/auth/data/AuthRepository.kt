package com.app.chat.feature.auth.data

import com.app.chat.core.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de autenticación.
 *
 * Encapsula el acceso a Firebase Authentication y expone operaciones de
 * registro, inicio/cierre de sesión y consulta del estado actual.
 *
 * Todas las operaciones suspendidas devuelven [Result] para permitir
 * manejar éxito/fracaso sin propagar excepciones a capas superiores.
 */
class AuthRepository(
    // Dependencia inyectable para facilitar pruebas; por defecto usa Firebase.auth
    private val auth: FirebaseAuth = Firebase.auth
) {
    /**
     * Registra un usuario con correo y contraseña en Firebase Authentication.
     *
     * @param email correo del usuario.
     * @param password contraseña del usuario.
     * @return [Result.Success] si se creó la cuenta; [Result.Error] con la excepción en caso de fallo.
     *
     * Detalles:
     * - `await()` convierte el Task de Firebase en una suspensión Kotlin.
     * - No devuelve el usuario; la capa superior puede consultarlo vía [uid] o `auth.currentUser`.
     */
    suspend fun register(email: String, password: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Inicia sesión con correo y contraseña en Firebase Authentication.
     *
     * @param email correo del usuario.
     * @param password contraseña del usuario.
     * @return [Result.Success] si la autenticación fue exitosa; [Result.Error] si falló.
     *
     * Notas:
     * - Tras el éxito, `auth.currentUser` queda disponible.
     */
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Indica si hay un usuario autenticado en este momento.
     *
     * @return `true` si `auth.currentUser` no es nulo, de lo contrario `false`.
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Cierra la sesión del usuario actual.
     *
     * Efectos:
     * - Limpia `auth.currentUser`.
     * - No lanza excepciones.
     */
    fun logout() { auth.signOut() }

    /**
     * Devuelve el UID del usuario actualmente autenticado, si lo hay.
     *
     * @return UID como `String` o `null` si no hay sesión activa.
     */
    fun uid(): String? = auth.currentUser?.uid
}
