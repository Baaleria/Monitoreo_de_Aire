package com.example.monitoreo_de_aire

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.monitoreo_de_aire.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class MainActivity : AppCompatActivity() {

    // Variable de FirebaseAuth para verificar el inicio de sesión
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        //Obtenemos la instancia de Firebase Auth
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya hay una sesión activa (Firebase la mantiene), se entra directo al Dashboard
        if (auth.currentUser != null) {
            irAlDashboard()
            return
        }
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //Botones
        binding.btnLogin.setOnClickListener { iniciarSesion() }
        binding.tvOlvide.setOnClickListener { recuperarContrasena() }
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistrarActivity::class.java))
        }
    }

    //Funcion de inicio de sesión
    private fun iniciarSesion() {
        val email = binding.correo.text.toString().trim()
        // La contraseña NO se recorta: los espacios pueden ser parte de ella
        val password = binding.contrasena.text.toString()

        // Corroboraciones de correo y contraseña
        if (email.isEmpty() || password.isEmpty()) {
            aviso(R.string.msg_login_campos_vacios)
            return
        }

        if (!Validacion.correoValido(email)) {
            aviso(R.string.msg_correo_invalido)
            return
        }

        // Se bloquea el botón mientras se valida, para evitar envíos repetidos
        binding.btnLogin.isEnabled = false

        // Auth verifica que la contraseña seea de ese correo en el servidor
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.btnLogin.isEnabled = true
                if (task.isSuccessful) {
                    aviso(R.string.msg_bienvenida)
                    irAlDashboard()
                } else {
                    // Mensaje genérico: no revela si el correo existe o no
                    val mensaje = when (val error = task.exception) {
                        is FirebaseNetworkException -> R.string.msg_sin_conexion
                        is FirebaseTooManyRequestsException -> R.string.msg_demasiados_intentos
                        is FirebaseAuthException ->
                            if (error.errorCode == "ERROR_TOO_MANY_REQUESTS")
                                R.string.msg_demasiados_intentos
                            else
                                R.string.msg_credenciales_incorrectas
                        else -> R.string.msg_login_error_generico
                    }
                    aviso(mensaje, largo = true)
                }
            }
    }

    // Recuperar contraseña por correo (lo envía Firebase)
    private fun recuperarContrasena() {
        val email = binding.correo.text.toString().trim()
        if (email.isEmpty() || !Validacion.correoValido(email)) {
            aviso(R.string.msg_recuperar_pedir_correo, largo = true)
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                if (task.exception is FirebaseNetworkException) {
                    aviso(R.string.msg_sin_conexion, largo = true)
                } else {
                    // Mismo mensaje exista o no el correo, para no revelar qué cuentas existen
                    aviso(R.string.msg_recuperar_enviado, largo = true)
                }
            }
    }

    // Funcion para aviso
    private fun aviso(@StringRes mensaje: Int, largo: Boolean = false) {
        Toast.makeText(this, mensaje, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // Función para ir al dashboard
    private fun irAlDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}