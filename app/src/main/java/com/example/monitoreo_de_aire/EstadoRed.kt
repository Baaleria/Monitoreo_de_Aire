package com.example.monitoreo_de_aire

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.material.snackbar.Snackbar

/**
 * Vigila la conexión a Internet del celular (necesita el permiso ACCESS_NETWORK_STATE).
 * Avisa cada vez que cambia: Wi-Fi, datos móviles, otra red o sin red.
 * Llamar a iniciar() en onStart() y a detener() en onStop().
 */
class EstadoRed(context: Context, private val alCambiar: (TipoRed) -> Unit) {

    enum class TipoRed { WIFI, DATOS_MOVILES, OTRA, SIN_RED }

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val hiloPrincipal = Handler(Looper.getMainLooper())
    private var registrado = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            avisar(tipoDe(caps))
        }

        override fun onLost(network: Network) {
            avisar(TipoRed.SIN_RED)
        }
    }

    fun iniciar() {
        if (registrado) return
        cm.registerDefaultNetworkCallback(callback)
        registrado = true
        avisar(actual())
    }

    fun detener() {
        if (!registrado) return
        registrado = false
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (ignorada: IllegalArgumentException) {
            // ya estaba desregistrado
        }
    }

    /** Estado de la red en este momento. */
    fun actual(): TipoRed {
        val red = cm.activeNetwork ?: return TipoRed.SIN_RED
        val caps = cm.getNetworkCapabilities(red) ?: return TipoRed.SIN_RED
        return tipoDe(caps)
    }

    private fun tipoDe(caps: NetworkCapabilities): TipoRed = when {
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> TipoRed.SIN_RED
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TipoRed.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TipoRed.DATOS_MOVILES
        else -> TipoRed.OTRA
    }

    // El sistema avisa desde otro hilo: se pasa al hilo principal antes de tocar la pantalla
    private fun avisar(tipo: TipoRed) {
        hiloPrincipal.post { if (registrado) alCambiar(tipo) }
    }
}

/**
 * Muestra un aviso fijo mientras no hay Internet y uno breve cuando vuelve la conexión.
 */
class AvisoConexion(private val vista: View) {

    private var avisoSinRed: Snackbar? = null
    private var estabaSinRed = false

    fun mostrar(tipo: EstadoRed.TipoRed) {
        if (tipo == EstadoRed.TipoRed.SIN_RED) {
            estabaSinRed = true
            if (avisoSinRed?.isShown != true) {
                avisoSinRed = Snackbar.make(
                    vista,
                    "📡 Sin conexión a Internet. Los datos pueden estar desactualizados.",
                    Snackbar.LENGTH_INDEFINITE
                ).also { it.show() }
            }
        } else {
            avisoSinRed?.dismiss()
            avisoSinRed = null
            if (estabaSinRed) {
                estabaSinRed = false
                Snackbar.make(vista, "✅ Conexión restablecida (${nombre(tipo)})", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun nombre(tipo: EstadoRed.TipoRed): String = when (tipo) {
        EstadoRed.TipoRed.WIFI -> "Wi-Fi"
        EstadoRed.TipoRed.DATOS_MOVILES -> "datos móviles"
        else -> "otra red"
    }
}