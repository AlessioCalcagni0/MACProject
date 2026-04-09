package com.example.myapplication.data.social

import com.example.myapplication.data.ApiService
import com.example.myapplication.data.FriendRequestResponse
import com.example.myapplication.data.GroupDetailedResponse
import com.example.myapplication.data.GroupInviteResponse
import com.example.myapplication.data.UserResponse
import com.example.myapplication.domain.social.SocialRepository

class SocialRepositoryImpl(private val apiService: ApiService) : SocialRepository {
    override suspend fun getFriends(userId: String): List<UserResponse> = apiService.getFriends(userId)
    override suspend fun getGroups(): List<GroupDetailedResponse> = apiService.getGroups()
    override suspend fun getPendingRequests(userId: String): List<FriendRequestResponse> = apiService.getPendingRequests(userId)
    override suspend fun getPendingGroupInvites(userId: String): List<GroupInviteResponse> = apiService.getPendingGroupInvites(userId)
    override suspend fun sendFriendRequest(fromUserId: String, toUserEmail: String) {
        apiService.sendFriendRequest(fromUserId, toUserEmail)
    }
    override suspend fun respondToFriendRequest(requestId: String, status: String) {
        apiService.respondToFriendRequest(requestId, status)
    }
    override suspend fun respondToGroupInvite(inviteId: String, userId: String, status: String) {
        apiService.respondInvite(inviteId, userId, status)
    }
    override suspend fun createGroup(name: String, creatorId: String): String = apiService.createGroup(name, creatorId).id
    override suspend fun updateGroup(groupId: String, name: String, membersIds: List<String>) {
        apiService.updateGroup(groupId, mapOf("name" to name, "members_ids" to membersIds))
    }
    override suspend fun deleteGroup(groupId: String) {
        apiService.deleteGroup(groupId)
    }
    override suspend fun searchUsers(query: String): List<UserResponse> = apiService.searchUsers(query)
    override suspend fun startGroupRun(groupId: String, organizerId: String): List<String> = apiService.startGroupRun(groupId, organizerId).members
}
