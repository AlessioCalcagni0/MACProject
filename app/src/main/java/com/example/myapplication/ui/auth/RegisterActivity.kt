package com.example.myapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.domain.auth.RegisterUseCase
import com.example.myapplication.ui.home.MainActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnCreateAccount: Button
    private lateinit var btnGoToLogin: Button

    private val repository = AuthRepositoryImpl()
    private val registerUseCase = RegisterUseCase(repository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnGoToLogin = findViewById(R.id.btnGoToLogin)

        btnCreateAccount.setOnClickListener {
            registerUser()
        }

        btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        when {
            name.isEmpty() -> {
                etName.error = "Inserisci il nome"
                etName.requestFocus()
            }

            email.isEmpty() -> {
                etEmail.error = "Inserisci l'email"
                etEmail.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Email non valida"
                etEmail.requestFocus()
            }

            password.isEmpty() -> {
                etPassword.error = "Inserisci la password"
                etPassword.requestFocus()
            }

            password.length < 6 -> {
                etPassword.error = "Almeno 6 caratteri"
                etPassword.requestFocus()
            }

            confirmPassword.isEmpty() -> {
                etConfirmPassword.error = "Conferma la password"
                etConfirmPassword.requestFocus()
            }

            password != confirmPassword -> {
                etConfirmPassword.error = "Le password non coincidono"
                etConfirmPassword.requestFocus()
            }

            else -> {
                val success = registerUseCase(name, email, password)

                if (success) {
                    Toast.makeText(this, "Registrazione completata", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Errore nella registrazione", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}