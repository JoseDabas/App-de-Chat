package com.app.chat.core.util

/**
 * Representa el resultado de una operación que puede:
 * - **Success**: devolver un dato de tipo [T].
 * - **Error**: fallar con una excepción.
 *
 * Patrón común para exponer estados de ejecución (éxito/fracaso) sin lanzar excepciones
 * hacia capas superiores. Útil para repositorios, data sources o use-cases.
 *
 * Uso típico:
 * ```
 * when (val r = repo.fetch()) {
 *   is Result.Success -> usar r.data
 *   is Result.Error   -> manejar r.exception
 * }
 * ```
 */
sealed class Result<out T> {

    /**
     * Variante de éxito. Contiene el valor producido por la operación.
     *
     * @param data dato resultante de tipo covariante [T].
     */
    data class Success<out T>(val data: T) : Result<T>()

    /**
     * Variante de error. Encapsula la causa de la falla.
     *
     * @param exception excepción arrojada o construida al fallar la operación.
     */
    data class Error(val exception: Throwable) : Result<Nothing>()
}
