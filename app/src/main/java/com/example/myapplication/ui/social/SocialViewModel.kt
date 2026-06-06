package com.example.myapplication.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.FriendRequestResponse
import com.example.myapplication.data.GroupDetailedResponse
import com.example.myapplication.data.GroupInviteResponse
import com.example.myapplication.data.UserResponse
import com.example.myapplication.domain.social.SocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SocialViewModel(private val repository: SocialRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<SocialUiState>(SocialUiState.Loading)
    val uiState: StateFlow<SocialUiState> = _uiState
    
    private var lastUserId: String? = null

    fun loadData(userId: String) {
        lastUserId = userId
        viewModelScope.launch {
            _uiState.value = SocialUiState.Loading
            try {
                val friends = repository.getFriends(userId)
                val allGroups = repository.getGroups()
                val myGroups = allGroups.filter { it.membersIds?.contains(userId) == true }
                val friendRequests = try { repository.getPendingRequests(userId) } catch (e: Exception) { emptyList() }
                val groupInvites = try { repository.getPendingGroupInvites(userId) } catch (e: Exception) { emptyList() }

                _uiState.value = SocialUiState.Success(
                    friends = friends,
                    groups = myGroups,
                    friendRequests = friendRequests,
                    groupInvites = groupInvites
                )
            } catch (e: Exception) {
                _uiState.value = SocialUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun retryConnection() {
        val uid = lastUserId
        if (uid != null && _uiState.value is SocialUiState.Error) {
            loadData(uid)
        }
    }

    fun sendFriendRequest(fromUserId: String, toUserEmail: String) {
        viewModelScope.launch {
            try {
                repository.sendFriendRequest(fromUserId, toUserEmail)
            } catch (e: Exception) {}
        }
    }

    fun respondToFriendRequest(requestId: String, status: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.respondToFriendRequest(requestId, status)
                loadData(userId)
            } catch (e: Exception) {}
        }
    }

    fun respondToGroupInvite(inviteId: String, userId: String, status: String) {
        viewModelScope.launch {
            try {
                repository.respondToGroupInvite(inviteId, userId, status)
                loadData(userId)
            } catch (e: Exception) {}
        }
    }

    fun createGroup(name: String, creatorId: String, friendIds: List<String>) {
        viewModelScope.launch {
            try {
                val groupId = repository.createGroup(name, creatorId)
                val finalMembers = friendIds.toMutableList().apply { if (creatorId !in this) add(creatorId) }
                repository.updateGroup(groupId, name, finalMembers)
                loadData(creatorId)
            } catch (e: Exception) {}
        }
    }

    fun updateGroup(groupId: String, name: String, membersIds: List<String>, userId: String) {
        viewModelScope.launch {
            try {
                repository.updateGroup(groupId, name, membersIds)
                loadData(userId)
            } catch (e: Exception) {}
        }
    }

    fun deleteGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.deleteGroup(groupId)
                loadData(userId)
            } catch (e: Exception) {}
        }
    }

    suspend fun startGroupRun(groupId: String, userId: String): List<String> {
        return repository.startGroupRun(groupId, userId)
    }
}

sealed class SocialUiState {
    object Loading : SocialUiState()
    data class Success(
        val friends: List<UserResponse>,
        val groups: List<GroupDetailedResponse>,
        val friendRequests: List<FriendRequestResponse>,
        val groupInvites: List<GroupInviteResponse>
    ) : SocialUiState()
    data class Error(val message: String) : SocialUiState()
}
