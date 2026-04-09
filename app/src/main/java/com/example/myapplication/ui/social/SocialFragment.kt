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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import retrofit2.HttpException

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
    
    // Riferimenti ai listener per poterli rimuovere correttamente
    private var syncListener: ValueEventListener? = null
    private var notificationListener: ChildEventListener? = null
    private var runInvitesListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_social, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Inizializzazione Database con URL esplicito per evitare problemi di regione
        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        initViews(view)
        loadData()
        startRealtimeListeners()
    }

    private fun startRealtimeListeners() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG, "Starting real-time listeners for user: $userId")

        // 1. STESSO PRINCIPIO DI RUN INVITES: Listener per aggiornamento lista amicizie
        syncListener = database.child("friend_requests_ping").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isAdded) {
                        Log.d(TAG, "Friendship ping detected in Firebase! Reloading data...")
                        loadData()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Sync listener error: ${error.message}")
                }
            })

        // 2. Listener per la notifica visiva (Snackbar)
        val fragmentOpenTime = System.currentTimeMillis()
        notificationListener = database.child("friend_notifications").child(userId)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    // Mostriamo la Snackbar solo se la notifica è recente (creata dopo l'apertura della pagina)
                    if (isAdded && timestamp > (fragmentOpenTime - 10000)) {
                        val fromName = snapshot.child("fromName").getValue(String::class.java) ?: "Someone"
                        showFeedback("$fromName sent you a friend request!")
                    }
                    // Rimuoviamo la notifica consumata per non rileggerla al prossimo avvio
                    snapshot.ref.removeValue()
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })

        // 3. Listener per gli inviti alle corse (già funzionante)
        runInvitesListener = database.child("run_invites").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val invites = mutableListOf<RunInvite>()
                    for (child in snapshot.children) {
                        val members = mutableListOf<String>()
                        child.child("members").children.forEach { members.add(it.value.toString()) }
                        invites.add(RunInvite(child.key!!, child.child("groupName").value?.toString() ?: "Run", members))
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
            syncListener?.let { database.child("friend_requests_ping").child(userId).removeEventListener(it) }
            notificationListener?.let { database.child("friend_notifications").child(userId).removeEventListener(it) }
            runInvitesListener?.let { database.child("run_invites").child(userId).removeEventListener(it) }
        }
    }

    private fun loadData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Carichiamo tutti i dati necessari dal backend
                val friends = RetrofitClient.api.getFriends(userId)
                val allGroups = RetrofitClient.api.getGroups()
                val friendRequests = try { RetrofitClient.api.getPendingRequests(userId) } catch (e: Exception) { emptyList() }
                val groupInvites = try { RetrofitClient.api.getPendingGroupInvites(userId) } catch (e: Exception) { emptyList() }

                val currentUser = FirebaseAuth.getInstance().currentUser
                val myNameForOthers = currentUser?.displayName ?: currentUser?.email ?: "Runner"
                
                val tempMap = mutableMapOf<String, String>()
                tempMap[userId] = myNameForOthers
                friends.forEach { tempMap[it.id] = it.display_name ?: it.email }

                // Arricchiamo la mappa dei nomi con i nomi provenienti dai gruppi (per vedere chi non è amico)
                allGroups.forEach { group ->
                    group.membersNames?.forEach { (mId, mName) ->
                        if (!tempMap.containsKey(mId)) {
                            tempMap[mId] = mName
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    allUsersMap = tempMap
                    friendsList = friends
                    myGroupsList = allGroups.filter { it.membersIds?.contains(userId) == true }

                    // Aggiornamento UI
                    rvFriends.adapter = FriendsAdapter(friendsList)
                    rvGroups.adapter = GroupsAdapter(
                        myGroupsList,
                        allUsersMap,
                        userId,
                        onGroupClick = { group -> showGroupDetailsDialog(group) },
                        onEditClick = { group -> showEditGroupDialog(group) },
                        onDeleteClick = { group -> confirmDeleteGroup(group) }
                    )

                    tvRequestsHeader.visibility = if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE
                    rvFriendRequests.visibility = if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE
                    if (friendRequests.isNotEmpty()) {
                        rvFriendRequests.adapter = FriendRequestsAdapter(friendRequests) { id, s, fromId -> respondToFriendRequest(id, s, fromId) }
                    }

                    tvGroupInvitesHeader.visibility = if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE
                    rvGroupInvites.visibility = if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE
                    if (groupInvites.isNotEmpty()) {
                        rvGroupInvites.adapter = GroupInvitesAdapter(groupInvites) { id, s -> respondToGroupInvite(id, s) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Data loading error: ${e.message}")
            }
        }
    }

    private fun sendFriendRequest(email: String) {
        if (isSendingRequest) return
        isSendingRequest = true

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: FirebaseAuth.getInstance().currentUser?.email ?: "A user"

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Chiamata API al backend
                val response = RetrofitClient.api.sendFriendRequest(currentUserId, email)
                val targetUserId = response.toUserId

                if (targetUserId != null) {
                    Log.d(TAG, "Sending real-time trigger to recipient: $targetUserId")
                    
                    // 2. Attiva il ping in tempo reale (come per gli inviti alle corse)
                    database.child("friend_requests_ping").child(targetUserId).setValue(ServerValue.TIMESTAMP)
                    
                    // 3. Invia la notifica per la Snackbar
                    database.child("friend_notifications").child(targetUserId).push().setValue(
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
                Log.e(TAG, "Error sending request: ${e.message}")
                withContext(Dispatchers.Main) {
                    isSendingRequest = false
                    val errorMsg = if (e is HttpException && e.code() == 400) {
                        "Request already pending or you are already friends"
                    } else if (e is HttpException && e.code() == 404) {
                        "User not found"
                    } else {
                        "Unable to send request"
                    }
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

        btnStartGroupRun.setOnClickListener { showSelectGroupForRunDialog() }
        btnCreateGroup.setOnClickListener { showCreateGroupDialog() }
        btnAddFriend.setOnClickListener { showAddFriendDialog() }
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        val view = view ?: return
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).apply {
            setBackgroundTint(if (isError) Color.parseColor("#E53935") else Color.parseColor("#00BFFF"))
            setTextColor(Color.WHITE)
            val snackbarView = this.view
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(24, 24, 24, 24)
            snackbarView.layoutParams = params
            snackbarView.background = context.getDrawable(R.drawable.shape_rounded_snackbar)
            show()
        }
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_friend, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etFriendEmail)

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val email = etEmail.text.toString().trim()
                if (email.isNotBlank()) sendFriendRequest(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateInvitesUI(invites: List<RunInvite>) {
        tvActiveRunInvitesHeader.visibility = if (invites.isNotEmpty()) View.VISIBLE else View.GONE
        rvActiveRunInvites.visibility = if (invites.isNotEmpty()) View.VISIBLE else View.GONE
        if (invites.isNotEmpty()) {
            rvActiveRunInvites.adapter = RunInviteAdapter(invites) { invite, action ->
                if (action == "accepted") {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@RunInviteAdapter
                    val userName = allUsersMap[userId] ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Runner"
                    val lRef = database.child("lobbies").child(invite.groupId)
                    lRef.child("names").child(userId).setValue(userName)
                    lRef.child(userId).setValue("ready")
                    navigateToLobby(invite.groupId, invite.groupName, invite.members)
                }
                database.child("run_invites").child(FirebaseAuth.getInstance().currentUser!!.uid).child(invite.groupId).removeValue()
            }
        }
    }

    private fun navigateToLobby(id: String, name: String, members: List<String>) {
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.navigateToFragment(GroupLobbyFragment.newInstance(id, name, members, allUsersMap), "LOBBY_$id", "Lobby $name")
    }

    private fun showGroupDetailsDialog(group: GroupDetailedResponse) {
        val members = group.membersIds?.map { mId -> 
            group.membersNames?.get(mId) ?: allUsersMap[mId] ?: "User ($mId)" 
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
            if (mId != currentUserId && allManageableUsers.none { it.id == mId }) {
                val name = group.membersNames?.get(mId) ?: allUsersMap[mId] ?: "Participant"
                allManageableUsers.add(UserResponse(mId, "", name, null))
            }
        }

        val adapter = InviteFriendsAdapter(allManageableUsers, currentMemberIds)
        rvInvite.adapter = adapter

        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etGroupName.text.toString()
                if (newName.isNotBlank()) updateGroup(group.realId, newName, adapter.getSelectedFriends(), currentMemberIds)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGroup(groupId: String, name: String, newMemberIds: List<String>, oldMemberIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.updateGroup(groupId, mapOf("name" to name, "members_ids" to newMemberIds))
                val allInvolved = (newMemberIds + oldMemberIds).distinct()
                notifyUpdate(allInvolved)
                withContext(Dispatchers.Main) {
                    showFeedback("Changes saved!")
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showFeedback("Error saving changes", true) }
            }
        }
    }

    private fun notifyUpdate(userIds: List<String>) {
        val timestamp = ServerValue.TIMESTAMP
        userIds.forEach { id ->
            database.child("friend_requests_ping").child(id).setValue(timestamp)
        }
    }

    private fun confirmDeleteGroup(group: GroupDetailedResponse) {
        MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setTitle("Delete Group")
            .setMessage("Do you want to delete '${group.name}'?")
            .setPositiveButton("Delete") { _, _ -> 
                val members = group.membersIds ?: emptyList()
                deleteGroup(group.realId, group.name, members) 
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
                    showFeedback("Group '$groupName' deleted")
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showFeedback("Error deleting group", true) }
            }
        }
    }

    private fun showSelectGroupForRunDialog() {
        if (myGroupsList.isEmpty()) { showFeedback("No groups available", true); return }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_group, null)
        val rvSelect = dialogView.findViewById<RecyclerView>(R.id.rvSelectGroup)
        rvSelect.layoutManager = LinearLayoutManager(context)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
            .setTitle("Select Group")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()
        rvSelect.adapter = GroupsAdapter(myGroupsList, allUsersMap, FirebaseAuth.getInstance().currentUser?.uid, onGroupClick = { group -> dialog.dismiss(); startGroupRunFlow(group) })
        dialog.show()
    }

    private fun startGroupRunFlow(group: GroupDetailedResponse) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val userName = allUsersMap[userId] ?: user.displayName ?: user.email ?: "Runner"
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = RetrofitClient.api.startGroupRun(group.realId, userId)
                withContext(Dispatchers.Main) {
                    val lobbyRef = database.child("lobbies").child(group.realId)
                    lobbyRef.removeValue().addOnCompleteListener {
                        lobbyRef.setValue(mapOf("status" to "waiting", "organizer" to userId))
                        lobbyRef.child("names").child(userId).setValue(userName)
                        lobbyRef.child(userId).setValue("ready")
                        res.members.forEach { mId -> 
                            if (mId != userId) {
                                database.child("run_invites").child(mId).child(group.realId).setValue(
                                    mapOf(
                                        "groupName" to group.name, 
                                        "from" to userId,
                                        "members" to res.members
                                    )
                                )
                            }
                        }
                        navigateToLobby(group.realId, group.name, res.members)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
        }
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvCreateGroupTitle)
        val etGroupName = dialogView.findViewById<EditText>(R.id.etGroupName)
        val rvInvite = dialogView.findViewById<RecyclerView>(R.id.rvInviteFriends)
        
        tvTitle.text = "New Group"
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
                val finalMembers = friendIds.toMutableList().apply { if (userId !in this) add(userId) }
                RetrofitClient.api.updateGroup(res.id, mapOf("members_ids" to finalMembers))
                notifyUpdate(finalMembers)
                withContext(Dispatchers.Main) { 
                    showFeedback("Group created!")
                    loadData() 
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { showFeedback("Error creating group", true) } }
        }
    }

    private fun respondToFriendRequest(id: String, status: String, fromUserId: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.api.respondToFriendRequest(id, status)
                // Ping del mittente in modo che la sua lista si aggiorni in tempo reale
                database.child("friend_requests_ping").child(fromUserId).setValue(ServerValue.TIMESTAMP)
                
                withContext(Dispatchers.Main) {
                    showFeedback(if (status == "accepted") "Request accepted!" else "Request rejected")
                    loadData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error responding to request: ${e.message}")
            }
        }
    }

    private fun respondToGroupInvite(id: String, s: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { try { RetrofitClient.api.respondInvite(id, userId, s); withContext(Dispatchers.Main) { loadData() } } catch (e: Exception) {} }
    }
}
