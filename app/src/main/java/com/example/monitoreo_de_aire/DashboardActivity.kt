package com.example.monitoreo_de_aire

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.drawable.GradientDrawable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboard)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Vinculamos IDs de activity
        val estado_aire = findViewById<TextView>(R.id.estado_aire)
        val sensor_val = findViewById<TextView>(R.id.sensor_val)
        val extractor = findViewById<TextView>(R.id.extractor)
        val nombre_sensor = findViewById<TextView>(R.id.nombre_sensor)
        val calidad_aire = findViewById<CardView>(R.id.calidad_aire)
        val calidad_aire_lb = findViewById<TextView>(R.id.calidad_aire_lb)

        // Inicializamos Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Verificamos el inicio de sesión
        val usuarioActual = auth.currentUser

        if (usuarioActual != null) {
            val uid = usuarioActual.uid

            val userDevicesRef = database.reference
                .child("Dispositivos")
                .child(uid)

            // Lecturas en tiempo real de las métricas en la base de datos
            userDevicesRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {

                        val firstSensor = snapshot.children.firstOrNull()

                        if (firstSensor != null) {
                            val rawSensorName = firstSensor.key ?: "Sensor Desconocido"

                            val cleanName = rawSensorName.replace("_", " ").replaceFirstChar { it.uppercase() }
                            nombre_sensor.text = "$cleanName ⚙️"

                            val estadoConexion = firstSensor.child("estadoConexion").getValue(Boolean::class.java) ?: false
                            val extractorEncendido = firstSensor.child("extractor").child("encendido").getValue(Boolean::class.java) ?: false
                            val valorMq135 = firstSensor.child("mq135_valor").getValue(Int::class.java) ?: 0

                            // Logica matematica
                            val labelBackground = calidad_aire_lb.background as? GradientDrawable

                            if (valorMq135 < 400) {

                                // Aire limpio - Calidad Alta
                                estado_aire.text = "Alta"
                                calidad_aire.setCardBackgroundColor(android.graphics.Color.parseColor("#4C8DFF"))
                                estado_aire.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                                // Etiqueta azul
                                labelBackground?.setColor(android.graphics.Color.parseColor("#2B6DE3"))
                            } else {

                                // Aire pesado - Calidad baja
                                estado_aire.text = "Baja"
                                calidad_aire.setCardBackgroundColor(android.graphics.Color.parseColor("#D32F2F"))
                                estado_aire.setTextColor(android.graphics.Color.parseColor("#66000000"))
                                // Etiqueta burdeos
                                labelBackground?.setColor(android.graphics.Color.parseColor("#8B0000"))
                            }

                            // Metricas sencillas
                            if (estadoConexion) {
                                sensor_val.text = "Valor MQ-135: $valorMq135 \uD83D\uDFE2 "
                            } else {
                                sensor_val.text = "Valor MQ-135: $valorMq135 \uD83D\uDD34 "
                            }

                            if (extractorEncendido) {
                                extractor.text = "Encendido"
                                extractor.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Verde
                            } else {
                                extractor.text = "Apagado"
                                extractor.setTextColor(android.graphics.Color.parseColor("#F44336")) // Rojo
                            }

                        } else {
                            nombre_sensor.text = "Sin sensores asignados"
                            estado_aire.text = "--"
                            sensor_val.text = "Buscando sensor..."
                        }
                    } else {
                        nombre_sensor.text = "Sin sensores asignados"
                        estado_aire.text = "--"
                        sensor_val.text = "Esperando conexión..."
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DashboardActivity, "Error de base de datos: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            // Mensajes en caso de error de inicio de sesión y redireccionamiento
            Toast.makeText(this, "Sesión no válida. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}