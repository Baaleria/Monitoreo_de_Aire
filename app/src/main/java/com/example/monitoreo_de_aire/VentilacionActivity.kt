package com.example.monitoreo_de_aire

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.firebase.database.ServerValue
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VentilacionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentilacionBinding

    private var sensorRef: DatabaseReference? = null
    private var sensorQuery: Query? = null
    private var sensorListener: ValueEventListener? = null

    // Escucha en tiempo real del nodo Dispositivos/{codigo}/extractor
    private var extractorRef: DatabaseReference? = null
    private var extractorListener: ValueEventListener? = null

    // Vigilancia de la conexión a Internet (usa ACCESS_NETWORK_STATE)
    private lateinit var estadoRed: EstadoRed
    private lateinit var avisoConexion: AvisoConexion
    private var hayRed = true

    // Evita que un cambio venido de Firebase se vuelva a escribir en Firebase
    private var actualizandoDesdeDb = false

    // Almacenamiento temporal de lecturas en el teléfono (SQLite)
    private lateinit var bufferLocal: BufferLecturas

    // Sondeo periódico del ESP32 (ESP32 -> App -> Firebase)
    private val hilo = Handler(Looper.getMainLooper())
    private val INTERVALO_SONDEO_MS = 5000L
    private var sondeando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVentilacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bufferLocal = BufferLecturas(this)

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
            if (volvimosDePerderRed) sincronizarOrdenPendiente()
            avisoConexion.mostrar(tipo)
        }

        cargarDispositivo()
    }

    override fun onStart() {
        super.onStart()
        estadoRed.iniciar()
        iniciarSondeo()
    }

    override fun onStop() {
        super.onStop()
        // Ahorro de batería: sin la app en pantalla no se sondea el ESP32, no se
        // escucha la base de datos ni se vigilan cambios de red. Se reanuda en onStart().
        detenerSondeo()
        estadoRed.detener()
    }

    // ==================== Sondeo ESP32: leer, almacenar y sincronizar ====================
    // Ciclo completo (rúbrica 3.1.1.4 + 3.1.2.7):
    //   1. App -> ESP32: estado actual (GET /datos)
    //   2. App <- ESP32: lecturas guardadas OFFLINE en la flash (GET /buffer)
    //   3. App: las guarda en SQLite local (sobrevive al cerrar la app)
    //   4. App -> Firebase: sube las lecturas (la nube recibe TODO por la app)
    //   5. App -> ESP32: confirma cuántas subió (POST /sync_ack); solo entonces
    //      el ESP32 borra esas lecturas de su flash.

    private fun iniciarSondeo() {
        if (sondeando) return
        sondeando = true
        hilo.postDelayed(sondeoLoop, INTERVALO_SONDEO_MS)
    }

    private fun detenerSondeo() {
        sondeando = false
        hilo.removeCallbacks(sondeoLoop)
    }

    private val sondeoLoop = object : Runnable {
        override fun run() {
            if (!sondeando) return
            Thread {
                // 1) Estado actual del ESP32
                val datos = ConexionEsp32.obtenerDatos()
                if (datos != null) datosRecientes = datos
                // 2) Buffer offline del ESP32 (lecturas sin conexión)
                val pendientesEsp32 = ConexionEsp32.obtenerBuffer()
                runOnUiThread {
                    if (datos != null) {
                        // Mostrar en pantalla el estado REAL del ESP32
                        if (!actualizandoDesdeDb) {
                            actualizandoDesdeDb = true
                            binding.switchExtractor.isChecked = datos.encendido
                            actualizarTextoEstado(datos.encendido)
                            actualizarDuracionHabilitada(datos.encendido)
                            actualizandoDesdeDb = false
                        }
                    }
                }
                // 3) Guardar en SQLite (fuera del hilo principal)
                if (pendientesEsp32 != null) {
                    for (l in pendientesEsp32) bufferLocal.agregar(l.valor, l.ms)
                }
                // 4) Subir a Firebase y 5) confirmar al ESP32
                sincronizarBufferConFirebase()
                if (sondeando) hilo.postDelayed(this, INTERVALO_SONDEO_MS)
            }.start()
        }
    }

    private fun sincronizarBufferConFirebase() {
        val ref = sensorRef ?: return
        if (!hayRed) return // sin Internet: las lecturas quedan en SQLite y se reintenta

        val pendientes = bufferLocal.primeras(20) // lotes de a 20
        if (pendientes.isNotEmpty()) {
            subirLoteAFirebase(ref, pendientes)
        }
        val ultima = datosRecientes ?: return
        if (pendientes.isEmpty()) {
            // Nada en cola: solo actualizar la última detección con fecha
            subirUltimaDeteccion(ref, ultima)
        }
    }

    private fun subirLoteAFirebase(
        ref: DatabaseReference,
        pendientes: List<Pair<Int, Long>>
    ) {
        val formato = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        var subidas = 0
        for ((valor, _) in pendientes) {
            // La base queda con SOLO la última detección (sobrescritura, sin historial)
            val deteccion = mapOf<String, Any>(
                "valor" to valor,
                "fecha" to formato.format(Date()),
                "fecha_ms" to ServerValue.TIMESTAMP
            )
            ref.child("ultima_deteccion").setValue(deteccion)
            ref.child("mq135_valor").setValue(valor)
            subidas++
        }

        // 5) Solo si Firebase aceptó (no lanzó), se confirma al ESP32 y se limpia el SQLite
        val confirmado = ConexionEsp32.confirmarSync(subidas)
        if (confirmado) bufferLocal.borrarPrimeras(subidas)
    }

    private fun subirUltimaDeteccion(ref: DatabaseReference, datos: ConexionEsp32.DatosEsp32) {
        val formato = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val deteccion = mapOf<String, Any>(
            "valor" to datos.mq135,
            "fecha" to formato.format(Date()),
            "fecha_ms" to ServerValue.TIMESTAMP
        )
        ref.child("ultima_deteccion").setValue(deteccion)
        val cambios = mapOf<String, Any>(
            "mq135_valor" to datos.mq135,
            "ultimaLectura" to ServerValue.TIMESTAMP,
            "estadoConexion" to true
        )
        ref.updateChildren(cambios)
    }

    // Último estado conocido del ESP32 en este ciclo de sondeo
    private var datosRecientes: ConexionEsp32.DatosEsp32? = null

    // Controles habilitados con dispositivo vinculado, HAYA O NO Internet:
    // sin conexión la orden se guarda en el teléfono y se sincroniza después.
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

    private fun actualizarTextoEstado(encendido: Boolean) {
        if (encendido) {
            binding.tvEstadoExtractor.setText(R.string.estado_encendido)
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
                // Si quedó una orden pendiente de una sesión anterior, se envía ahora
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
                actualizarTextoDuracion(duracion)
                actualizandoDesdeDb = false
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarAviso(getString(R.string.msg_error_bd, error.message))
            }
        }
        extractorListener = listener
        ref.addValueEventListener(listener)
    }

    // ============ Almacenamiento temporal de ÓRDENES sin conexión ============
    // La orden se guarda CIFRADA (AES-256-GCM con Android Keystore, vía CifradoKeystore)
    // y se envía al ESP32 + Firebase cuando vuelve la conexión.

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
            // dato alterado o ilegible: se descarta por seguridad
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
    // =========================================================================

    // App -> ESP32 (orden del servo) y App -> Firebase (registro mínimo de la orden)
    private fun guardarEstado() {
        val ref = sensorRef ?: return
        val encendido = binding.switchExtractor.isChecked
        val minutos = minutosActuales()

        if (!hayRed) {
            // Sin Internet: la orden NO se pierde, se guarda cifrada en el teléfono
            // y se sincroniza automáticamente al volver la conexión.
            guardarOrdenPendiente()
            Toast.makeText(this, R.string.msg_sin_conexion_orden, Toast.LENGTH_LONG).show()
            return
        }

        binding.btnAplicar.isEnabled = false
        Thread {
            // 1) La orden llega al ESP32 directamente (red local)
            val llego = ConexionEsp32.enviarOrden(encendido, minutos)
            runOnUiThread {
                binding.btnAplicar.isEnabled = true
                if (llego) {
                    Toast.makeText(this, R.string.msg_cambios_aplicados, Toast.LENGTH_SHORT).show()
                    actualizarTextoEstado(encendido)
                    actualizarDuracionHabilitada(encendido)
                } else {
                    Toast.makeText(this,
                        "No se pudo conectar con el ESP32 en la red local", Toast.LENGTH_LONG).show()
                }
            }
            // 2) Registro mínimo de la orden en Firebase (APP -> FIREBASE)
            if (llego) {
                val formato = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                val orden = mapOf<String, Any>(
                    "encendido" to encendido,
                    "duracion_minutos" to minutos,
                    "fecha" to formato.format(Date())
                )
                ref.child("extractor").updateChildren(orden)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerSondeo()
        sensorListener?.let { sensorQuery?.removeEventListener(it) }
        extractorListener?.let { extractorRef?.removeEventListener(it) }
    }
}
