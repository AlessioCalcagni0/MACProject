package com.example.myapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.ui.auth.AuthChoiceActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private val repository = AuthRepositoryImpl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLogout = findViewById(R.id.btnLogout)

        btnLogout.setOnClickListener {
            repository.logout()
            startActivity(Intent(this, AuthChoiceActivity::class.java))
            finish()
        }
    }
}
