package com.example.myapplication.ui.social

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupDetailedResponse
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.UserResponse
import com.example.myapplication.ui.home.MainActivity
import com.example.myapplication.utils.NetworkMonitor
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID

class SocialFragment : Fragment() {

    private lateinit var rvFriends: RecyclerView
    private lateinit var rvFriendRequests: RecyclerView
    private lateinit var rvGroupInvites: RecyclerView
    private lateinit var rvGroups: RecyclerView
    private lateinit var rvActiveRunInvites: RecyclerView

    private lateinit var tvRequestsHeader: TextView
    private lateinit var tvGroupInvitesHeader: TextView
    private lateinit var tvActiveRunInvitesHeader: TextView
    private lateinit var btnStartGroupRun: MaterialButton
    private lateinit var btnCreateGroup: MaterialButton
    private lateinit var btnAddFriend: MaterialButton

    private var myGroupsList: List<GroupDetailedResponse> = emptyList()
    private var friendsList: List<UserResponse> = emptyList()
    private var allUsersMap: Map<String, String> = emptyMap()

    private lateinit var database: DatabaseReference
    private val TAG = "SocialFragment"

    private var isSendingRequest = false
    private var isErrorState = false

    private var syncListener: ValueEventListener? = null
    private var notificationListener: ChildEventListener? = null
    private var runInvitesListener: ValueEventListener? = null
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_social, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database =
            FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference

        networkMonitor = NetworkMonitor(requireContext())

