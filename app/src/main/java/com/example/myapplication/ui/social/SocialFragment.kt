package com.example.myapplication.ui.social

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupDetailedResponse
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.UserResponse
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocialFragment : Fragment() {

    private lateinit var btnCreateGroup: MaterialButton
    private lateinit var btnAddFriend: MaterialButton
    private lateinit var btnStartGroupRun: MaterialButton
    private lateinit var rvFriends: RecyclerView
    private lateinit var rvFriendRequests: RecyclerView
    private lateinit var rvGroupInvites: RecyclerView
    private lateinit var rvGroups: RecyclerView
    private lateinit var rvActiveRunInvites: RecyclerView
    private lateinit var tvRequestsHeader: TextView
    private lateinit var tvGroupInvitesHeader: TextView
    private lateinit var tvActiveRunInvitesHeader: TextView
    
    private var friendsList: List<UserResponse> = emptyList()
    private var myGroupsList: List<GroupDetailedResponse> = emptyList()
    private var allUsersMap: Map<String, String> = emptyMap()
    
    private lateinit var database: DatabaseReference
    private var runInvitesRef: DatabaseReference? = null
    private var runInvitesListener: ValueEventListener? = null
    private lateinit var runInviteAdapter: RunInviteAdapter

    private val DB_URL = "https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_social, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = FirebaseDatabase.getInstance(DB_URL).reference
        initViews(view)
        loadData()
        listenForRunInvites()

        btnAddFriend.setOnClickListener { showAddFriendDialog() }
        btnCreateGroup.setOnClickListener { showCreateGroupDialog() }
        btnStartGroupRun.setOnClickListener { showSelectGroupForRunDialog() }
    }

    private fun initViews(view: View) {
        btnCreateGroup = view.findViewById(R.id.btnCreateGroup)
        btnAddFriend = view.findViewById(R.id.btnAddFriend)
        btnStartGroupRun = view.findViewById(R.id.btnStartGroupRun)
        rvFriends = view.findViewById(R.id.rvFriends)
        rvFriends.layoutManager = LinearLayoutManager(context)
        rvFriendRequests = view.findViewById(R.id.rvFriendRequests)
        rvFriendRequests.layoutManager = LinearLayoutManager(context)
        rvGroupInvites = view.findViewById(R.id.rvGroupInvites)
        rvGroupInvites.layoutManager = LinearLayoutManager(context)
        rvGroups = view.findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(context)
        rvActiveRunInvites = view.findViewById(R.id.rvActiveRunInvites)
        rvActiveRunInvites.layoutManager = LinearLayoutManager(context)
        runInviteAdapter = RunInviteAdapter(emptyList()) { invite, status -> respondToRunInvite(invite, status) }
        rvActiveRunInvites.adapter = runInviteAdapter
        tvRequestsHeader = view.findViewById(R.id.tvRequestsHeader)
        tvGroupInvitesHeader = view.findViewById(R.id.tvGroupInvitesHeader)
        tvActiveRunInvitesHeader = view.findViewById(R.id.tvActiveRunInvitesHeader)
    }

    private fun listenForRunInvites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = database.child("run_invites").child(userId)
        runInvitesRef = ref
        runInvitesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invites = mutableListOf<RunInvite>()
                for (child in snapshot.children) {
                    val gId = child.key ?: continue
                    val gName = child.child("groupName").value?.toString() ?: "Gruppo Ignoto"
                    invites.add(RunInvite(gId, gName))
                }
                activity?.runOnUiThread {
                    if (invites.isNotEmpty()) {
                        tvActiveRunInvitesHeader.visibility = View.VISIBLE
                        rvActiveRunInvites.visibility = View.VISIBLE
                        runInviteAdapter.updateData(invites)
                    } else {
                        tvActiveRunInvitesHeader.visibility = View.GONE
                        rvActiveRunInvites.visibility = View.GONE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(runInvitesListener!!)
    }

    private fun respondToRunInvite(invite: RunInvite, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child("run_invites").child(userId).child(invite.groupId).removeValue()
        if (status == "accepted") {
            database.child("lobbies").child(invite.groupId).child(userId).setValue("ready")
            navigateToLobbyById(invite.groupId, invite.groupName)
        } else {
            database.child("lobbies").child(invite.groupId).child(userId).setValue("rejected")
        }
    }

    private fun navigateToLobbyById(gId: String, gName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groups = RetrofitClient.api.getGroups()
                val group = groups.find { it.id == gId }
                withContext(Dispatchers.Main) {
                    val lobbyFragment = GroupLobbyFragment.newInstance(gId, gName, group?.membersIds ?: emptyList(), allUsersMap)
                    parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, lobbyFragment).addToBackStack(null).commit()
                }
            } catch (e: Exception) {}
        }
    }

    private fun sendGroupRunRequests(group: GroupDetailedResponse) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Reset totale della lobby per evitare avvii automatici
        database.child("lobbies").child(group.id).removeValue().addOnCompleteListener {
            database.child("lobbies").child(group.id).child("status").setValue("waiting")
            database.child("lobbies").child(group.id).child("organizer").setValue(userId)
            database.child("lobbies").child(group.id).child(userId).setValue("ready")

            group.membersIds?.forEach { memberId ->
                if (memberId != userId) {
                    val inviteData = mapOf("groupName" to group.name, "from" to userId)
                    database.child("run_invites").child(memberId).child(group.id).setValue(inviteData)
                }
            }
            navigateToLobby(group)
        }
    }

    private fun navigateToLobby(group: GroupDetailedResponse) {
        val lobbyFragment = GroupLobbyFragment.newInstance(group.id, group.name, group.membersIds ?: emptyList(), allUsersMap)
        parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, lobbyFragment).addToBackStack(null).commit()
    }

    private fun loadData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                friendsList = RetrofitClient.api.getFriends(userId)
                val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
                val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: currentUserEmail
                val tempMap = mutableMapOf<String, String>()
                tempMap[userId] = currentUserName
                friendsList.forEach { tempMap[it.id] = it.display_name ?: it.email }
                allUsersMap = tempMap
                val friendRequests = try { RetrofitClient.api.getPendingRequests(userId) } catch (e: Exception) { emptyList() }
                val groupInvites = try { RetrofitClient.api.getPendingGroupInvites(userId) } catch (e: Exception) { emptyList() }
                val allGroups = try { RetrofitClient.api.getGroups() } catch (e: Exception) { emptyList() }
                myGroupsList = allGroups.filter { it.membersIds?.contains(userId) == true }
                withContext(Dispatchers.Main) {
                    rvFriends.adapter = FriendsAdapter(friendsList)
                    rvGroups.adapter = GroupsAdapter(myGroupsList, allUsersMap) { }
                    if (friendRequests.isNotEmpty()) {
                        tvRequestsHeader.visibility = View.VISIBLE
                        rvFriendRequests.visibility = View.VISIBLE
                        rvFriendRequests.adapter = FriendRequestsAdapter(friendRequests) { id, s -> respondToFriendRequest(id, s) }
                    } else {
                        tvRequestsHeader.visibility = View.GONE
                        rvFriendRequests.visibility = View.GONE
                    }
                    if (groupInvites.isNotEmpty()) {
                        tvGroupInvitesHeader.visibility = View.VISIBLE
                        rvGroupInvites.visibility = View.VISIBLE
                        rvGroupInvites.adapter = GroupInvitesAdapter(groupInvites) { id, s -> respondToGroupInvite(id, s) }
                    } else {
                        tvGroupInvitesHeader.visibility = View.GONE
                        rvGroupInvites.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun showSelectGroupForRunDialog() {
        if (myGroupsList.isEmpty()) { Toast.makeText(context, "Non sei in nessun gruppo", Toast.LENGTH_SHORT).show(); return }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_group, null)
        val rvSelect = dialogView.findViewById<RecyclerView>(R.id.rvSelectGroup)
        rvSelect.layoutManager = LinearLayoutManager(context)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).setNegativeButton("Annulla", null).create()
        rvSelect.adapter = GroupsAdapter(myGroupsList, allUsersMap) { group -> dialog.dismiss(); startGroupRunProcess(group) }
        dialog.show()
    }

    private fun startGroupRunProcess(group: GroupDetailedResponse) {
        AlertDialog.Builder(requireContext()).setTitle("Corsa di Gruppo: ${group.name}").setMessage("Invia una richiesta ai membri per iniziare la corsa.").setPositiveButton("Send Request") { _, _ -> sendGroupRunRequests(group) }.setNegativeButton("Annulla", null).show()
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<EditText>(R.id.etGroupName)
        val rvInvite = dialogView.findViewById<RecyclerView>(R.id.rvInviteFriends)
        rvInvite.layoutManager = LinearLayoutManager(context)
        val adapter = InviteFriendsAdapter(friendsList)
        rvInvite.adapter = adapter
        AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("Crea") { _, _ ->
            val groupName = etGroupName.text.toString()
            val selectedFriends = adapter.getSelectedFriends()
            if (groupName.isNotEmpty()) createGroup(groupName, selectedFriends)
            else Toast.makeText(context, "Inserisci un nome per il gruppo", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Annulla", null).show()
    }

    private fun createGroup(name: String, friendIds: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupResponse = RetrofitClient.api.createGroup(name, userId)
                val groupId = groupResponse.id
                friendIds.forEach { RetrofitClient.api.inviteToGroup(groupId, userId, it) }
                withContext(Dispatchers.Main) { loadData() }
            } catch (e: Exception) {}
        }
    }

    private fun respondToFriendRequest(requestId: String, status: String) {
        CoroutineScope(Dispatchers.IO).launch { try { RetrofitClient.api.respondToFriendRequest(requestId, status); withContext(Dispatchers.Main) { loadData() } } catch (e: Exception) {} }
    }

    private fun respondToGroupInvite(inviteId: String, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch { try { RetrofitClient.api.respondInvite(inviteId, userId, status); withContext(Dispatchers.Main) { loadData() } } catch (e: Exception) {} }
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_friend, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etFriendEmail)
        AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("Invia") { _, _ ->
            val email = etEmail.text.toString()
            if (email.isNotEmpty()) sendFriendRequest(email)
        }.setNegativeButton("Annulla", null).show()
    }

    private fun sendFriendRequest(email: String) {
        val fromUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch { try { RetrofitClient.api.sendFriendRequest(fromUserId, email); withContext(Dispatchers.Main) { Toast.makeText(context, "Richiesta inviata!", Toast.LENGTH_SHORT).show() } } catch (e: Exception) {} }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        runInvitesListener?.let { runInvitesRef?.removeEventListener(it) }
    }
}
