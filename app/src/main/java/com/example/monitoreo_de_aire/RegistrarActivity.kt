package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityRegistrarBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase

class RegistrarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrarBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegistrarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.registrar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializamos Firebase Auth y Realtime Database
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        binding.tvVolver.setOnClickListener { finish() }
        binding.btnRegistrar.setOnClickListener { registrar() }
    }

    private fun registrar() {
        val email = binding.correoElectronico.text.toString().trim()
        // El nombre se sanitiza antes de validarlo y guardarlo
        val nombre = Validacion.limpiarTexto(binding.usuario.text.toString())
        // Las contraseñas no se recortan
        val pass1 = binding.contrasenareg1.text.toString()
        val pass2 = binding.contrasenareg2.text.toString()

        if (email.isEmpty() || nombre.isEmpty() || pass1.isEmpty() || pass2.isEmpty()) {
            aviso(R.string.msg_campos_vacios)
            return
        }

        if (!Validacion.correoValido(email)) {
            aviso(R.string.msg_correo_invalido)
            return
        }

        if (!Validacion.nombreValido(nombre)) {
            Toast.makeText(
                this,
                getString(R.string.msg_nombre_invalido, Validacion.MAX_NOMBRE),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val errorPass = Validacion.errorPassword(pass1)
        if (errorPass != null) {
            Toast.makeText(this, mensajePassword(errorPass), Toast.LENGTH_LONG).show()
            return
        }

        if (pass1 != pass2) {
            aviso(R.string.msg_pass_no_coinciden)
            return
        }

        // Se bloquea el botón para evitar crear la cuenta dos veces
        binding.btnRegistrar.isEnabled = false

        // Creamos la cuenta en Firebase Authentication
        auth.createUserWithEmailAndPassword(email, pass1)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    guardarPerfilYContinuar(nombre, email)
                } else {
                    binding.btnRegistrar.isEnabled = true
                    // Mensajes propios: no se muestra el texto técnico de Firebase
                    val mensaje = when (task.exception) {
                        is FirebaseAuthUserCollisionException -> R.string.msg_correo_registrado
                        is FirebaseAuthWeakPasswordException -> R.string.msg_pass_debil
                        is FirebaseNetworkException -> R.string.msg_sin_conexion
                        else -> R.string.msg_registro_error_generico
                    }
                    aviso(mensaje, largo = true)
                }
            }
    }

    private fun mensajePassword(error: Validacion.ErrorPassword): String = when (error) {
        Validacion.ErrorPassword.CORTA -> getString(R.string.msg_pass_corta, Validacion.MIN_PASSWORD)
        Validacion.ErrorPassword.LARGA -> getString(R.string.msg_pass_larga, Validacion.MAX_PASSWORD)
        Validacion.ErrorPassword.SIN_LETRA -> getString(R.string.msg_pass_sin_letra)
        Validacion.ErrorPassword.SIN_NUMERO -> getString(R.string.msg_pass_sin_numero)
    }

    // Guarda nombre y correo en Usuarios/{uid} y sigue a Vincular
    private fun guardarPerfilYContinuar(nombre: String, email: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            aviso(R.string.msg_sesion_error, largo = true)
            finish()
            return
        }

        val userData = hashMapOf(
            "nombre" to nombre,
            "correo" to email
        )

        database.reference.child("Usuarios").child(uid).setValue(userData)
            .addOnSuccessListener {
                aviso(R.string.msg_cuenta_creada, largo = true)
                irAVincular()
            }
            .addOnFailureListener {
                // La cuenta ya existe: se continúa para que el usuario no quede bloqueado
                aviso(R.string.msg_cuenta_creada_sin_nombre)
                irAVincular()
            }
    }

    private fun aviso(@StringRes mensaje: Int, largo: Boolean = false) {
        Toast.makeText(this, mensaje, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private fun irAVincular() {
        startActivity(Intent(this, VincularActivity::class.java))
        finish()
    }
}