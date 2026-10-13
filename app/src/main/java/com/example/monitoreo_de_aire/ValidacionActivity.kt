package com.example.monitoreo_de_aire

import android.util.Patterns

/**
 * Reglas de validación de credenciales (control de acceso / autenticación).
 * Se usan en el login y en el registro para que las reglas sean las mismas.
 */
object Validacion {

    const val MIN_PASSWORD = 8

    fun correoValido(correo: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(correo).matches()

    /** Devuelve el mensaje de error, o null si la contraseña cumple las reglas. */
    fun errorPassword(password: String): String? = when {
        password.length < MIN_PASSWORD ->
            "⚠️ La contraseña debe tener al menos $MIN_PASSWORD caracteres"
        password.none { it.isLetter() } ->
            "⚠️ La contraseña debe incluir al menos una letra"
        password.none { it.isDigit() } ->
            "⚠️ La contraseña debe incluir al menos un número"
        else -> null
    }
}