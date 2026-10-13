package com.example.monitoreo_de_aire

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas del cifrado AES-GCM con Android Keystore. Requieren celular o emulador. */
@RunWith(AndroidJUnit4::class)
class CifradoKeystoreTest {

    private val contexto: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cifrarYDescifrar_devuelveElMismoTexto() {
        val cifrado = CifradoKeystore.cifrar("AIRE-001")
        assertNotNull(cifrado)
        assertEquals("AIRE-001", CifradoKeystore.descifrar(cifrado!!))
    }

    @Test
    fun elTextoPlanoNoApareceEnElCifrado() {
        val cifrado = CifradoKeystore.cifrar("AIRE-001")!!
        assertFalse(cifrado.contains("AIRE-001"))
    }

    @Test
    fun dosCifradosDelMismoTexto_sonDistintos() {
        // Cada cifrado usa un IV nuevo
        assertNotEquals(CifradoKeystore.cifrar("AIRE-001"), CifradoKeystore.cifrar("AIRE-001"))
    }

    @Test
    fun datoAlterado_noSeDescifra() {
        val bytes = Base64.decode(CifradoKeystore.cifrar("AIRE-001")!!, Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        assertNull(CifradoKeystore.descifrar(Base64.encodeToString(bytes, Base64.NO_WRAP)))
    }

    @Test
    fun basura_devuelveNull() {
        assertNull(CifradoKeystore.descifrar("esto-no-es-un-dato-cifrado"))
        assertNull(CifradoKeystore.descifrar(""))
    }

    @Test
    fun dispositivoActivo_seGuardaCifradoYSeRecupera() {
        DispositivoActivo.guardar(contexto, "uid-prueba", "AIRE-001")

        assertEquals("AIRE-001", DispositivoActivo.obtener(contexto, "uid-prueba"))

        // En el archivo de preferencias NO debe estar el código en texto plano
        val crudo = contexto.getSharedPreferences("monitoreo_prefs", Context.MODE_PRIVATE)
            .getString("dispositivo_activo_uid-prueba", null)
        assertNotNull(crudo)
        assertNotEquals("AIRE-001", crudo)
    }
}