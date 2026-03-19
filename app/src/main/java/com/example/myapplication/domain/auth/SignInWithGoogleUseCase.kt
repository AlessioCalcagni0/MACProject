package com.example.myapplication.domain.auth

import android.content.Context

class SignInWithGoogleUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(context: Context): Boolean {
        return repository.signInWithGoogle(context)
    }
}