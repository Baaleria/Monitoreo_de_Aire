package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    // Llamamos Firebase
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializamos Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Llamamos los id
        val etCorreo = findViewById<EditText>(R.id.correo)
        val etPassword = findViewById<EditText>(R.id.contrasena)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)

        // Botón de Ingresar
        btnLogin.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Verificamos que no intenten entrar con campos vacíos
            if (email.isNotEmpty() && password.isNotEmpty()) {

                // Le pedimos a Firebase que inicie sesión
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // ¡Login exitoso!
                            Toast.makeText(this, "Bienvenido a Monitoreo de Aire", Toast.LENGTH_SHORT).show()

                            // Quitar estas barras cuando el dashboard este realizado

                            // val intent = Intent(this, DashboardActivity::class.java)
                            // startActivity(intent)
                            // finish() // Esto evita que el usuario vuelva al login si presiona "Atrás"

                        } else {
                            // Error (Contraseña incorrecta, usuario no existe, etc.)
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor, ingresa tu correo y contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        // Lógica para navegar a la pantalla de Registro aun no implementado
        tvCreateAccount.setOnClickListener {
            // Cuando crees tu RegistrarActivity, quítale las barras (//) a este código:

            // val intent = Intent(this, RegistrarActivity::class.java)
            // startActivity(intent)
        }
    }
}