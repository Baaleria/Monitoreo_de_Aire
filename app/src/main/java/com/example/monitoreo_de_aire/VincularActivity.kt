package com.example.monitoreo_de_aire

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.content.Intent

class VincularActivity : AppCompatActivity() {
    private var iconoSeleccionado: String = ""

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var tiempoUltimoClicAtras: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vincular)

        // Inicializamos Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Vinculamos los IDs del XML
        val btnAtras = findViewById<ImageView>(R.id.btnAtras)
        val input_codigo = findViewById<EditText>(R.id.input_codigo)
        val input_nombre = findViewById<EditText>(R.id.input_nombre)
        val input_ubicacion = findViewById<EditText>(R.id.input_ubicacion)
        val btnAñadir = findViewById<Button>(R.id.btnAñadir)

        // Lista de todos nuestros íconos
        val iconos = listOf(
            findViewById<ImageView>(R.id.iconBed) to "bed",
            findViewById<ImageView>(R.id.iconDevice) to "device",
            findViewById<ImageView>(R.id.iconEco) to "eco",
            findViewById<ImageView>(R.id.iconClothes) to "clothes",
            findViewById<ImageView>(R.id.iconLeaf) to "leaf",
            findViewById<ImageView>(R.id.iconFan) to "fan",
            findViewById<ImageView>(R.id.iconHome) to "home",
            findViewById<ImageView>(R.id.iconBag) to "bag"
        )

        // Retroceso con doble confirmación
        btnAtras.setOnClickListener {
            // Obtenemos el tiempo actual en milisegundos
            val tiempoActual = System.currentTimeMillis()

            // Si la diferencia es menor a 2000 ms (doble clic)
            if (tiempoActual - tiempoUltimoClicAtras < 2000) {

                // Creamos el viaje directo hacia tu Dashboard
                val intent = Intent(this, DashboardActivity::class.java)

                // Opcional pero recomendado: Estas banderas limpian el historial para que
                // si el usuario presiona "Atrás" estando en el Dashboard, no vuelva aquí ni al Login.
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
                finish()

            } else {
                // Primer clic: Muestra tu mensaje personalizado
                Toast.makeText(this, "¿Estas Seguro? No estas vinculando dispositivo, Presiona de nuevo si quieres salir", Toast.LENGTH_LONG).show()
                tiempoUltimoClicAtras = tiempoActual
            }
        }

        // Lógica para que los íconos cambien de color al tocarlos
        for ((imageView, nombreIcono) in iconos) {
            imageView.setOnClickListener {
                // 1. Primero pintamos TODOS los íconos de gris oscuro nuevamente
                iconos.forEach { it.first.setColorFilter(Color.parseColor("#666666")) }

                // 2. Pintamos de azul solo el ícono que el usuario acaba de tocar
                imageView.setColorFilter(Color.parseColor("#0055FF"))

                // 3. Guardamos el nombre de ese ícono en nuestra variable
                iconoSeleccionado = nombreIcono
            }
        }

        // Lógica de Añadir
        btnAñadir.setOnClickListener {
            // Obtenemos los textos que escribió el usuario
            val codigo = input_codigo.text.toString().trim()
            val nombre = input_nombre.text.toString().trim()
            val ubicacion = input_ubicacion.text.toString().trim()

            // Validamos que no haya dejado nada vacío
            if (codigo.isEmpty() || nombre.isEmpty() || ubicacion.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos de texto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (iconoSeleccionado.isEmpty()) {
                Toast.makeText(this, "Por favor, selecciona un ícono para tu dispositivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Verificación de usuario logeado
            val usuarioActual = auth.currentUser

            if (usuarioActual != null) {
                val uid = usuarioActual.uid

                // Directamente bajo "Dispositivos" usando el Código del Sensor
                val dispositivoRef = database.reference
                    .child("Dispositivos")
                    .child(codigo)

                // Usamos updateChildren para inyectar el owner_uid y metadatos sin borrar la MAC original
                val actualizaciones = mapOf<String, Any>(
                    "owner_uid" to uid, // LA LLAVE FORÁNEA RELACIONAL
                    "nombre" to nombre,
                    "ubicacion" to ubicacion,
                    "icono" to iconoSeleccionado
                )

                // Subimos la actualización a Firebase
                dispositivoRef.updateChildren(actualizaciones)
                    .addOnSuccessListener {
                        Toast.makeText(this, "¡Dispositivo vinculado con éxito!", Toast.LENGTH_SHORT).show()
                        finish() // Cerramos esta pantalla y volvemos al Dashboard
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "Error al vincular: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "Error de sesión. Vuelve a iniciar sesión.", Toast.LENGTH_LONG).show()
            }
        }
    }
}