package com.example.monitoreo_de_aire

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class MainActivity : AppCompatActivity() {

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

        auth = FirebaseAuth.getInstance()

        val etCorreo = findViewById<EditText>(R.id.correo)
        val etPassword = findViewById<EditText>(R.id.contrasena)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)

        btnLogin.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Alerta si los campos están vacíos
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this@MainActivity, "⚠️ Por favor, ingresa tu correo y contraseña.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Detiene la ejecución aquí
            }

            // Inicio de sesión
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this@MainActivity) { task ->
                    if (task.isSuccessful) {
                        // Si ingresa bien
                        Toast.makeText(this@MainActivity, "✅ ¡Bienvenido!", Toast.LENGTH_SHORT).show()
                        // Redireccionamiendo a la pagina de inicio
                        // val intent = Intent(this, DashboardActivity::class.java)
                        // startActivity(intent)
                        // finish()

                    } else {
                        // Alertas de error
                        try {
                            throw task.exception!!
                        } catch (e: FirebaseAuthInvalidUserException) {
                            Toast.makeText(this@MainActivity, "❌ Este correo no está registrado.", Toast.LENGTH_LONG).show()
                        } catch (e: FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this@MainActivity, "❌ La contraseña es incorrecta.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "❌ Error de conexión. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }

        tvCreateAccount.setOnClickListener {
            val intent = Intent(this, RegistrarActivity::class.java)
            startActivity(intent)
        }
    }
}