package com.example.monitoreo_de_aire

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
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
            val pass1 = etPass1.text.toString().trim()
            val pass2 = etPass2.text.toString().trim()

            // Validaciones
            if (email.isEmpty() || nombre.isEmpty() || pass1.isEmpty() || pass2.isEmpty()) {
                Toast.makeText(this, "⚠️ Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass1.length < 6) {
                Toast.makeText(this, "⚠️ La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass1 != pass2) {
                Toast.makeText(this, "❌ Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Creamos la cuenta en Firebase Authentication
            auth.createUserWithEmailAndPassword(email, pass1)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid

                        if (uid != null) {
                            val userReference = database.reference.child("Usuarios").child(uid)

                            // Diccionario de datos.
                            val userData = hashMapOf(
                                "nombre" to nombre,
                                "correo" to email
                            )

                            userReference.setValue(userData)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "✅ Cuenta creada exitosamente", Toast.LENGTH_LONG).show()
                                    // Regresamos para iniciar sesión
                                    finish()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "⚠️ Cuenta creada, pero falló al guardar el nombre", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "❌ Error al crear cuenta: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}