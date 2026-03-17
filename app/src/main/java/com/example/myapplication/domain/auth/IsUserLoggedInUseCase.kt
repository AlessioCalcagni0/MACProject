package com.example.myapplication.domain.auth

class IsUserLoggedInUseCase(
    private val repository: AuthRepository
) {
    operator fun invoke(): Boolean {
        return repository.isUserLoggedIn()
    }
}