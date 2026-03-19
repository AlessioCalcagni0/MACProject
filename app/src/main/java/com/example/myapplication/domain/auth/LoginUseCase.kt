package com.example.myapplication.domain.auth

class LoginUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Boolean {
        return repository.login(email, password)
    }
}