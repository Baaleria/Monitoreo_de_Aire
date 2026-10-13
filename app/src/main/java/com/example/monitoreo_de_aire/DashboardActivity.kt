package com.example.monitoreo_de_aire

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class DashboardActivity : AppCompatActivity() {

    companion object {
        // Valor crudo del MQ-135 desde el cual la calidad del aire se considera baja.
        // Debe calibrarse con el sensor real (medir con aire limpio y con aire contaminado).
        private const val UMBRAL_CALIDAD_BAJA = 400
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var devicesQuery: Query? = null
    private var devicesListener: ValueEventListener? = null

    // Vigilancia de la conexión a Internet (usa ACCESS_NETWORK_STATE)
    private lateinit var estadoRed: EstadoRed
    private lateinit var avisoConexion: AvisoConexion

    // Últimos datos recibidos: se vuelven a dibujar al volver de elegir otro dispositivo
    private var ultimoSnapshot: DataSnapshot? = null

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
        database = FirebaseDatabase.getInstance()

        // Aviso cuando se pierde o vuelve la conexión
        avisoConexion = AvisoConexion(binding.dashboard)
        estadoRed = EstadoRed(this) { tipo -> avisoConexion.mostrar(tipo) }

        // Verificamos el inicio de sesión
        val usuarioActual = auth.currentUser

        if (usuarioActual != null) {
            // Todos los dispositivos del usuario; se muestra el que esté seleccionado
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
        // Al volver de Dispositivos / Ventilación con la flecha, se reabre el menú
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

    // Atajos desde el Dashboard: el nombre del dispositivo abre la lista de dispositivos
    // y la tarjeta "Sistema de Ventilación" abre el control del extractor
    private fun configurarAccesosRapidos() {
        binding.nombreSensor.setOnClickListener {
            startActivity(Intent(this, DispositivosActivity::class.java))
        }
        binding.cardVentilacion.setOnClickListener {
            startActivity(Intent(this, VentilacionActivity::class.java))
        }
    }

    private fun cerrarSesion() {
        // Primero soltamos el listener: sin sesión Firebase lo cancelaría con 'permission denied'
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
        estadoRed.iniciar()
    }

    override fun onStop() {
        super.onStop()
        estadoRed.detener()
    }

    override fun onResume() {
        super.onResume()
        // Si en Dispositivos se eligió otro, se actualiza la pantalla
        ultimoSnapshot?.let { mostrar(it) }
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    // Dibuja los datos del dispositivo seleccionado
    private fun mostrar(snapshot: DataSnapshot) {
        val uid = auth.currentUser?.uid ?: return
        val sensor = DispositivoActivo.elegir(this, uid, snapshot)

        if (sensor == null) {
            binding.nombreSensor.setText(R.string.dashboard_sin_sensores)
            binding.estadoAire.setText(R.string.valor_vacio)
            binding.sensorVal.setText(R.string.dashboard_esperando)
            binding.extractor.setText(R.string.valor_vacio)
            return
        }

        val cleanName = sensor.child("nombre").getValue(String::class.java)
            ?: sensor.key ?: "Sensor Desconocido"
        binding.nombreSensor.text = getString(R.string.dashboard_nombre_sensor, cleanName)

        val estadoConexion = sensor.child("estadoConexion").value as? Boolean ?: false
        val extractorEncendido = sensor.child("extractor").child("encendido").value as? Boolean ?: false
        val valorMq135 = (sensor.child("mq135_valor").value as? Number)?.toInt() ?: 0

        // Calidad del aire: Alta o Baja según el umbral
        val fondoEtiqueta = binding.calidadAireLb.background as? GradientDrawable

        if (valorMq135 < UMBRAL_CALIDAD_BAJA) {
            // Aire limpio - Calidad Alta
            binding.estadoAire.setText(R.string.calidad_alta)
            binding.calidadAire.setCardBackgroundColor(color(R.color.azul_claro))
            binding.estadoAire.setTextColor(color(R.color.blanco_translucido))
            fondoEtiqueta?.setColor(color(R.color.azul_etiqueta_alta))
        } else {
            // Aire pesado - Calidad baja
            binding.estadoAire.setText(R.string.calidad_baja)
            binding.calidadAire.setCardBackgroundColor(color(R.color.rojo_baja))
            binding.estadoAire.setTextColor(color(R.color.negro_translucido))
            fondoEtiqueta?.setColor(color(R.color.rojo_etiqueta_baja))
        }

        // Valor del sensor, con 🟢 / 🔴 según esté conectado el dispositivo
        binding.sensorVal.text = getString(
            if (estadoConexion) R.string.dashboard_valor_conectado else R.string.dashboard_valor_desconectado,
            valorMq135
        )

        if (extractorEncendido) {
            binding.extractor.setText(R.string.estado_encendido)
            binding.extractor.setTextColor(color(R.color.verde_encendido))
        } else {
            binding.extractor.setText(R.string.estado_apagado)
            binding.extractor.setTextColor(color(R.color.rojo_apagado))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        devicesListener?.let { devicesQuery?.removeEventListener(it) }
    }
}