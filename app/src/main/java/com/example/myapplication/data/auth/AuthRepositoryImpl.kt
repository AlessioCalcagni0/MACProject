package com.example.myapplication.data.auth

import com.example.myapplication.domain.auth.AuthRepository

class AuthRepositoryImpl : AuthRepository {

    override fun login(email: String, password: String): Boolean {
        return AuthFirebaseDataSource.login(email, password)
    }

    override fun register(name: String, email: String, password: String): Boolean {
        return AuthFirebaseDataSource.register(name, email, password)
    }

    override fun signInWithGoogle(): Boolean {
        return AuthFirebaseDataSource.signInWithGoogle()
    }

    override fun isUserLoggedIn(): Boolean {
        return AuthFirebaseDataSource.isUserLoggedIn()
    }

    override fun logout() {
        AuthFirebaseDataSource.logout()
    }
}