        initViews(view)
        loadData()
        startRealtimeListeners()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewLifecycleOwner.lifecycleScope.launch {
            networkMonitor.isConnected.collectLatest { isConnected ->
                if (isConnected && isErrorState) {
                    loadData()
                }
            }
        }
    }

    private fun startRealtimeListeners() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        syncListener = database.child("friend_requests_ping")
            .child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isAdded) loadData()
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        val fragmentOpenTime = System.currentTimeMillis()

        notificationListener = database.child("friend_notifications")
            .child(userId)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    if (isAdded && timestamp > (fragmentOpenTime - 10000)) {
                        val fromName = snapshot.child("fromName").getValue(String::class.java)
                            ?: "Someone"

                        showFeedback("$fromName sent you a friend request!")
                    }

                    snapshot.ref.removeValue()
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })

        runInvitesListener = database.child("run_invites")
            .child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val invites = mutableListOf<RunInvite>()

                    for (child in snapshot.children) {
                        val members = mutableListOf<String>()

                        child.child("members").children.forEach {
                            members.add(it.value.toString())
                        }

                        val sessionId = child.child("sessionId").value?.toString() ?: ""

                        invites.add(
                            RunInvite(
                                groupId = child.key ?: "",
                                groupName = child.child("groupName").value?.toString() ?: "Run",
                                members = members,
                                sessionId = sessionId
                            )
                        )
                    }

                    updateInvitesUI(invites)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            syncListener?.let {
                database.child("friend_requests_ping")
                    .child(userId)
                    .removeEventListener(it)
            }

            notificationListener?.let {
                database.child("friend_notifications")
                    .child(userId)
                    .removeEventListener(it)
            }

            runInvitesListener?.let {
                database.child("run_invites")
                    .child(userId)
                    .removeEventListener(it)
            }
        }
    }

    private fun loadData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val friends = RetrofitClient.api.getFriends(userId)
                val allGroups = RetrofitClient.api.getGroups()

                val friendRequests = try {
                    RetrofitClient.api.getPendingRequests(userId)
                } catch (e: Exception) {
                    emptyList()
                }

                val groupInvites = try {
                    RetrofitClient.api.getPendingGroupInvites(userId)
                } catch (e: Exception) {
                    emptyList()
                }

                val currentUser = FirebaseAuth.getInstance().currentUser
                val myNameForOthers = currentUser?.displayName ?: currentUser?.email ?: "Runner"

                val tempMap = mutableMapOf<String, String>()
                tempMap[userId] = myNameForOthers

                friends.forEach {
                    tempMap[it.firebaseUid] = it.displayName ?: it.email ?: "Friend"
                }

                allGroups.forEach { group ->
                    group.membersNames?.forEach { (mId, mName) ->
                        if (!tempMap.containsKey(mId)) {
                            tempMap[mId] = mName
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    isErrorState = false
                    allUsersMap = tempMap
                    friendsList = friends
                    myGroupsList = allGroups.filter { it.membersIds?.contains(userId) == true }

                    rvFriends.adapter = FriendsAdapter(friendsList)

                    rvGroups.adapter = GroupsAdapter(
                        groups = myGroupsList,
                        userNamesMap = allUsersMap,
                        currentUserId = userId,
                        showActions = true,
                        onGroupClick = { group -> showGroupDetailsDialog(group) },
                        onEditClick = { group -> showEditGroupDialog(group) },
                        onDeleteClick = { group -> confirmDeleteGroup(group) }
                    )

                    tvRequestsHeader.visibility =
                        if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE

                    rvFriendRequests.visibility =
                        if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE

                    if (friendRequests.isNotEmpty()) {
                        rvFriendRequests.adapter =
                            FriendRequestsAdapter(friendRequests) { id, status, fromId ->
                                respondToFriendRequest(id, status, fromId)
                            }
                    }

                    tvGroupInvitesHeader.visibility =
                        if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE

                    rvGroupInvites.visibility =
                        if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE

                    if (groupInvites.isNotEmpty()) {
                        rvGroupInvites.adapter =
                            GroupInvitesAdapter(groupInvites) { id, status ->
                                respondToGroupInvite(id, status)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Data loading error: ${e.message}", e)

                withContext(Dispatchers.Main) {
                    isErrorState = true
                }
            }
        }
    }

    private fun sendFriendRequest(email: String) {
        if (isSendingRequest) return

        isSendingRequest = true

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentUserName =
            FirebaseAuth.getInstance().currentUser?.displayName
                ?: FirebaseAuth.getInstance().currentUser?.email
                ?: "A user"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.sendFriendRequest(currentUserId, email)
                val targetUserId = response.toUserId

                if (targetUserId != null) {
                    database.child("friend_requests_ping")
                        .child(targetUserId)
                        .setValue(ServerValue.TIMESTAMP)

                    database.child("friend_notifications")
                        .child(targetUserId)
                        .push()
                        .setValue(
                            mapOf(
                                "fromName" to currentUserName,
                                "fromId" to currentUserId,
                                "timestamp" to ServerValue.TIMESTAMP
                            )
                        )
                }

                withContext(Dispatchers.Main) {
                    showFeedback("Request sent to $email")
                    loadData()
                    isSendingRequest = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Friend request error", e)

                val errorMsg = if (e is HttpException) {
                    val body = try {
                        e.response()?.errorBody()?.string()
                    } catch (_: Exception) {
                        null
                    }

                    Log.e(TAG, "Friend request HTTP ${e.code()}, body=$body")

                    when (e.code()) {
                        400 -> "Request already pending or invalid request"
                        404 -> "User not found"
                        401 -> "Authentication error"
                        500 -> "Server error"
                        503 -> "Backend unavailable"
                        else -> "Error ${e.code()}: ${body ?: e.message()}"
                    }
                } else {
                    "Network error: ${e.localizedMessage}"
                }

                withContext(Dispatchers.Main) {
                    isSendingRequest = false
                    showFeedback(errorMsg, true)
                }
            }
        }
    }

    private fun initViews(view: View) {
        rvFriends = view.findViewById(R.id.rvFriends)
        rvFriendRequests = view.findViewById(R.id.rvFriendRequests)
        rvGroupInvites = view.findViewById(R.id.rvGroupInvites)
        rvGroups = view.findViewById(R.id.rvGroups)
        rvActiveRunInvites = view.findViewById(R.id.rvActiveRunInvites)

        tvRequestsHeader = view.findViewById(R.id.tvRequestsHeader)
        tvGroupInvitesHeader = view.findViewById(R.id.tvGroupInvitesHeader)
        tvActiveRunInvitesHeader = view.findViewById(R.id.tvActiveRunInvitesHeader)

        btnStartGroupRun = view.findViewById(R.id.btnStartGroupRun)
        btnCreateGroup = view.findViewById(R.id.btnCreateGroup)
        btnAddFriend = view.findViewById(R.id.btnAddFriend)

        rvFriends.layoutManager = LinearLayoutManager(context)
        rvFriendRequests.layoutManager = LinearLayoutManager(context)
        rvGroupInvites.layoutManager = LinearLayoutManager(context)
        rvGroups.layoutManager = LinearLayoutManager(context)
        rvActiveRunInvites.layoutManager = LinearLayoutManager(context)

        btnStartGroupRun.setOnClickListener {
            showSelectGroupForRunDialog()
        }

        btnCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }

        btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        val currentView = view ?: return

        Snackbar.make(currentView, message, Snackbar.LENGTH_LONG).apply {
            setBackgroundTint(
                if (isError) Color.parseColor("#E53935") else Color.parseColor("#00BFFF")
            )
            setTextColor(Color.WHITE)
            show()
        }
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_friend, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etFriendEmail)

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val email = etEmail.text.toString().trim().lowercase()
                if (email.isNotBlank()) sendFriendRequest(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateInvitesUI(invites: List<RunInvite>) {
        tvActiveRunInvitesHeader.visibility =
            if (invites.isNotEmpty()) View.VISIBLE else View.GONE

        rvActiveRunInvites.visibility =
            if (invites.isNotEmpty()) View.VISIBLE else View.GONE

        if (invites.isNotEmpty()) {
            rvActiveRunInvites.adapter = RunInviteAdapter(invites) { invite, action ->
                if (action == "accepted") {
                    val userId =
                        FirebaseAuth.getInstance().currentUser?.uid ?: return@RunInviteAdapter

                    val userName =
                        allUsersMap[userId]
                            ?: FirebaseAuth.getInstance().currentUser?.displayName
                            ?: "Runner"

                    val lRef = database.child("lobbies").child(invite.groupId)

                    lRef.child("names").child(userId).setValue(userName)
                    lRef.child(userId).setValue("ready")

                    navigateToLobby(
                        id = invite.groupId,
                        name = invite.groupName,
                        members = invite.members,
                        sessionId = invite.sessionId
                    )
                }

                database.child("run_invites")
                    .child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .child(invite.groupId)
                    .removeValue()
            }
        }
    }

    private fun navigateToLobby(
        id: String,
        name: String,
        members: List<String>,
        sessionId: String
    ) {
        val mainActivity = activity as? MainActivity ?: return

        mainActivity.navigateToFragment(
            GroupLobbyFragment.newInstance(
                groupId = id,
                groupName = name,
                members = members,
                namesMap = allUsersMap,
                sessionId = sessionId
            ),
            "LOBBY_${id}_$sessionId",
            "Lobby $name"
        )
    }

    private fun showGroupDetailsDialog(group: GroupDetailedResponse) {
        val members = group.membersIds?.map { mId ->
            group.membersNames?.get(mId) ?: allUsersMap[mId] ?: "User"
        } ?: emptyList()

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setTitle(group.name)
            .setMessage("Members:\n\n" + members.joinToString("\n"))
            .setPositiveButton("Ok", null)
            .show()
    }

    private fun showEditGroupDialog(group: GroupDetailedResponse) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvCreateGroupTitle)
        val etGroupName = dialogView.findViewById<EditText>(R.id.etGroupName)
        val rvInvite = dialogView.findViewById<RecyclerView>(R.id.rvInviteFriends)

        tvTitle.text = "Edit Group"
        etGroupName.setText(group.name)

        rvInvite.layoutManager = LinearLayoutManager(context)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val currentMemberIds = group.membersIds ?: emptyList()
        val allManageableUsers = friendsList.toMutableList()

        currentMemberIds.forEach { mId ->
            if (mId != currentUserId && allManageableUsers.none { it.firebaseUid == mId }) {
                val name = group.membersNames?.get(mId) ?: allUsersMap[mId] ?: "Participant"
                allManageableUsers.add(UserResponse(firebaseUid = mId, name = "", surname = name))
            }
        }

        val adapter = InviteFriendsAdapter(allManageableUsers, currentMemberIds)
        rvInvite.adapter = adapter

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etGroupName.text.toString()
                if (newName.isNotBlank()) {
                    updateGroup(
                        group.realId,
                        newName,
                        adapter.getSelectedFriends(),
                        currentMemberIds
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGroup(
        groupId: String,
        name: String,
        newMemberIds: List<String>,
        oldMemberIds: List<String>
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.updateGroup(
                    groupId,
                    mapOf(
                        "name" to name,
                        "members_ids" to newMemberIds
                    )
                )

                notifyUpdate((newMemberIds + oldMemberIds).distinct())

                withContext(Dispatchers.Main) {
                    showFeedback("Changes saved!")
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showFeedback("Error saving changes", true)
                }
            }
        }
    }

    private fun notifyUpdate(userIds: List<String>) {
        userIds.forEach { id ->
            database.child("friend_requests_ping")
                .child(id)
                .setValue(ServerValue.TIMESTAMP)
        }
    }

    private fun confirmDeleteGroup(group: GroupDetailedResponse) {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setTitle("Delete Group")
            .setMessage("Do you want to delete '${group.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteGroup(
                    group.realId,
                    group.name,
                    group.membersIds ?: emptyList()
                )
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteGroup(groupId: String, groupName: String, members: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.deleteGroup(groupId)
                notifyUpdate(members)

                withContext(Dispatchers.Main) {
                    showFeedback("Group deleted")
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showFeedback("Error deleting group", true)
                }
            }
        }
    }

    private fun showSelectGroupForRunDialog() {
        if (myGroupsList.isEmpty()) {
            showFeedback("No groups", true)
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_group, null)
        val rvSelect = dialogView.findViewById<RecyclerView>(R.id.rvSelectGroup)

        rvSelect.layoutManager = LinearLayoutManager(context)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setTitle("Select Group")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        rvSelect.adapter = GroupsAdapter(
            groups = myGroupsList,
            userNamesMap = allUsersMap,
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
            showActions = false,
            onGroupClick = { group ->
                dialog.dismiss()
                startGroupRunFlow(group)
            }
        )

        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun startGroupRunFlow(group: GroupDetailedResponse) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userName = allUsersMap[user.uid] ?: user.displayName ?: "Runner"
        val sessionId = UUID.randomUUID().toString()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = RetrofitClient.api.startGroupRun(group.realId, user.uid)

                withContext(Dispatchers.Main) {
                    val lobbyRef = database.child("lobbies").child(group.realId)

                    val initialLobbyData = mapOf(
                        "status" to "waiting",
                        "organizer" to user.uid,
                        "sessionId" to sessionId,
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    lobbyRef.setValue(initialLobbyData).addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            showFeedback("Error creating lobby", true)
                            return@addOnCompleteListener
                        }

                        lobbyRef.child("names").child(user.uid).setValue(userName)
                        lobbyRef.child(user.uid).setValue("ready")

                        res.members.forEach { memberId ->
                            if (memberId != user.uid) {
                                database.child("run_invites")
                                    .child(memberId)
                                    .child(group.realId)
                                    .setValue(
                                        mapOf(
                                            "groupName" to group.name,
                                            "from" to user.uid,
                                            "members" to res.members,
                                            "sessionId" to sessionId
                                        )
                                    )
                            }
                        }

                        navigateToLobby(
                            id = group.realId,
                            name = group.name,
                            members = res.members,
                            sessionId = sessionId
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting group run", e)

                withContext(Dispatchers.Main) {
                    showFeedback("Error starting group run", true)
                }
            }
        }
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<EditText>(R.id.etGroupName)
        val rvInvite = dialogView.findViewById<RecyclerView>(R.id.rvInviteFriends)

        rvInvite.layoutManager = LinearLayoutManager(context)

        val adapter = InviteFriendsAdapter(friendsList)
        rvInvite.adapter = adapter

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etGroupName.text.toString()
                if (name.isNotBlank()) createGroup(name, adapter.getSelectedFriends())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGroup(name: String, friendIds: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = RetrofitClient.api.createGroup(name, userId)

                val finalMembers = friendIds.toMutableList().apply {
                    if (userId !in this) add(userId)
                }

                RetrofitClient.api.updateGroup(
                    res.id,
                    mapOf("members_ids" to finalMembers)
                )

                notifyUpdate(finalMembers)

                withContext(Dispatchers.Main) {
                    showFeedback("Group created!")
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showFeedback("Error", true)
                }
            }
        }
    }

    private fun respondToFriendRequest(id: String, status: String, fromUserId: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.respondToFriendRequest(id, status)

                database.child("friend_requests_ping")
                    .child(fromUserId)
                    .setValue(ServerValue.TIMESTAMP)

                withContext(Dispatchers.Main) {
                    showFeedback("Done!")
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error responding to friend request", e)
            }
        }
    }

    private fun respondToGroupInvite(id: String, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.respondInvite(id, userId, status)

                withContext(Dispatchers.Main) {
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error responding to group invite", e)
            }
        }
    }
}