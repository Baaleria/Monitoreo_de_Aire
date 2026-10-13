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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya hay una sesión activa (Firebase la mantiene), se entra directo al Dashboard
        if (auth.currentUser != null) {
            irAlDashboard()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etCorreo = findViewById<EditText>(R.id.correo)
        val etPassword = findViewById<EditText>(R.id.contrasena)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)
        val tvOlvide = findViewById<TextView>(R.id.tvOlvide)

        btnLogin.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            // La contraseña NO se recorta: los espacios pueden ser parte de ella
            val password = etPassword.text.toString()

            // Alerta si los campos están vacíos
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "⚠️ Por favor, ingresa tu correo y contraseña.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Validacion.correoValido(email)) {
                Toast.makeText(this, "⚠️ El formato del correo no es válido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Se bloquea el botón mientras se valida, para evitar envíos repetidos
            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        Toast.makeText(this, "✅ ¡Bienvenido!", Toast.LENGTH_SHORT).show()
                        irAlDashboard()
                    } else {
                        // Mensaje genérico: no revela si el correo existe o no
                        val mensaje = when (task.exception) {
                            is FirebaseNetworkException ->
                                "❌ Sin conexión. Revisa tu Wi-Fi o datos móviles."
                            is FirebaseTooManyRequestsException ->
                                "❌ Demasiados intentos. Espera unos minutos."
                            is FirebaseAuthException ->
                                if ((task.exception as FirebaseAuthException).errorCode == "ERROR_TOO_MANY_REQUESTS")
                                    "❌ Demasiados intentos. Espera unos minutos."
                                else
                                    "❌ Correo o contraseña incorrectos."
                            else -> "❌ No se pudo iniciar sesión. Inténtalo de nuevo."
                        }
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Recuperar contraseña por correo (lo envía Firebase)
        tvOlvide.setOnClickListener {
            val email = etCorreo.text.toString().trim()
            if (email.isEmpty() || !Validacion.correoValido(email)) {
                Toast.makeText(this, "⚠️ Escribe tu correo en el campo para recuperar la contraseña.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this) { task ->
                    if (task.exception is FirebaseNetworkException) {
                        Toast.makeText(this, "❌ Sin conexión. Revisa tu Wi-Fi o datos móviles.", Toast.LENGTH_LONG).show()
                    } else {
                        // Mismo mensaje exista o no el correo, para no revelar qué cuentas existen
                        Toast.makeText(
                            this,
                            "📧 Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistrarActivity::class.java))
        }
    }

    private fun irAlDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}