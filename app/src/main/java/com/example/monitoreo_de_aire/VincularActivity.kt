package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.monitoreo_de_aire.databinding.ActivityVincularBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class VincularActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVincularBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private var iconoSeleccionado: String = ""
    private var tiempoUltimoClicAtras: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVincularBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        configurarAtras()
        configurarIconos()
        binding.btnAnadir.setOnClickListener { vincular() }
    }

    // Retroceso con doble confirmación
    private fun configurarAtras() {
        binding.btnAtras.setOnClickListener {
            val tiempoActual = System.currentTimeMillis()

            // Si la diferencia es menor a 2000 ms (doble clic)
            if (tiempoActual - tiempoUltimoClicAtras < 2000) {
                if (intent.getBooleanExtra("desde_menu", false)) {
                    // Se abrió desde el menú del Dashboard: volvemos y se reabre el menú
                    setResult(RESULT_OK)
                    finish()
                } else {
                    // Flujo de registro: no hay Dashboard debajo, así que lo abrimos
                    irAlDashboard()
                }
            } else {
                aviso(R.string.msg_vincular_atras_confirmar, largo = true)
                tiempoUltimoClicAtras = tiempoActual
            }
        }
    }

    // Los íconos cambian de color al tocarlos
    private fun configurarIconos() {
        val iconos = listOf(
            binding.iconBed to "bed",
            binding.iconDevice to "device",
            binding.iconEco to "eco",
            binding.iconClothes to "clothes",
            binding.iconLeaf to "leaf",
            binding.iconFan to "fan",
            binding.iconHome to "home",
            binding.iconBag to "bag"
        )
        val gris = ContextCompat.getColor(this, R.color.gris_icono)
        val azul = ContextCompat.getColor(this, R.color.azul_primario)

        for ((imageView, nombreIcono) in iconos) {
            imageView.setOnClickListener {
                iconos.forEach { it.first.setColorFilter(gris) }
                imageView.setColorFilter(azul)
                iconoSeleccionado = nombreIcono
            }
        }
    }

    private fun vincular() {
        val codigo = binding.inputCodigo.text.toString().trim().uppercase()
        // Texto libre sanitizado antes de validarlo y guardarlo
        val nombre = Validacion.limpiarTexto(binding.inputNombre.text.toString())
        val ubicacion = Validacion.limpiarTexto(binding.inputUbicacion.text.toString())

        if (codigo.isEmpty() || nombre.isEmpty() || ubicacion.isEmpty()) {
            aviso(R.string.msg_completa_campos)
            return
        }

        // El código es una llave de Firebase: se valida el formato antes de usarlo
        if (!Validacion.codigoDispositivoValido(codigo)) {
            aviso(R.string.msg_codigo_invalido, largo = true)
            return
        }

        if (!Validacion.nombreValido(nombre) || !Validacion.ubicacionValida(ubicacion)) {
            Toast.makeText(
                this,
                getString(
                    R.string.msg_nombre_ubicacion_invalidos,
                    Validacion.MAX_NOMBRE,
                    Validacion.MAX_UBICACION
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (iconoSeleccionado.isEmpty()) {
            aviso(R.string.msg_selecciona_icono)
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            aviso(R.string.msg_sesion_error_login, largo = true)
            return
        }

        val dispositivoRef = database.reference.child("Dispositivos").child(codigo)

        // updateChildren agrega owner_uid y metadatos sin borrar la MAC original
        val actualizaciones = mapOf<String, Any>(
            "owner_uid" to uid, // LA LLAVE FORÁNEA RELACIONAL
            "nombre" to nombre,
            "ubicacion" to ubicacion,
            "icono" to iconoSeleccionado
        )

        dispositivoRef.updateChildren(actualizaciones)
            .addOnSuccessListener {
                aviso(R.string.msg_vinculado)
                DispositivoActivo.guardar(this, uid, codigo)
                irAlDashboard()
            }
            .addOnFailureListener {
                // Las reglas de Firebase rechazan códigos que no existen o que ya tienen otro dueño
                aviso(R.string.msg_vincular_error, largo = true)
            }
    }

    private fun aviso(@StringRes mensaje: Int, largo: Boolean = false) {
        Toast.makeText(this, mensaje, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private fun irAlDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}