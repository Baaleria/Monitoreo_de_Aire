package com.example.monitoreo_de_aire

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class DispositivosActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var tvAviso: TextView

    private var uid: String = ""
    private var query: Query? = null
    private var listener: ValueEventListener? = null
    private var ultimoSnapshot: DataSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dispositivos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dispositivos)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left + dp(24),
                systemBars.top + dp(24),
                systemBars.right + dp(24),
                systemBars.bottom + dp(24)
            )
            insets
        }

        lista = findViewById(R.id.listaDispositivos)
        tvAviso = findViewById(R.id.tvAviso)

        // Vuelve al Dashboard con el menú lateral abierto
        findViewById<View>(R.id.btnAtras).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        // Ir a vincular un dispositivo nuevo
        findViewById<View>(R.id.btnVincularOtro).setOnClickListener {
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
                mostrarAviso("Error de base de datos: ${error.message}")
            }
        }
        listener = escucha
        consulta.addValueEventListener(escucha)
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private fun mostrarAviso(texto: String) {
        tvAviso.text = texto
        tvAviso.visibility = View.VISIBLE
    }

    // Dibuja una tarjeta por cada dispositivo del usuario; la seleccionada va en azul
    private fun mostrar(snapshot: DataSnapshot) {
        lista.removeAllViews()

        if (!snapshot.hasChildren()) {
            mostrarAviso("Aún no tienes dispositivos vinculados.")
            return
        }
        tvAviso.visibility = View.GONE

        val activo = DispositivoActivo.elegir(this, uid, snapshot)?.key
        val inflater = LayoutInflater.from(this)

        for (sensor in snapshot.children) {
            val codigo = sensor.key ?: continue
            val item = inflater.inflate(R.layout.item_dispositivo, lista, false) as CardView
            val tvNombre = item.findViewById<TextView>(R.id.tvNombreDispositivo)
            val ivIcono = item.findViewById<ImageView>(R.id.ivIconoDispositivo)

            val nombre = sensor.child("nombre").getValue(String::class.java)
            tvNombre.text = if (nombre.isNullOrBlank()) codigo else nombre
            ivIcono.setImageResource(iconoPara(sensor.child("icono").getValue(String::class.java)))

            val seleccionado = codigo == activo
            item.setCardBackgroundColor(Color.parseColor(if (seleccionado) "#8FADF3" else "#C8C8C8"))
            val colorTexto = Color.parseColor(if (seleccionado) "#2F6BE8" else "#333333")
            tvNombre.setTextColor(colorTexto)
            ivIcono.setColorFilter(colorTexto)

            item.setOnClickListener {
                DispositivoActivo.guardar(this, uid, codigo)
                ultimoSnapshot?.let { mostrar(it) }
            }
            lista.addView(item)
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