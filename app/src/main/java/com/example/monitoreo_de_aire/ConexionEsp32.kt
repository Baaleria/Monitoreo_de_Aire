package com.example.monitoreo_de_aire

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ConexionEsp32 {

    data class DatosEsp32(
        val mq135: Int,
        val encendido: Boolean,
        val automatico: Boolean,
        val motivo: String,
        val umbral: Int,
        val minutos: Int,
        val restanteSeg: Long,
        val servo: String
    )

    data class LecturaOffline(
        val ms: Long,
        val valor: Int
    )

    private const val IP = "172.20.10.150"
    private const val TOKEN = "monitoreo-aire-2026"

    private fun conectar(ruta: String, metodo: String, cuerpo: String? = null): Pair<Int, String> {
        val conn = URL("http://$IP$ruta").openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.requestMethod = metodo
        conn.setRequestProperty("X-Device-Token", TOKEN)
        if (cuerpo != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
        }
        val codigo = conn.responseCode
        val texto = (if (codigo in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return codigo to texto
    }

    /** Lee el estado del ESP32 incluyendo si se activó automáticamente y el motivo. */
    fun obtenerDatos(): DatosEsp32? = try {
        val (codigo, texto) = conectar("/datos", "GET")
        if (codigo == 200) {
            val json = JSONObject(texto)
            DatosEsp32(
                mq135 = json.getInt("mq135"),
                encendido = json.getBoolean("encendido"),
                automatico = json.optBoolean("automatico", false),
                motivo = json.optString("motivo", "APAGADO"),
                umbral = json.optInt("umbral", 400),
                minutos = json.getInt("minutos"),
                restanteSeg = json.getLong("restante_s"),
                servo = json.getString("servo")
            )
        } else null
    } catch (e: Exception) {
        null
    }

    /** Descarga las lecturas que el ESP32 guardó offline, o null si no respondió. */
    fun obtenerBuffer(): List<LecturaOffline>? = try {
        val (codigo, texto) = conectar("/buffer", "GET")
        if (codigo == 200) {
            val json = JSONObject(texto)
            val arreglo = json.getJSONArray("lecturas")
            val lista = mutableListOf<LecturaOffline>()
            for (i in 0 until arreglo.length()) {
                val o = arreglo.getJSONObject(i)
                lista.add(LecturaOffline(ms = o.getLong("ms"), valor = o.getInt("valor")))
            }
            lista
        } else null
    } catch (e: Exception) {
        null
    }

    /** Confirma al ESP32 cuántas lecturas recibió la app; el ESP32 las borra de su flash. */
    fun confirmarSync(cantidad: Int): Boolean = try {
        val cuerpo = JSONObject().put("cantidad", cantidad).toString()
        val (codigo, _) = conectar("/sync_ack", "POST", cuerpo)
        codigo == 200
    } catch (e: Exception) {
        false
    }

    /** Envía la orden del servo con sus minutos. Devuelve true si el ESP32 la aceptó. */
    fun enviarOrden(encendido: Boolean, minutos: Int): Boolean = try {
        val cuerpo = JSONObject()
            .put("encendido", encendido)
            .put("minutos", minutos.coerceIn(1, 15))
            .toString()
        val (codigo, _) = conectar("/servo", "POST", cuerpo)
        codigo == 200
    } catch (e: Exception) {
        false
    }
}

