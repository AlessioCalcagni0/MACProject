package com.example.myapplication.domain.auth

class RegisterUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(name: String, surname: String, email: String, password: String): Boolean {
        return repository.register(name, surname, email, password)
    }
}