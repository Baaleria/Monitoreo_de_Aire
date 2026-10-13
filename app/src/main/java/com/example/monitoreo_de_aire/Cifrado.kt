package com.example.monitoreo_de_aire

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de datos sensibles guardados en el teléfono (ISO/IEC 27400: protección de datos).
 *
 * - Algoritmo: AES-256 en modo GCM (cifra y además detecta si el dato fue alterado).
 * - La clave se genera y vive dentro de Android Keystore: la app nunca la ve ni se puede
 *   copiar fuera del teléfono.
 * - Formato guardado: Base64( IV de 12 bytes + texto cifrado ). Cada cifrado usa un IV nuevo.
 */
object CifradoKeystore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "monitoreo_aire_aes_gcm"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    private fun obtenerClave(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    /** Devuelve el texto cifrado en Base64, o null si el teléfono no pudo cifrar. */
    fun cifrar(textoPlano: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerClave())
        val cifrado = cipher.doFinal(textoPlano.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + cifrado, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** Devuelve el texto original, o null si el dato no es válido, fue alterado o la clave cambió. */
    fun descifrar(textoCifrado: String): String? = try {
        val bytes = Base64.decode(textoCifrado, Base64.NO_WRAP)
        if (bytes.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                obtenerClave(),
                GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES))
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}