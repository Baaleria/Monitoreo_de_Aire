package com.example.monitoreo_de_aire

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class VentilacionActivity : AppCompatActivity() {

    private lateinit var switchExtractor: MaterialSwitch
    private lateinit var tvEstado: TextView
    private lateinit var tvDuracion: TextView
    private lateinit var seekDuracion: SeekBar
    private lateinit var cardDuracion: CardView
    private lateinit var btnAplicar: Button
    private lateinit var tvAviso: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ventilacion)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ventilacion)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left + dp(24),
                systemBars.top + dp(24),
                systemBars.right + dp(24),
                systemBars.bottom + dp(24)
            )
            insets
        }

        switchExtractor = findViewById(R.id.switchExtractor)
        tvEstado = findViewById(R.id.tvEstadoExtractor)
        tvDuracion = findViewById(R.id.tvDuracion)
        seekDuracion = findViewById(R.id.seekDuracion)
        cardDuracion = findViewById(R.id.cardDuracion)
        btnAplicar = findViewById(R.id.btnAplicar)
        tvAviso = findViewById(R.id.tvAviso)

        findViewById<android.view.View>(R.id.btnAtras).setOnClickListener {
            // Vuelve al Dashboard con el menú lateral abierto
            setResult(RESULT_OK)
            finish()
        }

        actualizarTextoEstado(false)
        actualizarTextoDuracion(minutosActuales())
        actualizarDuracionHabilitada(false)

        // Interruptor encendido / apagado
        switchExtractor.setOnCheckedChangeListener { _, isChecked ->
            actualizarTextoEstado(isChecked)
            actualizarDuracionHabilitada(isChecked)
            // Aplicar solo se ve con el extractor encendido
            btnAplicar.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            // Apagar se guarda de inmediato (no necesita confirmar)
            if (!actualizandoDesdeDb && !isChecked) guardarEstado()
        }

        // Los cambios se envían a Firebase solo al presionar "Aplicar"
        btnAplicar.setOnClickListener { guardarEstado() }

        // Barra de duración: 1 a 15 minutos (progreso 0..14)
        seekDuracion.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                actualizarTextoDuracion(progress + 1)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Sin Internet no se pueden enviar órdenes: Firebase las dejaría en cola y el
        // extractor se encendería más tarde, al volver la conexión, sin que el usuario lo espere
        avisoConexion = AvisoConexion(findViewById(R.id.ventilacion))
        estadoRed = EstadoRed(this) { tipo ->
            hayRed = tipo != EstadoRed.TipoRed.SIN_RED
            actualizarControles()
            avisoConexion.mostrar(tipo)
        }

        cargarDispositivo()
    }

    override fun onStart() {
        super.onStart()
        estadoRed.iniciar()
    }

    override fun onStop() {
        super.onStop()
        estadoRed.detener()
    }

    // Los controles solo funcionan si hay dispositivo vinculado y hay Internet
    private fun actualizarControles() {
        val habilitado = sensorRef != null && hayRed
        switchExtractor.isEnabled = habilitado
        btnAplicar.isEnabled = habilitado
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    // La tarjeta de duración se ve gris y bloqueada mientras el extractor está apagado
    private fun actualizarDuracionHabilitada(habilitada: Boolean) {
        seekDuracion.isEnabled = habilitada
        tvDuracion.alpha = if (habilitada) 1f else 0.5f
        cardDuracion.setCardBackgroundColor(
            Color.parseColor(if (habilitada) "#FFFFFF" else "#E0E0E0")
        )
    }

    private fun mostrarAviso(texto: String) {
        tvAviso.text = texto
        tvAviso.visibility = android.view.View.VISIBLE
    }

    private fun minutosActuales(): Int = seekDuracion.progress + 1

    private fun actualizarTextoDuracion(minutos: Int) {
        tvDuracion.text = if (minutos == 1) "1 minuto" else "$minutos minutos"
    }

    private fun actualizarTextoEstado(encendido: Boolean) {
        if (encendido) {
            tvEstado.text = "Encendido"
            tvEstado.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvEstado.text = "Apagado"
            tvEstado.setTextColor(Color.parseColor("#F44336"))
        }
    }

    // Busca el dispositivo seleccionado del usuario (una sola vez) y después escucha en
    // tiempo real SOLO su nodo "extractor". Así las lecturas del sensor no reinician el
    // interruptor mientras el usuario lo está usando.
    private fun cargarDispositivo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Sesión no válida. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
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
                    switchExtractor.isEnabled = false
                    btnAplicar.isEnabled = false
                    actualizarDuracionHabilitada(false)
                    mostrarAviso("Esta cuenta no tiene un dispositivo vinculado. Vincula uno desde Dispositivos.")
                    return
                }

                tvAviso.visibility = android.view.View.GONE
                sensorRef = sensor.ref
                actualizarControles()
                escucharExtractor(sensor.ref.child("extractor"))
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarAviso("Error de base de datos: ${error.message}")
            }
        }
        sensorListener = listener
        query.addListenerForSingleValueEvent(listener)
    }

    // Refleja en pantalla el estado del extractor cada vez que cambia en Firebase
    // (por ejemplo, cuando el dispositivo lo apaga al terminar el tiempo)
    private fun escucharExtractor(ref: DatabaseReference) {
        extractorListener?.let { extractorRef?.removeEventListener(it) }
        extractorRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val encendido = snapshot.child("encendido").value as? Boolean ?: false
                val duracionDb = (snapshot.child("duracion_minutos").value as? Number)?.toInt() ?: 0
                // Si en la base hay 0 (sin configurar) se muestra 5 minutos por defecto
                val duracion = if (duracionDb < 1) 5 else duracionDb.coerceAtMost(15)

                actualizandoDesdeDb = true
                switchExtractor.isChecked = encendido
                seekDuracion.progress = duracion - 1
                actualizarTextoEstado(encendido)
                actualizarTextoDuracion(duracion)
                actualizarDuracionHabilitada(encendido)
                btnAplicar.visibility = if (encendido) android.view.View.VISIBLE else android.view.View.GONE
                actualizandoDesdeDb = false
            }

            override fun onCancelled(error: DatabaseError) {
                mostrarAviso("Error de base de datos: ${error.message}")
            }
        }
        extractorListener = listener
        ref.addValueEventListener(listener)
    }

    // Guarda estado y duración en Dispositivos/{codigo}/extractor
    private fun guardarEstado() {
        val ref = sensorRef ?: return
        if (!hayRed) {
            Toast.makeText(this, "Sin conexión: no se puede enviar la orden", Toast.LENGTH_LONG).show()
            return
        }
        val cambios = mapOf<String, Any>(
            "extractor/encendido" to switchExtractor.isChecked,
            "extractor/duracion_minutos" to minutosActuales()
        )
        ref.updateChildren(cambios).addOnSuccessListener {
            Toast.makeText(this, "Cambios aplicados", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { error ->
            Toast.makeText(this, "No se pudo guardar: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorListener?.let { sensorQuery?.removeEventListener(it) }
        extractorListener?.let { extractorRef?.removeEventListener(it) }
    }
}