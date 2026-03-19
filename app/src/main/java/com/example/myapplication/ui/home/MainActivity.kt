package com.example.myapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.data.auth.AuthRepositoryImpl
import com.example.myapplication.ui.auth.AuthChoiceActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var tvUserEmail: TextView
    private val repository = AuthRepositoryImpl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLogout = findViewById(R.id.btnLogout)
        tvUserEmail = findViewById(R.id.tvUserEmail)

        // Recuperiamo l'utente corrente da Firebase (tramite FirebaseAuth direttamente per semplicità di visualizzazione)
        val currentUser = FirebaseAuth.getInstance().currentUser
        tvUserEmail.text = "Benvenuto,\n${currentUser?.email ?: "Utente"}"

        btnLogout.setOnClickListener {
            repository.logout()
            val intent = Intent(this, AuthChoiceActivity::class.java)
            // Puliamo lo stack delle activity per evitare che l'utente torni indietro con il tasto back
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
