package com.example.monitoreo_de_aire

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class DashboardActivity : AppCompatActivity() {

    companion object {
        private const val UMBRAL_CALIDAD_BAJA = 400
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var devicesQuery: Query? = null
    private var devicesListener: ValueEventListener? = null

    private lateinit var estadoRed: EstadoRed
    private lateinit var avisoConexion: AvisoConexion

    private var ultimoSnapshot: DataSnapshot? = null
    private var dispositivoRef: DatabaseReference? = null
    private var enPrimerPlano = false
    private var notificadoAutoDashboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboard) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        configurarMenuLateral()
        configurarAccesosRapidos()

        auth = FirebaseAuth.getInstance()
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Ya estaba habilitada en esta instancia de la app
        }
        database = FirebaseDatabase.getInstance()

        avisoConexion = AvisoConexion(binding.dashboard)
        estadoRed = EstadoRed(this) { tipo ->
            SincronizadorEsp.hayRed = tipo != EstadoRed.TipoRed.SIN_RED
            avisoConexion.mostrar(tipo)
        }

        val usuarioActual = auth.currentUser

        if (usuarioActual != null) {
            cargarNombreUsuario(usuarioActual.uid)

            val consulta = database.reference
                .child("Dispositivos")
                .orderByChild("owner_uid")
                .equalTo(usuarioActual.uid)
            devicesQuery = consulta

            val escucha = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    ultimoSnapshot = snapshot
                    mostrar(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isFinishing || auth.currentUser == null) return
                    Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.msg_error_bd, error.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            devicesListener = escucha
            consulta.addValueEventListener(escucha)
        } else {
            Toast.makeText(this, R.string.msg_sesion_invalida, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun configurarMenuLateral() {
        val drawer = binding.drawerLayout
        val menuLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) drawer.openDrawer(GravityCompat.START)
        }
        binding.btnMenuHamburguesa.setOnClickListener {
            drawer.openDrawer(GravityCompat.START)
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dispositivos -> menuLauncher.launch(Intent(this, DispositivosActivity::class.java))
                R.id.nav_ventilacion -> menuLauncher.launch(Intent(this, VentilacionActivity::class.java))
                R.id.nav_cerrar_sesion -> cerrarSesion()
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun configurarAccesosRapidos() {
        binding.nombreSensor.setOnClickListener {
            startActivity(Intent(this, DispositivosActivity::class.java))
        }
        binding.cardVentilacion.setOnClickListener {
            startActivity(Intent(this, VentilacionActivity::class.java))
        }
    }

    private fun cargarNombreUsuario(uid: String) {
        val tvNombre = binding.navView.getHeaderView(0).findViewById<TextView>(R.id.tvNombreUsuario)
        database.reference.child("Usuarios").child(uid).get()
            .addOnSuccessListener { datos ->
                val nombre = datos.child("nombre").getValue(String::class.java)
                    ?: datos.child("usuario").getValue(String::class.java)
                if (!nombre.isNullOrBlank()) tvNombre.text = nombre
            }
    }

    private fun cerrarSesion() {
        SincronizadorEsp.detener(this)
        devicesListener?.let { devicesQuery?.removeEventListener(it) }
        devicesListener = null
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onStart() {
        super.onStart()
        enPrimerPlano = true
        estadoRed.iniciar()
        SincronizadorEsp.onDatosNuevos = { datos ->
            if (datos.encendido && datos.automatico) {
                binding.extractor.text = "Encendido (Umbral ≥ ${datos.umbral})"
                binding.extractor.setTextColor(color(R.color.verde_encendido))
                if (!notificadoAutoDashboard) {
                    notificadoAutoDashboard = true
                    Toast.makeText(
                        this,
                        "Ventilación activada automáticamente: MQ-135 (${datos.mq135}) superó el umbral de ${datos.umbral}.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (!datos.encendido && notificadoAutoDashboard) {
                notificadoAutoDashboard = false
                binding.extractor.setText(R.string.estado_apagado)
                binding.extractor.setTextColor(color(R.color.rojo_apagado))
                Toast.makeText(
                    this,
                    "Calidad de aire normalizada (< ${datos.umbral}): ventilación apagada.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dispositivoRef?.let { SincronizadorEsp.iniciar(this, this, it) }
    }

    override fun onStop() {
        super.onStop()
        enPrimerPlano = false
        SincronizadorEsp.detener(this)
        estadoRed.detener()
    }

    override fun onResume() {
        super.onResume()
        ultimoSnapshot?.let { mostrar(it) }
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    private fun mostrar(snapshot: DataSnapshot) {
        val uid = auth.currentUser?.uid ?: return
        val sensor = DispositivoActivo.elegir(this, uid, snapshot)

        if (sensor == null) {
            dispositivoRef = null
            SincronizadorEsp.detener(this)
            binding.nombreSensor.setText(R.string.dashboard_sin_sensores)
            binding.estadoAire.setText(R.string.valor_vacio)
            binding.sensorVal.setText(R.string.dashboard_esperando)
            binding.extractor.setText(R.string.valor_vacio)
            return
        }

        dispositivoRef = sensor.ref
        if (enPrimerPlano) {
            SincronizadorEsp.iniciar(this, this, sensor.ref)
        }

        val cleanName = sensor.child("nombre").getValue(String::class.java)
            ?: sensor.key ?: "Sensor Desconocido"
        binding.nombreSensor.text = getString(R.string.dashboard_nombre_sensor, cleanName)

        val conectado = sensor.child("estadoConexion").value as? Boolean ?: false
        val ultimaLectura = (sensor.child("ultimaLectura").value as? Number)?.toLong() ?: 0L
        val estadoConexion = conectado && (System.currentTimeMillis() - ultimaLectura in 0L..45_000L)

        val extractorEncendido = sensor.child("extractor").child("encendido").value as? Boolean ?: false
        val extractorAuto = sensor.child("extractor").child("automatico").value as? Boolean ?: false
        val valorMq135 = (sensor.child("mq135_valor").value as? Number)?.toInt() ?: 0

        val fondoEtiqueta = binding.calidadAireLb.background as? GradientDrawable

        if (valorMq135 < UMBRAL_CALIDAD_BAJA) {
            binding.estadoAire.setText(R.string.calidad_alta)
            binding.calidadAire.setCardBackgroundColor(color(R.color.azul_claro))
            binding.estadoAire.setTextColor(color(R.color.blanco_translucido))
            fondoEtiqueta?.setColor(color(R.color.azul_etiqueta_alta))
        } else {
            binding.estadoAire.setText(R.string.calidad_baja)
            binding.calidadAire.setCardBackgroundColor(color(R.color.rojo_baja))
            binding.estadoAire.setTextColor(color(R.color.negro_translucido))
            fondoEtiqueta?.setColor(color(R.color.rojo_etiqueta_baja))
        }

        binding.sensorVal.text = getString(
            if (estadoConexion) R.string.dashboard_valor_conectado else R.string.dashboard_valor_desconectado,
            valorMq135
        )

        if (extractorEncendido) {
            if (extractorAuto) {
                binding.extractor.text = "Encendido (Umbral ≥ $UMBRAL_CALIDAD_BAJA)"
            } else {
                binding.extractor.setText(R.string.estado_encendido)
            }
            binding.extractor.setTextColor(color(R.color.verde_encendido))
        } else {
            binding.extractor.setText(R.string.estado_apagado)
            binding.extractor.setTextColor(color(R.color.rojo_apagado))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SincronizadorEsp.detener(this)
        devicesListener?.let { devicesQuery?.removeEventListener(it) }
    }
}