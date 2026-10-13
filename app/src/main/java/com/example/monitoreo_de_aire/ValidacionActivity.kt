package com.example.monitoreo_de_aire

/**
 * Reglas de validación de credenciales y datos de entrada
 * (control de acceso / autenticación / validación de datos).
 * Se usan en el login, el registro y la vinculación, para que las reglas sean las mismas.
 * No depende de Android, así que se puede probar con tests unitarios.
 */
object Validacion {

    const val MIN_PASSWORD = 8

    private val REGEX_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Los códigos de dispositivo son llaves de Firebase: no pueden tener . $ # [ ] /
    private val REGEX_CODIGO = Regex("^[A-Za-z0-9_-]{3,30}$")

    fun correoValido(correo: String): Boolean = REGEX_CORREO.matches(correo)

    fun codigoDispositivoValido(codigo: String): Boolean = REGEX_CODIGO.matches(codigo)

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