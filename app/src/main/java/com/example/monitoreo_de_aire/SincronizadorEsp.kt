package com.example.monitoreo_de_aire

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SincronizadorEsp {
    private val hilo = Handler(Looper.getMainLooper())
    private var activo = false
    private var propietarioActual: Any? = null
    private var refActiva: DatabaseReference? = null
    private var buffer: BufferLecturas? = null
    var hayRed = true
    var onDatosNuevos: ((ConexionEsp32.DatosEsp32) -> Unit)? = null

    fun iniciar(context: Context, propietario: Any, ref: DatabaseReference) {
        propietarioActual = propietario
        refActiva = ref
        if (buffer == null) buffer = BufferLecturas(context.applicationContext)
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
                val pendientes = ConexionEsp32.obtenerBuffer()
                if (datos != null) hilo.post { onDatosNuevos?.invoke(datos) }

                val dbLocal = buffer
                if (pendientes != null && dbLocal != null) {
                    for (l in pendientes) dbLocal.agregar(l.valor, l.ms)
                }

                val ref = refActiva
                if (hayRed && ref != null && dbLocal != null) {
                    val lote = dbLocal.primeras(20)
                    val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    if (lote.isNotEmpty()) {
                        for ((valor, _) in lote) {
                            ref.child("ultima_deteccion").setValue(
                                mapOf(
                                    "valor" to valor,
                                    "fecha" to fmt.format(Date()),
                                    "fecha_ms" to ServerValue.TIMESTAMP
                                )
                            )
                            ref.child("mq135_valor").setValue(valor)
                        }
                        if (ConexionEsp32.confirmarSync(lote.size)) {
                            dbLocal.borrarPrimeras(lote.size)
                        }
                    } else if (datos != null) {
                        ref.child("ultima_deteccion").setValue(
                            mapOf(
                                "valor" to datos.mq135,
                                "fecha" to fmt.format(Date()),
                                "fecha_ms" to ServerValue.TIMESTAMP
                            )
                        )
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
                                "automatico" to datos.automatico,
                                "motivo" to datos.motivo
                            )
                        )
                    }
                }
                if (activo) hilo.postDelayed(this, 5000L)
            }.start()
        }
    }
}