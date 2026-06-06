package com.example.myapplication.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("runs/start")
    suspend fun startRun(@Query("user_id") userId: String): RunStartResponse

    @POST("runs/{runId}/route")
    suspend fun sendLocation(@Path("runId") runId: String, @Query("lat") lat: Double, @Query("lng") lng: Double)

    @POST("runs/{runId}/end")
    suspend fun endRun(@Path("runId") runId: String, @Query("distance") distance: Double)

    // --- GESTIONE GRUPPI ---

    @POST("groups")
    suspend fun createGroup(
        @Query("name") name: String,
        @Query("creator_id") creatorId: String
    ): GroupResponse

    @GET("groups")
    suspend fun getGroups(): List<GroupDetailedResponse>

    @POST("groups/{groupId}/start-run")
    suspend fun startGroupRun(
        @Path("groupId") groupId: String,
        @Query("organizer_id") organizerId: String
    ): GroupStartRunResponse

    @PUT("groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): SimpleStatusResponse

    @DELETE("groups/{groupId}")
    suspend fun deleteGroup(
        @Path("groupId") groupId: String
    ): SimpleStatusResponse

    @POST("groups/{groupId}/invites")
    suspend fun inviteToGroup(
        @Path("groupId") groupId: String,
        @Query("from_user_id") fromUserId: String,
        @Query("to_user_id") toUserId: String
    ): GroupInviteActionResponse

    @GET("groups/invites/pending")
    suspend fun getPendingGroupInvites(
        @Query("user_id") userId: String
    ): List<GroupInviteResponse>

    @POST("group-invites/respond")
    suspend fun respondInvite(
        @Query("invite_id") inviteId: String,
        @Query("user_id") userId: String,
        @Query("status") status: String
    ): GroupInviteActionResponse

    @POST("groups/{groupId}/members/{userId}")
    suspend fun addGroupMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): SimpleStatusResponse

    @DELETE("groups/{groupId}/members/{userId}")
    suspend fun removeGroupMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): SimpleStatusResponse

    // --- GESTIONE UTENTI E AMICI ---

    @POST("users/sync")
    suspend fun syncUser(
        @Header("Authorization") token: String, 
        @Body userData: SyncUserPayload
    ): UserResponse

    @GET("users/search")
    suspend fun searchUsers(@Query("query") query: String): List<UserResponse>

    @POST("friends/request")
    suspend fun sendFriendRequest(
        @Query("from_user_id") fromUserId: String,
        @Query("to_user_email") toUserEmail: String
    ): FriendRequestActionResponse

    @GET("friends/pending")
    suspend fun getPendingRequests(@Query("user_id") userId: String): List<FriendRequestResponse>

    @POST("friends/respond")
    suspend fun respondToFriendRequest(@Query("request_id") requestId: String, @Query("status") status: String)

    @GET("friends/list")
    suspend fun getFriends(@Query("user_id") userId: String): List<UserResponse>
}

data class SyncUserPayload(
    val name: String? = null,
    val surname: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val email: String? = null,
    val weight: Double? = null
)

data class UserResponse(
    @SerializedName("id") val idInt: Int? = null,
    @SerializedName("firebase_uid") val firebaseUid: String = "",
    val name: String? = null,
    val surname: String? = null,
    val email: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val weight: Double? = null,
    val provider: String? = null
) {
    val id: String get() = firebaseUid
}

data class RunStartResponse(val run_id: String, val status: String)
data class SimpleStatusResponse(val status: String)
data class GroupResponse(@SerializedName("group_id") val id: String)
data class GroupStartRunResponse(val group_id: String, val group_name: String, val members: List<String>)

data class GroupDetailedResponse(
    val id: String?,
    @SerializedName("group_id") val groupId: String?,
    val name: String,
    @SerializedName("creator_id") val creatorId: String? = null,
    @SerializedName("members_ids") val membersIds: List<String>?,
    @SerializedName("members_names") val membersNames: Map<String, String>? = null
) {
    val realId: String get() = id ?: groupId ?: ""
}

data class GroupInviteResponse(val id: String, val group_name: String?, @SerializedName("invited_by") val invitedBy: UserResponse?)
data class GroupInviteActionResponse(val status: String, val invite_id: String?)
data class FriendRequestResponse(val id: String, @SerializedName("from_user") val fromUser: UserResponse, val status: String)
data class FriendRequestActionResponse(val status: String, @SerializedName("request_id") val requestId: String, @SerializedName("to_user_id") val toUserId: String? = null)
