package com.example.myapplication.data.auth

object AuthFirebaseDataSource {
    private var loggedIn = false

    fun login(email: String, password: String): Boolean {
        loggedIn = email.isNotEmpty() && password.isNotEmpty()
        return loggedIn
    }

    fun register(name: String, email: String, password: String): Boolean {
        loggedIn = name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()
        return loggedIn
    }

    fun signInWithGoogle(): Boolean {
        loggedIn = true
        return loggedIn
    }

    fun isUserLoggedIn(): Boolean {
        return loggedIn
    }

    fun logout() {
        loggedIn = false
    }
}