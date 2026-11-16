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
import com.example.monitoreo_de_aire.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            irAlDashboard()
            return
        }
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        binding.btnLogin.setOnClickListener { iniciarSesion() }
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistrarActivity::class.java))
        }
        binding.tvOlvide.setOnClickListener { recuperarContrasena() }
    }

    private fun iniciarSesion() {
        val correo = binding.correo.text.toString().trim()
        val clave = binding.contrasena.text.toString()
        if (!Validacion.correoValido(correo) || clave.isEmpty()) {
            aviso("Escribe un correo válido y tu contraseña.")
            return
        }
        binding.btnLogin.isEnabled = false
        auth.signInWithEmailAndPassword(correo, clave)
            .addOnCompleteListener(this) { tarea ->
                binding.btnLogin.isEnabled = true
                if (tarea.isSuccessful) {
                    irAlDashboard()
                } else {
                    mostrarError("Iniciar sesión", tarea.exception)
                }
            }
    }

    private fun recuperarContrasena() {
        val correo = binding.correo.text.toString().trim()
        if (!Validacion.correoValido(correo)) {
            aviso("Escribe primero tu correo electrónico.")
            return
        }
        auth.sendPasswordResetEmail(correo)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    aviso("Si corresponde, recibirás instrucciones para recuperar tu contraseña.")
                } else {
                    mostrarError("Recuperar contraseña", tarea.exception)
                }
            }
    }

    private fun mostrarError(operacion: String, error: Exception?) {
        Log.e("AUTH_MONITOREO", "Falló: $operacion", error)
        val codigo = (error as? FirebaseAuthException)?.errorCode
            ?: error?.javaClass?.simpleName ?: "SIN_CODIGO"
        val descripcion = when (error) {
            is FirebaseNetworkException -> "No se pudo conectar con Firebase. Revisa Internet."
            is FirebaseTooManyRequestsException -> "Demasiados intentos. Intenta más tarde."
            is FirebaseAuthInvalidCredentialsException -> "Credenciales incorrectas o no válidas."
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" -> "Activa Correo/Contraseña en Firebase Authentication."
                "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada."
                "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos."
                "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED" -> "Revisa la API Key y google-services.json."
                else -> "Firebase rechazó la autenticación."
            }
            else -> "Firebase no pudo completar la solicitud."
        }
        // Este diagnóstico se muestra durante las pruebas; no contiene contraseñas.
        val detalle = error?.localizedMessage?.take(500) ?: "Sin información adicional."
        AlertDialog.Builder(this)
            .setTitle("Error al $operacion")
            .setMessage("$descripcion\n\nCódigo: $codigo\nDetalle: $detalle")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    private fun irAlDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
