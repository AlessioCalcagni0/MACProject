package com.example.myapplication

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Attiva la persistenza offline per tutto il ciclo di vita dell'app
        FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app")
            .setPersistenceEnabled(true)
    }
}
