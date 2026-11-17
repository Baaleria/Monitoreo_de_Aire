package com.example.monitoreo_de_aire

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue

object SincronizadorEsp {
    private val hilo = Handler(Looper.getMainLooper())
    private var activo = false
    private var propietarioActual: Any? = null
    private var refActiva: DatabaseReference? = null
    var hayRed = true
    var onDatosNuevos: ((ConexionEsp32.DatosEsp32) -> Unit)? = null

    fun iniciar(context: Context, propietario: Any, ref: DatabaseReference) {
        propietarioActual = propietario
        refActiva = ref
        if (activo) return
        activo = true
        hilo.post(tarea)
    }

    fun detener(propietario: Any) {
        if (propietarioActual !== propietario) return
        activo = false
        propietarioActual = null
        onDatosNuevos = null
        hilo.removeCallbacks(tarea)
    }

    private val tarea = object : Runnable {
        override fun run() {
            if (!activo) return
            Thread {
                val datos = ConexionEsp32.obtenerDatos()
                if (datos != null) hilo.post { onDatosNuevos?.invoke(datos) }

                val ref = refActiva
                if (hayRed && ref != null) {
                    val pendientes = ConexionEsp32.obtenerBuffer()
                    if (!pendientes.isNullOrEmpty()) {
                        for (lectura in pendientes) {
                            ref.updateChildren(
                                mapOf(
                                    "mq135_valor" to lectura.valor,
                                    "ultimaLectura" to ServerValue.TIMESTAMP,
                                    "estadoConexion" to true
                                )
                            )
                        }
                        ConexionEsp32.confirmarSync(pendientes.size)
                    } else if (datos != null) {
                        ref.updateChildren(
                            mapOf(
                                "mq135_valor" to datos.mq135,
                                "ultimaLectura" to ServerValue.TIMESTAMP,
                                "estadoConexion" to true
                            )
                        )
                        ref.child("extractor").updateChildren(
                            mapOf(
                                "encendido" to datos.encendido,
                                "automatico" to datos.automatico
                            )
                        )
                    }
                }
                if (activo) hilo.postDelayed(this, 5000L)
            }.start()
        }
    }
}