package com.example.monitoreo_de_aire

import com.example.monitoreo_de_aire.Validacion.ErrorPassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas unitarias de las reglas de validación y sanitización (se ejecutan en el computador, sin celular). */
class ValidacionTest {

    // ---- Contraseña
    @Test
    fun password_corta_esRechazada() {
        assertEquals(ErrorPassword.CORTA, Validacion.errorPassword("abc123"))
    }

    @Test
    fun password_sinNumero_esRechazada() {
        assertEquals(ErrorPassword.SIN_NUMERO, Validacion.errorPassword("soloLetrasAqui"))
    }

    @Test
    fun password_sinLetra_esRechazada() {
        assertEquals(ErrorPassword.SIN_LETRA, Validacion.errorPassword("12345678"))
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
    fun password_limites() {
        assertNull(Validacion.errorPassword("a1" + "x".repeat(Validacion.MIN_PASSWORD - 2)))
        assertEquals(
            ErrorPassword.CORTA,
            Validacion.errorPassword("a1" + "x".repeat(Validacion.MIN_PASSWORD - 3))
        )
        assertNull(Validacion.errorPassword("a1" + "x".repeat(Validacion.MAX_PASSWORD - 2)))
        assertEquals(
            ErrorPassword.LARGA,
            Validacion.errorPassword("a1" + "x".repeat(Validacion.MAX_PASSWORD - 1))
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

    @Test
    fun correo_demasiadoLargo_esRechazado() {
        val sobrante = Validacion.MAX_CORREO - "@b.co".length
        assertTrue(Validacion.correoValido("a".repeat(sobrante) + "@b.co"))
        assertFalse(Validacion.correoValido("a".repeat(sobrante + 1) + "@b.co"))
    }

    @Test
    fun correo_conCodigoInyectado_esRechazado() {
        assertFalse(Validacion.correoValido("<script>@dominio.com"))
        assertFalse(Validacion.correoValido("a@b.com'; DROP TABLE"))
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

    // ---- Sanitización de texto libre
    @Test
    fun limpiarTexto_juntaEspaciosYRecorta() {
        assertEquals("Sala 1", Validacion.limpiarTexto("  Sala    1  "))
    }

    @Test
    fun limpiarTexto_saltosDeLineaYTabsSeVuelvenEspacio() {
        assertEquals("a b c", Validacion.limpiarTexto("a\nb\t c"))
    }

    @Test
    fun limpiarTexto_quitaCaracteresDeControl() {
        assertEquals("ab", Validacion.limpiarTexto("a\u0000b"))
    }

    @Test
    fun nombre_valido() {
        assertTrue(Validacion.nombreValido("José Núñez"))
        assertTrue(Validacion.nombreValido("Sala_1 - piso 2"))
        assertTrue(Validacion.nombreValido("a".repeat(Validacion.MAX_NOMBRE)))
    }

    @Test
    fun nombre_invalido() {
        assertFalse(Validacion.nombreValido(""))
        assertFalse(Validacion.nombreValido("a".repeat(Validacion.MAX_NOMBRE + 1)))
        assertFalse(Validacion.nombreValido("<script>alert(1)</script>"))
        assertFalse(Validacion.nombreValido("a/b"))
        assertFalse(Validacion.nombreValido("x\$y"))
        assertFalse(Validacion.nombreValido("'; DROP TABLE"))
    }

    @Test
    fun ubicacion_limites() {
        assertTrue(Validacion.ubicacionValida("Sala de estar"))
        assertTrue(Validacion.ubicacionValida("a".repeat(Validacion.MAX_UBICACION)))
        assertFalse(Validacion.ubicacionValida("a".repeat(Validacion.MAX_UBICACION + 1)))
        assertFalse(Validacion.ubicacionValida("<b>Sala</b>"))
    }

    @Test
    fun limpiarYValidar_rechazaEtiquetasHtml() {
        val texto = Validacion.limpiarTexto("   <b>Sala</b>   ")
        assertFalse(Validacion.nombreValido(texto))
    }
}