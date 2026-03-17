package com.example.myapplication.domain.auth

class RegisterUseCase(
    private val repository: AuthRepository
) {
    operator fun invoke(name: String, email: String, password: String): Boolean {
        return repository.register(name, email, password)
    }
}