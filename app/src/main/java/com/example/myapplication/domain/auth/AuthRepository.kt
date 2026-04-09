package com.example.myapplication.domain.auth

import android.content.Context

interface AuthRepository {
    suspend fun login(email: String, password: String): Boolean
    suspend fun register(name: String, surname: String, email: String, password: String): Boolean
    suspend fun signInWithGoogle(context: Context): Boolean
    fun isUserLoggedIn(): Boolean
    fun logout()
}