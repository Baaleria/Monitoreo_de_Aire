package com.example.monitoreo_de_aire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas unitarias de las reglas de validación (se ejecutan en el computador, sin celular). */
class ValidacionTest {

    // ---- Contraseña
    @Test
    fun password_corta_esRechazada() {
        assertNotNull(Validacion.errorPassword("abc123"))
    }

    @Test
    fun password_sinNumero_esRechazada() {
        assertNotNull(Validacion.errorPassword("soloLetrasAqui"))
    }

    @Test
    fun password_sinLetra_esRechazada() {
        assertNotNull(Validacion.errorPassword("12345678"))
    }

    @Test
    fun password_valida_esAceptada() {
        assertNull(Validacion.errorPassword("Clave1234"))
    }

    @Test
    fun password_conEspacios_noSeRecorta() {
        // Los espacios cuentan como caracteres de la contraseña
        assertNull(Validacion.errorPassword("ab 12 345"))
    }

    @Test
    fun password_mensajeIndicaMinimo() {
        assertEquals(
            "⚠️ La contraseña debe tener al menos ${Validacion.MIN_PASSWORD} caracteres",
            Validacion.errorPassword("a1")
        )
    }

    // ---- Correo
    @Test
    fun correo_valido() {
        assertTrue(Validacion.correoValido("valeria@gmail.com"))
        assertTrue(Validacion.correoValido("nombre.apellido+tag@dominio.cl"))
    }

    @Test
    fun correo_invalido() {
        assertFalse(Validacion.correoValido(""))
        assertFalse(Validacion.correoValido("sinarroba.com"))
        assertFalse(Validacion.correoValido("usuario@"))
        assertFalse(Validacion.correoValido("usuario@dominio"))
        assertFalse(Validacion.correoValido("con espacio@dominio.com"))
    }

    // ---- Código de dispositivo (llave de Firebase)
    @Test
    fun codigo_valido() {
        assertTrue(Validacion.codigoDispositivoValido("AIRE-001"))
    }

    @Test
    fun codigo_conCaracteresProhibidos_esRechazado() {
        assertFalse(Validacion.codigoDispositivoValido("AIRE/001"))
        assertFalse(Validacion.codigoDispositivoValido("AIRE.001"))
        assertFalse(Validacion.codigoDispositivoValido("AI"))
        assertFalse(Validacion.codigoDispositivoValido(""))
    }
}