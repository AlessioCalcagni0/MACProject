package com.example.myapplication.data.auth

import android.content.Context
import com.example.myapplication.domain.auth.AuthRepository

class AuthRepositoryImpl : AuthRepository {

    override suspend fun login(email: String, password: String): Boolean {
        return AuthFirebaseDataSource.login(email, password)
    }

    override suspend fun register(name: String, surname: String, email: String, password: String): Boolean {
        return AuthFirebaseDataSource.register(name, surname, email, password)
    }

    override suspend fun signInWithGoogle(context: Context): Boolean {
        return AuthFirebaseDataSource.signInWithGoogle(context)
    }

    override fun isUserLoggedIn(): Boolean {
        return AuthFirebaseDataSource.isUserLoggedIn()
    }

    override fun logout() {
        AuthFirebaseDataSource.logout()
    }
}