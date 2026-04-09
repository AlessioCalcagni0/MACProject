package com.example.myapplication.domain.social

import com.example.myapplication.data.FriendRequestResponse
import com.example.myapplication.data.GroupDetailedResponse
import com.example.myapplication.data.GroupInviteResponse
import com.example.myapplication.data.UserResponse

interface SocialRepository {
    suspend fun getFriends(userId: String): List<UserResponse>
    suspend fun getGroups(): List<GroupDetailedResponse>
    suspend fun getPendingRequests(userId: String): List<FriendRequestResponse>
    suspend fun getPendingGroupInvites(userId: String): List<GroupInviteResponse>
    suspend fun sendFriendRequest(fromUserId: String, toUserEmail: String)
    suspend fun respondToFriendRequest(requestId: String, status: String)
    suspend fun respondToGroupInvite(inviteId: String, userId: String, status: String)
    suspend fun createGroup(name: String, creatorId: String): String
    suspend fun updateGroup(groupId: String, name: String, membersIds: List<String>)
    suspend fun deleteGroup(groupId: String)
    suspend fun searchUsers(query: String): List<UserResponse>
    suspend fun startGroupRun(groupId: String, organizerId: String): List<String>
}
