package com.example.monitoreo_de_aire

import android.content.Context
import com.google.firebase.database.DataSnapshot

/**
 * Guarda cuál dispositivo está seleccionado (por usuario) en el teléfono.
 * El Dashboard y Ventilación leen y escriben datos del dispositivo seleccionado.
 */
object DispositivoActivo {

    private const val PREFS = "monitoreo_prefs"

    private fun clave(uid: String) = "dispositivo_activo_$uid"

    fun obtener(context: Context, uid: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(clave(uid), null)

    fun guardar(context: Context, uid: String, codigo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(clave(uid), codigo).apply()
    }

    /** Del resultado de la consulta por owner_uid devuelve el dispositivo seleccionado, o el primero si no hay uno válido. */
    fun elegir(context: Context, uid: String, snapshot: DataSnapshot): DataSnapshot? {
        val activo = obtener(context, uid)
        return snapshot.children.firstOrNull { it.key == activo } ?: snapshot.children.firstOrNull()
    }
}