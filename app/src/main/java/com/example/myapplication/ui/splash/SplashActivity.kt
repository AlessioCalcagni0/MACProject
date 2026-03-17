package com.example.myapplication.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.domain.auth.IsUserLoggedInUseCase
import com.example.myapplication.ui.auth.AuthChoiceActivity
import com.example.myapplication.ui.home.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val repository = AuthRepositoryImpl()
    private val isUserLoggedInUseCase = IsUserLoggedInUseCase(repository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Aggiungiamo un ritardo di 2 secondi per mostrare lo splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            if (isUserLoggedInUseCase()) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, AuthChoiceActivity::class.java))
            }
            finish()
        }, 2000)
    }
}