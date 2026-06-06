package com.example.myapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.SyncUserPayload
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.domain.auth.LoginUseCase
import com.example.myapplication.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmailLogin: EditText
    private lateinit var etPasswordLogin: EditText
    private lateinit var btnLoginAccess: Button
    private lateinit var btnGoToRegister: Button
    private lateinit var btnBackLogin: ImageButton

    private val repository = AuthRepositoryImpl()
    private val loginUseCase = LoginUseCase(repository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmailLogin = findViewById(R.id.etEmailLogin)
        etPasswordLogin = findViewById(R.id.etPasswordLogin)
        btnLoginAccess = findViewById(R.id.btnLoginAccess)
        btnGoToRegister = findViewById(R.id.btnGoToRegister)
        btnBackLogin = findViewById(R.id.btnBackLogin)

        btnBackLogin.setOnClickListener {
            finish()
        }

        btnLoginAccess.setOnClickListener {
            loginUser()
        }

        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = etEmailLogin.text.toString().trim()
        val password = etPasswordLogin.text.toString().trim()

        when {
            email.isEmpty() -> {
                etEmailLogin.error = "Please enter your email"
                etEmailLogin.requestFocus()
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmailLogin.error = "Invalid email"
                etEmailLogin.requestFocus()
            }

            password.isEmpty() -> {
                etPasswordLogin.error = "Please enter your password"
                etPasswordLogin.requestFocus()
            }

            else -> {
                lifecycleScope.launch {
                    val success = loginUseCase(email, password)

                    if (success) {
                        // After successful login, force a sync to retrieve data from DB
                        syncUserDataOnLogin()
                        
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun syncUserDataOnLogin() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        try {
            val tokenResult = com.google.android.gms.tasks.Tasks.await(user.getIdToken(true))
            val token = "Bearer ${tokenResult.token}"

            RetrofitClient.api.syncUser(
                token,
                SyncUserPayload(
                    displayName = user.displayName,
                    email = user.email?.lowercase()
                )
            )

            Log.d("LoginActivity", "User synced successfully on login")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Failed to sync user on login", e)
        }
    }
}