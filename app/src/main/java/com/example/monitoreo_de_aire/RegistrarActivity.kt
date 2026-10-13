package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase

class RegistrarActivity : AppCompatActivity() {

    // Llamamos a firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registrar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registrar)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializamos Firebase Auth y Realtime Database
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Vinculamos las vistas del XML
        val etCorreo = findViewById<EditText>(R.id.Correo_electronico)
        val etUsuario = findViewById<EditText>(R.id.usuario)
        val etPass1 = findViewById<EditText>(R.id.contrasenareg1)
        val etPass2 = findViewById<EditText>(R.id.contrasenareg2)
        val btnRegistrar = findViewById<Button>(R.id.btnRegistrar)
        val btnVolver = findViewById<ImageButton>(R.id.tvVolver)

        // Botones
        btnVolver.setOnClickListener {
            finish()
        }

        btnRegistrar.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            val nombre = etUsuario.text.toString().trim()
            // Las contraseñas no se recortan
            val pass1 = etPass1.text.toString()
            val pass2 = etPass2.text.toString()

            // Validaciones
            if (email.isEmpty() || nombre.isEmpty() || pass1.isEmpty() || pass2.isEmpty()) {
                Toast.makeText(this, "⚠️ Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Validacion.correoValido(email)) {
                Toast.makeText(this, "⚠️ El formato del correo no es válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val errorPass = Validacion.errorPassword(pass1)
            if (errorPass != null) {
                Toast.makeText(this, errorPass, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (pass1 != pass2) {
                Toast.makeText(this, "❌ Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Se bloquea el botón para evitar crear la cuenta dos veces
            btnRegistrar.isEnabled = false

            // Creamos la cuenta en Firebase Authentication
            auth.createUserWithEmailAndPassword(email, pass1)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        guardarPerfilYContinuar(nombre, email)
                    } else {
                        btnRegistrar.isEnabled = true
                        // Mensajes propios: no se muestra el texto técnico de Firebase
                        val mensaje = when (task.exception) {
                            is FirebaseAuthUserCollisionException -> "❌ Este correo ya está registrado"
                            is FirebaseAuthWeakPasswordException -> "❌ La contraseña es demasiado débil"
                            is FirebaseNetworkException -> "❌ Sin conexión. Revisa tu Wi-Fi o datos móviles."
                            else -> "❌ No se pudo crear la cuenta. Inténtalo de nuevo."
                        }
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    // Guarda nombre y correo en Usuarios/{uid} y sigue a Vincular
    private fun guardarPerfilYContinuar(nombre: String, email: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Error de sesión. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val userData = hashMapOf(
            "nombre" to nombre,
            "correo" to email
        )

        database.reference.child("Usuarios").child(uid).setValue(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Cuenta creada exitosamente", Toast.LENGTH_LONG).show()
                irAVincular()
            }
            .addOnFailureListener {
                // La cuenta ya existe: se continúa para que el usuario no quede bloqueado
                Toast.makeText(this, "⚠️ Cuenta creada, pero no se pudo guardar el nombre", Toast.LENGTH_SHORT).show()
                irAVincular()
            }
    }

    private fun irAVincular() {
        startActivity(Intent(this, VincularActivity::class.java))
        finish()
    }
}