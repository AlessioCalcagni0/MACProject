package com.example.myapplication.domain.social

import com.example.myapplication.data.GroupDetailedResponse

class GetGroupsUseCase(private val repository: SocialRepository) {
    suspend operator fun invoke(): List<GroupDetailedResponse> {
        return repository.getGroups()
    }
}
