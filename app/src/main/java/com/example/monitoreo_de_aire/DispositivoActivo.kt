package com.example.monitoreo_de_aire

import android.content.Context
import com.google.firebase.database.DataSnapshot

/**
 * Guarda cuál dispositivo está seleccionado (por usuario) en el teléfono.
 * El código se guarda CIFRADO (AES-GCM con Android Keystore), no en texto plano.
 * El Dashboard y Ventilación leen y escriben datos del dispositivo seleccionado.
 */
object DispositivoActivo {

    private const val PREFS = "monitoreo_prefs"

    private fun clave(uid: String) = "dispositivo_activo_$uid"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun obtener(context: Context, uid: String): String? {
        val cifrado = prefs(context).getString(clave(uid), null) ?: return null
        return CifradoKeystore.descifrar(cifrado)
    }

    fun guardar(context: Context, uid: String, codigo: String) {
        // Si no se puede cifrar, no se guarda nada (nunca se guarda en texto plano)
        val cifrado = CifradoKeystore.cifrar(codigo) ?: return
        prefs(context).edit().putString(clave(uid), cifrado).apply()
    }

    /** Del resultado de la consulta por owner_uid devuelve el dispositivo seleccionado, o el primero si no hay uno válido. */
    fun elegir(context: Context, uid: String, snapshot: DataSnapshot): DataSnapshot? {
        val activo = obtener(context, uid)
        return snapshot.children.firstOrNull { it.key == activo } ?: snapshot.children.firstOrNull()
    }
}