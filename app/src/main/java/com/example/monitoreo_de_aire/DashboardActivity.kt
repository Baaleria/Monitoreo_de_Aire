package com.example.monitoreo_de_aire

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
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

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var devicesQuery: Query? = null
    private var devicesListener: ValueEventListener? = null

    // Últimos datos recibidos: se vuelven a dibujar al volver de elegir otro dispositivo
    private var ultimoSnapshot: DataSnapshot? = null

    private lateinit var estadoAire: TextView
    private lateinit var sensorVal: TextView
    private lateinit var extractor: TextView
    private lateinit var nombreSensor: TextView
    private lateinit var calidadAire: CardView
    private lateinit var calidadAireLb: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Menu lateral
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        // Al volver de Dispositivos / Ventilación con la flecha, se reabre el menú
        val menuLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) drawer.openDrawer(GravityCompat.START)
        }
        findViewById<ImageView>(R.id.btnMenuHamburguesa).setOnClickListener {
            drawer.openDrawer(GravityCompat.START)
        }
        findViewById<NavigationView>(R.id.nav_view).setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dispositivos -> menuLauncher.launch(Intent(this, DispositivosActivity::class.java))
                R.id.nav_ventilacion -> menuLauncher.launch(Intent(this, VentilacionActivity::class.java))
                R.id.nav_cerrar_sesion -> {
                    // Primero soltamos el listener: sin sesión Firebase lo cancelaría con 'permission denied'
                    devicesListener?.let { devicesQuery?.removeEventListener(it) }
                    devicesListener = null
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                else -> Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
            }
            drawer.closeDrawer(GravityCompat.START)
            true
        }

        // Vinculamos IDs de activity
        estadoAire = findViewById(R.id.estado_aire)
        sensorVal = findViewById(R.id.sensor_val)
        extractor = findViewById(R.id.extractor)
        nombreSensor = findViewById(R.id.nombre_sensor)
        calidadAire = findViewById(R.id.calidad_aire)
        calidadAireLb = findViewById(R.id.calidad_aire_lb)

        // Inicializamos Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Verificamos el inicio de sesión
        val usuarioActual = auth.currentUser

        if (usuarioActual != null) {
            val uid = usuarioActual.uid

            // Todos los dispositivos del usuario; se muestra el que esté seleccionado
            val userDevicesRef = database.reference
                .child("Dispositivos")
                .orderByChild("owner_uid")
                .equalTo(uid)
            devicesQuery = userDevicesRef

            devicesListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    ultimoSnapshot = snapshot
                    mostrar(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isFinishing || auth.currentUser == null) return
                    Toast.makeText(this@DashboardActivity, "Error de base de datos: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
            userDevicesRef.addValueEventListener(devicesListener!!)
        } else {
            // Mensajes en caso de error de inicio de sesión y redireccionamiento
            Toast.makeText(this, "Sesión no válida. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Si en Dispositivos se eligió otro, se actualiza la pantalla
        ultimoSnapshot?.let { mostrar(it) }
    }

    // Dibuja los datos del dispositivo seleccionado
    private fun mostrar(snapshot: DataSnapshot) {
        val uid = auth.currentUser?.uid ?: return
        val sensor = DispositivoActivo.elegir(this, uid, snapshot)

        if (sensor == null) {
            nombreSensor.text = "Sin sensores asignados"
            estadoAire.text = "--"
            sensorVal.text = "Esperando conexión..."
            extractor.text = "--"
            return
        }

        val cleanName = sensor.child("nombre").getValue(String::class.java)
            ?: sensor.key ?: "Sensor Desconocido"
        nombreSensor.text = "$cleanName ⚙️"

        val estadoConexion = sensor.child("estadoConexion").value as? Boolean ?: false
        val extractorEncendido = sensor.child("extractor").child("encendido").value as? Boolean ?: false
        val valorMq135 = (sensor.child("mq135_valor").value as? Number)?.toInt() ?: 0

        // Logica matematica
        val labelBackground = calidadAireLb.background as? GradientDrawable

        if (valorMq135 < UMBRAL_CALIDAD_BAJA) {
            // Aire limpio - Calidad Alta
            estadoAire.text = "Alta"
            calidadAire.setCardBackgroundColor(Color.parseColor("#4C8DFF"))
            estadoAire.setTextColor(Color.parseColor("#80FFFFFF"))
            // Etiqueta azul
            labelBackground?.setColor(Color.parseColor("#2B6DE3"))
        } else {
            // Aire pesado - Calidad baja
            estadoAire.text = "Baja"
            calidadAire.setCardBackgroundColor(Color.parseColor("#D32F2F"))
            estadoAire.setTextColor(Color.parseColor("#66000000"))
            // Etiqueta burdeos
            labelBackground?.setColor(Color.parseColor("#8B0000"))
        }

        // Metricas sencillas
        if (estadoConexion) {
            sensorVal.text = "Valor MQ-135: $valorMq135 🟢 "
        } else {
            sensorVal.text = "Valor MQ-135: $valorMq135 🔴 "
        }

        if (extractorEncendido) {
            extractor.text = "Encendido"
            extractor.setTextColor(Color.parseColor("#4CAF50")) // Verde
        } else {
            extractor.text = "Apagado"
            extractor.setTextColor(Color.parseColor("#F44336")) // Rojo
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        devicesListener?.let { devicesQuery?.removeEventListener(it) }
    }
}