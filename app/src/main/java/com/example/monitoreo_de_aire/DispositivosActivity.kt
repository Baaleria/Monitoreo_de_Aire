package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityDispositivosBinding
import com.example.monitoreo_de_aire.databinding.ItemDispositivoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class DispositivosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDispositivosBinding

    private var uid: String = ""
    private var query: Query? = null
    private var listener: ValueEventListener? = null
    private var ultimoSnapshot: DataSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDispositivosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.dispositivos) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left + dp(24),
                systemBars.top + dp(24),
                systemBars.right + dp(24),
                systemBars.bottom + dp(24)
            )
            insets
        }

        // Vuelve al Dashboard con el menú lateral abierto
        binding.btnAtras.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        // Ir a vincular un dispositivo nuevo
        binding.btnVincularOtro.setOnClickListener {
            startActivity(Intent(this, VincularActivity::class.java).putExtra("desde_menu", true))
        }

        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario == null) {
            finish()
            return
        }
        uid = usuario.uid

        val consulta = FirebaseDatabase.getInstance().reference
            .child("Dispositivos")
            .orderByChild("owner_uid")
            .equalTo(uid)
        query = consulta

        val escucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimoSnapshot = snapshot
                mostrar(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                if (isFinishing || FirebaseAuth.getInstance().currentUser == null) return
                mostrarAviso(getString(R.string.msg_error_bd, error.message))
            }
        }
        listener = escucha
        consulta.addValueEventListener(escucha)
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    private fun mostrarAviso(texto: String) {
        binding.tvAviso.text = texto
        binding.tvAviso.visibility = View.VISIBLE
    }

    // Dibuja una tarjeta por cada dispositivo del usuario; la seleccionada va en azul
    private fun mostrar(snapshot: DataSnapshot) {
        binding.listaDispositivos.removeAllViews()

        if (!snapshot.hasChildren()) {
            mostrarAviso(getString(R.string.aviso_sin_dispositivos))
            return
        }
        binding.tvAviso.visibility = View.GONE

        val activo = DispositivoActivo.elegir(this, uid, snapshot)?.key
        val inflater = LayoutInflater.from(this)

        for (sensor in snapshot.children) {
            val codigo = sensor.key ?: continue
            val item = ItemDispositivoBinding.inflate(inflater, binding.listaDispositivos, false)

            val nombre = sensor.child("nombre").getValue(String::class.java)
            item.tvNombreDispositivo.text = if (nombre.isNullOrBlank()) codigo else nombre
            item.ivIconoDispositivo.setImageResource(
                iconoPara(sensor.child("icono").getValue(String::class.java))
            )

            // Estado de conexión del dispositivo (lo publica el ESP32)
            val conectado = sensor.child("estadoConexion").value as? Boolean ?: false
            item.tvEstadoDispositivo.setText(
                if (conectado) R.string.estado_conectado else R.string.estado_desconectado
            )
            item.tvEstadoDispositivo.setTextColor(
                color(if (conectado) R.color.verde_encendido else R.color.rojo_apagado)
            )

            val seleccionado = codigo == activo
            item.root.setCardBackgroundColor(
                color(if (seleccionado) R.color.azul_seleccion_fondo else R.color.gris_tarjeta)
            )
            val colorTexto = color(if (seleccionado) R.color.azul_seleccion_texto else R.color.gris_texto)
            item.tvNombreDispositivo.setTextColor(colorTexto)
            item.ivIconoDispositivo.setColorFilter(colorTexto)

            item.root.setOnClickListener {
                DispositivoActivo.guardar(this, uid, codigo)
                ultimoSnapshot?.let { mostrar(it) }
            }
            binding.listaDispositivos.addView(item.root)
        }
    }

    // Mismos íconos que se ofrecen en la pantalla de vincular
    private fun iconoPara(icono: String?): Int = when (icono) {
        "bed", "eco", "leaf", "home" -> R.drawable.ic_bed_outline
        else -> R.drawable.baseline_mode_fan_off_24
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.let { query?.removeEventListener(it) }
    }
}