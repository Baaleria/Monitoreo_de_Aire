package com.example.monitoreo_de_aire

/**
 * Reglas de validación y sanitización de las entradas del usuario
 * (control de acceso / autenticación / validación de datos, ISO/IEC 27400).
 * Se usan en el login, el registro y la vinculación, para que las reglas sean las mismas.
 * No depende de Android, así que se puede probar con tests unitarios.
 */
object Validacion {

    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 64
    const val MAX_CORREO = 254
    const val MAX_NOMBRE = 50
    const val MAX_UBICACION = 60

    enum class ErrorPassword { CORTA, LARGA, SIN_LETRA, SIN_NUMERO }

    private val REGEX_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Los códigos de dispositivo son llaves de Firebase: no pueden tener . $ # [ ] /
    private val REGEX_CODIGO = Regex("^[A-Za-z0-9_-]{3,30}$")

    // Lista blanca para texto libre: letras (con tildes y ñ), números, espacio y . , _ ' -
    private val REGEX_TEXTO = Regex("^[\\p{L}\\p{N} ._,'-]+$")

    private val REGEX_ESPACIOS = Regex("\\s+")

    fun correoValido(correo: String): Boolean =
        correo.length <= MAX_CORREO && REGEX_CORREO.matches(correo)

    fun codigoDispositivoValido(codigo: String): Boolean = REGEX_CODIGO.matches(codigo)

    /**
     * Sanitiza texto libre: junta los espacios repetidos (y saltos de línea) en uno solo,
     * quita caracteres de control y recorta los extremos.
     */
    fun limpiarTexto(texto: String): String =
        texto.replace(REGEX_ESPACIOS, " ").filterNot { it.isISOControl() }.trim()

    /** Recibe texto ya limpiado con [limpiarTexto]. */
    fun nombreValido(nombre: String): Boolean =
        nombre.length in 1..MAX_NOMBRE && REGEX_TEXTO.matches(nombre)

    /** Recibe texto ya limpiado con [limpiarTexto]. */
    fun ubicacionValida(ubicacion: String): Boolean =
        ubicacion.length in 1..MAX_UBICACION && REGEX_TEXTO.matches(ubicacion)

    /** Devuelve el motivo del rechazo, o null si la contraseña cumple las reglas. */
    fun errorPassword(password: String): ErrorPassword? = when {
        password.length < MIN_PASSWORD -> ErrorPassword.CORTA
        password.length > MAX_PASSWORD -> ErrorPassword.LARGA
        password.none { it.isLetter() } -> ErrorPassword.SIN_LETRA
        password.none { it.isDigit() } -> ErrorPassword.SIN_NUMERO
        else -> null
    }
}