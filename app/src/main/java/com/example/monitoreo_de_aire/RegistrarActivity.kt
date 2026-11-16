package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityRegistrarBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.registrar) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        binding.tvVolver.setOnClickListener { finish() }
        binding.btnRegistrar.setOnClickListener { registrar() }
    }

    private fun registrar() {
        val correo = binding.correoElectronico.text.toString().trim()
        val nombre = Validacion.limpiarTexto(binding.usuario.text.toString())
        val clave = binding.contrasenareg1.text.toString()
        val confirmar = binding.contrasenareg2.text.toString()
        if (correo.isEmpty() || nombre.isEmpty() || clave.isEmpty() || confirmar.isEmpty()) {
            aviso("Completa todos los campos.")
            return
        }
        if (!Validacion.correoValido(correo)) {
            aviso("Ingresa un correo electrónico válido.")
            return
        }
        if (!Validacion.nombreValido(nombre)) {
            aviso("El nombre es inválido o demasiado largo.")
            return
        }
        when (Validacion.errorPassword(clave)) {
            Validacion.ErrorPassword.CORTA -> { aviso("La contraseña necesita al menos 8 caracteres."); return }
            Validacion.ErrorPassword.LARGA -> { aviso("La contraseña es demasiado larga."); return }
            Validacion.ErrorPassword.SIN_LETRA -> { aviso("La contraseña necesita al menos una letra."); return }
            Validacion.ErrorPassword.SIN_NUMERO -> { aviso("La contraseña necesita al menos un número."); return }
            null -> Unit
        }
        if (clave != confirmar) {
            aviso("Las contraseñas no coinciden.")
            return
        }
        binding.btnRegistrar.isEnabled = false
        auth.createUserWithEmailAndPassword(correo, clave)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    val uid = tarea.result?.user?.uid ?: auth.currentUser?.uid
                    if (uid == null) {
                        binding.btnRegistrar.isEnabled = true
                        aviso("Cuenta creada, pero no se obtuvo el usuario. Prueba iniciar sesión.")
                    } else {
                        guardarPerfil(uid, nombre)
                    }
                } else {
                    binding.btnRegistrar.isEnabled = true
                    mostrarError(tarea.exception)
                }
            }
    }

    private fun guardarPerfil(uid: String, nombre: String) {
        database.reference.child("Usuarios").child(uid)
            .setValue(mapOf("nombre" to nombre))
            .addOnSuccessListener {
                binding.btnRegistrar.isEnabled = true
                aviso("Cuenta creada correctamente.")
                irAVincular()
            }
            .addOnFailureListener { error ->
                binding.btnRegistrar.isEnabled = true
                Log.e("AUTH_MONITOREO", "Error al guardar perfil", error)
                AlertDialog.Builder(this)
                    .setTitle("Cuenta creada")
                    .setMessage("La cuenta se creó en Authentication, pero falló el guardado del nombre en Database.\n\nDetalle: ${error.localizedMessage}")
                    .setPositiveButton("Continuar") { _, _ -> irAVincular() }
                    .show()
            }
    }

    private fun mostrarError(error: Exception?) {
        Log.e("AUTH_MONITOREO", "Error al registrar", error)
        val codigo = (error as? FirebaseAuthException)?.errorCode
            ?: error?.javaClass?.simpleName ?: "SIN_CODIGO"
        val descripcion = when (error) {
            is FirebaseAuthUserCollisionException -> "El correo ya está registrado. Prueba iniciar sesión."
            is FirebaseAuthWeakPasswordException -> "Firebase rechazó la contraseña por ser débil."
            is FirebaseNetworkException -> "No hay conexión con Firebase."
            is FirebaseTooManyRequestsException -> "Demasiados intentos."
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" -> "Activa Correo/Contraseña en Firebase Authentication."
                "ERROR_INVALID_EMAIL" -> "El correo no es válido."
                "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY" -> "Comprueba google-services.json y la API Key."
                else -> "Firebase rechazó la creación de la cuenta."
            }
            else -> "Firebase no pudo completar el registro."
        }
        val detalle = error?.localizedMessage?.take(500) ?: "Sin información adicional."
        AlertDialog.Builder(this)
            .setTitle("Error al crear cuenta")
            .setMessage("$descripcion\n\nCódigo: $codigo\nDetalle: $detalle")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun irAVincular() {
        startActivity(Intent(this, VincularActivity::class.java))
        finish()
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }
}