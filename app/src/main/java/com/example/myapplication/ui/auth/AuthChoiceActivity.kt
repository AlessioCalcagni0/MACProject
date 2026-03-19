package com.example.myapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.domain.auth.SignInWithGoogleUseCase
import com.example.myapplication.ui.home.MainActivity
import kotlinx.coroutines.launch

class AuthChoiceActivity : AppCompatActivity() {

    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnGoogle: Button

    private val repository = AuthRepositoryImpl()
    private val signInWithGoogleUseCase = SignInWithGoogleUseCase(repository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_choice)

        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnGoogle = findViewById(R.id.btnGoogle)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnGoogle.setOnClickListener {
            lifecycleScope.launch {
                val success = signInWithGoogleUseCase(this@AuthChoiceActivity)

                if (success) {
                    Toast.makeText(this@AuthChoiceActivity, "Accesso con Google effettuato", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@AuthChoiceActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@AuthChoiceActivity, "Errore accesso Google", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}