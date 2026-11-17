package com.example.monitoreo_de_aire

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityVentilacionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VentilacionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentilacionBinding

    private var sensorRef: DatabaseReference? = null
    private var sensorQuery: Query? = null
    private var sensorListener: ValueEventListener? = null

    private var extractorRef: DatabaseReference? = null
    private var extractorListener: ValueEventListener? = null

    private lateinit var estadoRed: EstadoRed
    private lateinit var avisoConexion: AvisoConexion
    private var hayRed = true

    private var actualizandoDesdeDb = false
    private var estabaEnAutomatico = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVentilacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.ventilacion) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left + dp(24),
                systemBars.top + dp(24),
                systemBars.right + dp(24),
                systemBars.bottom + dp(24)
            )
            insets
        }

        binding.btnAtras.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        actualizarTextoEstado(false)
        actualizarTextoDuracion(minutosActuales())
        actualizarDuracionHabilitada(false)

        binding.switchExtractor.setOnCheckedChangeListener { _, isChecked ->
            actualizarTextoEstado(isChecked)
            actualizarDuracionHabilitada(isChecked)
            binding.btnAplicar.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!actualizandoDesdeDb && !isChecked) guardarEstado()
        }

        binding.btnAplicar.setOnClickListener { guardarEstado() }

        binding.seekDuracion.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                actualizarTextoDuracion(progress + 1)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        avisoConexion = AvisoConexion(binding.ventilacion)
        estadoRed = EstadoRed(this) { tipo ->
            val volvimosDePerderRed = !hayRed && tipo != EstadoRed.TipoRed.SIN_RED
            hayRed = tipo != EstadoRed.TipoRed.SIN_RED
            SincronizadorEsp.hayRed = hayRed
            if (volvimosDePerderRed) sincronizarOrdenPendiente()
            avisoConexion.mostrar(tipo)
        }

        cargarDispositivo()
    }

    override fun onStart() {
        super.onStart()
        estadoRed.iniciar()
        SincronizadorEsp.onDatosNuevos = { datos ->
            aplicarEstadoDesdeEsp32(datos)
        }
        sensorRef?.let { SincronizadorEsp.iniciar(this, this, it) }
    }

    override fun onStop() {
        super.onStop()
        SincronizadorEsp.detener(this)
        estadoRed.detener()
    }

    private fun aplicarEstadoDesdeEsp32(datos: ConexionEsp32.DatosEsp32) {
        if (!actualizandoDesdeDb) {
            actualizandoDesdeDb = true
            binding.switchExtractor.isChecked = datos.encendido
            actualizarTextoEstado(datos.encendido, datos.automatico)
            actualizarDuracionHabilitada(datos.encendido && !datos.automatico)
            binding.btnAplicar.visibility =
                if (datos.encendido && !datos.automatico) View.VISIBLE else View.GONE
            actualizandoDesdeDb = false
        }

        if (datos.encendido && datos.automatico) {
            binding.tvDuracion.text = "Automático por umbral (MQ-135 ≥ ${datos.umbral})"
            val mensajeRazon =
                "Encendido automático: el sensor MQ-135 detectó ${datos.mq135} (superó el umbral de ${datos.umbral}). Se apagará al bajar."
            mostrarAviso(mensajeRazon)
            if (!estabaEnAutomatico) {
                Toast.makeText(this, mensajeRazon, Toast.LENGTH_LONG).show()
            }
            estabaEnAutomatico = true
        } else {
            if (estabaEnAutomatico) {
                estabaEnAutomatico = false
                binding.tvAviso.visibility = View.GONE
                actualizarTextoDuracion(minutosActuales())
                Toast.makeText(
                    this,
                    "Aire normalizado (< ${datos.umbral}): extractor apagado automáticamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun actualizarControles() {
        val habilitado = sensorRef != null
        binding.switchExtractor.isEnabled = habilitado
        binding.btnAplicar.isEnabled = habilitado
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    private fun actualizarDuracionHabilitada(habilitada: Boolean) {
        binding.seekDuracion.isEnabled = habilitada
        binding.tvDuracion.alpha = if (habilitada) 1f else 0.5f
        binding.cardDuracion.setCardBackgroundColor(
            color(if (habilitada) R.color.white else R.color.gris_claro)
        )
    }

    private fun mostrarAviso(texto: String) {
        binding.tvAviso.text = texto
        binding.tvAviso.visibility = View.VISIBLE
    }

    private fun minutosActuales(): Int = binding.seekDuracion.progress + 1

    private fun actualizarTextoDuracion(minutos: Int) {
        binding.tvDuracion.text = resources.getQuantityString(R.plurals.minutos, minutos, minutos)
    }

    private fun actualizarTextoEstado(encendido: Boolean, automatico: Boolean = false) {
        if (encendido) {
            if (automatico) {
                binding.tvEstadoExtractor.text = "Encendido (Umbral superado)"
            } else {
                binding.tvEstadoExtractor.setText(R.string.estado_encendido)
            }
            binding.tvEstadoExtractor.setTextColor(color(R.color.verde_encendido))
        } else {
            binding.tvEstadoExtractor.setText(R.string.estado_apagado)
            binding.tvEstadoExtractor.setTextColor(color(R.color.rojo_apagado))
        }
    }

    private fun cargarDispositivo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, R.string.msg_sesion_invalida, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val query = FirebaseDatabase.getInstance().reference
            .child("Dispositivos")
            .orderByChild("owner_uid")
            .equalTo(uid)
        sensorQuery = query

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sensor = DispositivoActivo.elegir(this@VentilacionActivity, uid, snapshot)
                if (sensor == null) {
                    sensorRef = null
                    binding.switchExtractor.isEnabled = false
                    binding.btnAplicar.isEnabled = false
                    actualizarDuracionHabilitada(false)
                    mostrarAviso(getString(R.string.aviso_sin_dispositivo_vinculado))
                    return
                }

                binding.tvAviso.visibility = View.GONE
                sensorRef = sensor.ref
                actualizarControles()
                SincronizadorEsp.iniciar(this@VentilacionActivity, this@VentilacionActivity, sensor.ref)
                sincronizarOrdenPendiente()
                escucharExtractor(sensor.ref.child("extractor"))
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarAviso(getString(R.string.msg_error_bd, error.message))
            }
        }
        sensorListener = listener
        query.addListenerForSingleValueEvent(listener)
    }

    private fun escucharExtractor(ref: DatabaseReference) {
        extractorListener?.let { extractorRef?.removeEventListener(it) }
        extractorRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val duracionDb = (snapshot.child("duracion_minutos").value as? Number)?.toInt() ?: 0
                val duracion = if (duracionDb < 1) 5 else duracionDb.coerceAtMost(15)
                actualizandoDesdeDb = true
                binding.seekDuracion.progress = duracion - 1
                if (!estabaEnAutomatico) {
                    actualizarTextoDuracion(duracion)
                }
                actualizandoDesdeDb = false
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarAviso(getString(R.string.msg_error_bd, error.message))
            }
        }
        extractorListener = listener
        ref.addValueEventListener(listener)
    }

    private fun prefsOrden() =
        getSharedPreferences("ordenes_pendientes", Context.MODE_PRIVATE)

    private fun claveOrden(uid: String) = "orden_$uid"

    private fun guardarOrdenPendiente() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val orden = JSONObject()
            .put("encendido", binding.switchExtractor.isChecked)
            .put("minutos", minutosActuales())
        val cifrado = CifradoKeystore.cifrar(orden.toString()) ?: return
        prefsOrden().edit().putString(claveOrden(uid), cifrado).apply()
    }

    private fun sincronizarOrdenPendiente() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val cifrado = prefsOrden().getString(claveOrden(uid), null) ?: return
        val texto = CifradoKeystore.descifrar(cifrado)
        if (texto == null) {
            prefsOrden().edit().remove(claveOrden(uid)).apply()
            return
        }
        val orden = JSONObject(texto)
        val encendido = orden.getBoolean("encendido")
        val minutos = orden.getInt("minutos")

        Thread {
            val llegoAlEsp32 = ConexionEsp32.enviarOrden(encendido, minutos)
            runOnUiThread {
                if (llegoAlEsp32) {
                    prefsOrden().edit().remove(claveOrden(uid)).apply()
                    Toast.makeText(this, "Orden guardada sin conexión sincronizada", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun guardarEstado() {
        val ref = sensorRef ?: return
        val encendido = binding.switchExtractor.isChecked
        val minutos = minutosActuales()

        if (!hayRed) {
            guardarOrdenPendiente()
            Toast.makeText(this, R.string.msg_sin_conexion_orden, Toast.LENGTH_LONG).show()
            return
        }

        binding.btnAplicar.isEnabled = false
        Thread {
            val llego = ConexionEsp32.enviarOrden(encendido, minutos)
            runOnUiThread {
                binding.btnAplicar.isEnabled = true
                if (llego) {
                    estabaEnAutomatico = false
                    binding.tvAviso.visibility = View.GONE
                    Toast.makeText(this, R.string.msg_cambios_aplicados, Toast.LENGTH_SHORT).show()
                    actualizarTextoEstado(encendido, false)
                    actualizarDuracionHabilitada(encendido)
                } else {
                    Toast.makeText(
                        this,
                        "No se pudo conectar con el ESP32 en la red local",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            if (llego) {
                val formato = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                val orden = mapOf<String, Any>(
                    "encendido" to encendido,
                    "automatico" to false,
                    "motivo" to if (encendido) "MANUAL_APP" else "APAGADO",
                    "duracion_minutos" to minutos,
                    "fecha" to formato.format(Date())
                )
                ref.child("extractor").updateChildren(orden)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        SincronizadorEsp.detener(this)
        sensorListener?.let { sensorQuery?.removeEventListener(it) }
        extractorListener?.let { extractorRef?.removeEventListener(it) }
    }
}
