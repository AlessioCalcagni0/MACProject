package com.example.myapplication.domain.auth

interface AuthRepository {
    fun login(email: String, password: String): Boolean
    fun register(name: String, email: String, password: String): Boolean
    fun signInWithGoogle(): Boolean
    fun isUserLoggedIn(): Boolean
    fun logout()
}