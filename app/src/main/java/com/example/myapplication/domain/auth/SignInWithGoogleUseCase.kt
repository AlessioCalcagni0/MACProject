package com.example.myapplication.domain.auth

class SignInWithGoogleUseCase(
    private val repository: AuthRepository
) {
    operator fun invoke(): Boolean {
        return repository.signInWithGoogle()
    }
}