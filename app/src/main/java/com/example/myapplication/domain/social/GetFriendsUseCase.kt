package com.example.myapplication.domain.social

import com.example.myapplication.data.UserResponse

class GetFriendsUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(userId: String): List<UserResponse> {
        return repository.getFriends(userId)
    }
}
