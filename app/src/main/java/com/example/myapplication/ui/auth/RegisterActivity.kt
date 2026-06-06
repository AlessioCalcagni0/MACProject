package com.example.myapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.domain.auth.RegisterUseCase
import com.example.myapplication.ui.home.MainActivity
import kotlinx.coroutines.launch
import android.util.Log
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.SyncUserPayload
import com.google.firebase.auth.FirebaseAuth


class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etSurname: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnCreateAccount: Button
    private lateinit var btnGoToLogin: Button
    private lateinit var btnBackRegister: ImageButton

    private val repository = AuthRepositoryImpl()
    private val registerUseCase = RegisterUseCase(repository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etName = findViewById(R.id.etName)
        etSurname = findViewById(R.id.etSurname)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnGoToLogin = findViewById(R.id.btnGoToLogin)
        btnBackRegister = findViewById(R.id.btnBackRegister)

        btnBackRegister.setOnClickListener {
            finish()
        }

        btnCreateAccount.setOnClickListener {
            registerUser()
        }

        btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    private suspend fun syncUserDataOnRegister(
        name: String,
        surname: String,
        email: String
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        try {
            val tokenResult = com.google.android.gms.tasks.Tasks.await(user.getIdToken(true))
            val token = "Bearer ${tokenResult.token}"

            RetrofitClient.api.syncUser(
                token,
                SyncUserPayload(
                    name = name,
                    surname = surname,
                    displayName = "$name $surname",
                    email = email.lowercase()
                )
            )

            Log.d("RegisterActivity", "User synced successfully after registration")
        } catch (e: Exception) {
            Log.e("RegisterActivity", "Failed to sync user after registration", e)
        }
    }

    private fun registerUser() {
        val name = etName.text.toString().trim()
        val surname = etSurname.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        when {
            name.isEmpty() -> {
                etName.error = "Please enter your name"
                etName.requestFocus()
            }

            surname.isEmpty() -> {
                etSurname.error = "Please enter your surname"
                etSurname.requestFocus()
            }

            email.isEmpty() -> {
                etEmail.error = "Please enter your email"
                etEmail.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Invalid email"
                etEmail.requestFocus()
            }

            password.isEmpty() -> {
                etPassword.error = "Please enter a password"
                etPassword.requestFocus()
            }

            password.length < 6 -> {
                etPassword.error = "At least 6 characters"
                etPassword.requestFocus()
            }

            confirmPassword.isEmpty() -> {
                etConfirmPassword.error = "Please confirm your password"
                etConfirmPassword.requestFocus()
            }

            password != confirmPassword -> {
                etConfirmPassword.error = "Passwords do not match"
                etConfirmPassword.requestFocus()
            }

            else -> {
                lifecycleScope.launch {
                    val success = registerUseCase(name, surname, email, password)

                    if (success) {
                        syncUserDataOnRegister(name, surname, email)

                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration successful",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}