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
import com.example.myapplication.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var myGroupsList: List<GroupDetailedResponse> = emptyList()
    private var friendsList: List<UserResponse> = emptyList()
    private var allUsersMap: Map<String, String> = emptyMap()
    
    private lateinit var database: DatabaseReference
    private val TAG = "SocialFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_social, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        initViews(view)
        loadData()
        listenForRunInvites()
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

        rvFriends.layoutManager = LinearLayoutManager(context)
        rvFriendRequests.layoutManager = LinearLayoutManager(context)
        rvGroupInvites.layoutManager = LinearLayoutManager(context)
        rvGroups.layoutManager = LinearLayoutManager(context)
        rvActiveRunInvites.layoutManager = LinearLayoutManager(context)

        btnStartGroupRun.setOnClickListener { showSelectGroupForRunDialog() }
        btnCreateGroup.setOnClickListener { showCreateGroupDialog() }
    }

    private fun loadData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val friends = RetrofitClient.api.getFriends(userId)
                val allGroups = RetrofitClient.api.getGroups()
                val friendRequests = try { RetrofitClient.api.getPendingRequests(userId) } catch (e: Exception) { emptyList() }
                val groupInvites = try { RetrofitClient.api.getPendingGroupInvites(userId) } catch (e: Exception) { emptyList() }

                myGroupsList = allGroups.filter { it.membersIds?.contains(userId) == true }
                friendsList = friends

                val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Io"
                val tempMap = mutableMapOf<String, String>()
                tempMap[userId] = currentUserName
                friends.forEach { tempMap[it.id] = it.display_name ?: it.email }
                allUsersMap = tempMap

                withContext(Dispatchers.Main) {
                    rvFriends.adapter = FriendsAdapter(friendsList)
                    rvGroups.adapter = GroupsAdapter(myGroupsList, allUsersMap) { group -> showGroupDetailsDialog(group) }

                    tvRequestsHeader.visibility = if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE
                    rvFriendRequests.visibility = if (friendRequests.isNotEmpty()) View.VISIBLE else View.GONE
                    if (friendRequests.isNotEmpty()) rvFriendRequests.adapter = FriendRequestsAdapter(friendRequests) { id, s -> respondToFriendRequest(id, s) }

                    tvGroupInvitesHeader.visibility = if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE
                    rvGroupInvites.visibility = if (groupInvites.isNotEmpty()) View.VISIBLE else View.GONE
                    if (groupInvites.isNotEmpty()) rvGroupInvites.adapter = GroupInvitesAdapter(groupInvites) { id, s -> respondToGroupInvite(id, s) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading: ${e.message}")
            }
        }
    }

    private fun showGroupDetailsDialog(group: GroupDetailedResponse) {
        val members = group.membersIds?.map { allUsersMap[it] ?: "Utente ($it)" } ?: emptyList()
        AlertDialog.Builder(requireContext()).setTitle(group.name).setMessage("Membri:\n\n" + members.joinToString("\n")).setPositiveButton("Chiudi", null).show()
    }

    private fun showSelectGroupForRunDialog() {
        if (myGroupsList.isEmpty()) { Toast.makeText(context, "Nessun gruppo disponibile", Toast.LENGTH_SHORT).show(); return }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_group, null)
        val rvSelect = dialogView.findViewById<RecyclerView>(R.id.rvSelectGroup)
        rvSelect.layoutManager = LinearLayoutManager(context)
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Inizia corsa di gruppo").setView(dialogView).setNegativeButton("Annulla", null).create()
        rvSelect.adapter = GroupsAdapter(myGroupsList, allUsersMap) { group -> dialog.dismiss(); startGroupRunFlow(group) }
        dialog.show()
    }

    private fun startGroupRunFlow(group: GroupDetailedResponse) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val gId = group.realId
        val userName = user.displayName ?: user.email ?: "Runner"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = RetrofitClient.api.startGroupRun(gId, userId)
                withContext(Dispatchers.Main) {
                    val lobbyRef = database.child("lobbies").child(gId)
                    lobbyRef.removeValue().addOnCompleteListener {
                        val lobbyData = mapOf("status" to "waiting", "organizer" to userId)
                        lobbyRef.setValue(lobbyData)
                        // Scriviamo il nome partecipante
                        lobbyRef.child("names").child(userId).setValue(userName)
                        lobbyRef.child(userId).setValue("ready")
                        
                        res.members.forEach { mId ->
                            if (mId != userId) {
                                database.child("run_invites").child(mId).child(gId).setValue(mapOf("groupName" to group.name, "from" to userId))
                            }
                        }
                        navigateToLobby(gId, group.name, res.members)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }

    private fun listenForRunInvites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database.child("run_invites").child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invites = mutableListOf<RunInvite>()
                for (child in snapshot.children) {
                    invites.add(RunInvite(child.key!!, child.child("groupName").value?.toString() ?: "Corsa"))
                }
                updateInvitesUI(invites)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateInvitesUI(invites: List<RunInvite>) {
        tvActiveRunInvitesHeader.visibility = if (invites.isNotEmpty()) View.VISIBLE else View.GONE
        rvActiveRunInvites.visibility = if (invites.isNotEmpty()) View.VISIBLE else View.GONE
        if (invites.isNotEmpty()) {
            rvActiveRunInvites.adapter = RunInviteAdapter(invites) { invite, action ->
                if (action == "accepted") {
                    val user = FirebaseAuth.getInstance().currentUser ?: return@RunInviteAdapter
                    val userId = user.uid
                    val userName = user.displayName ?: user.email ?: "Runner"
                    val lRef = database.child("lobbies").child(invite.groupId)
                    lRef.child("names").child(userId).setValue(userName)
                    lRef.child(userId).setValue("ready")
                    navigateToLobby(invite.groupId, invite.groupName, emptyList())
                }
                database.child("run_invites").child(FirebaseAuth.getInstance().currentUser!!.uid).child(invite.groupId).removeValue()
            }
        }
    }

    private fun navigateToLobby(id: String, name: String, members: List<String>) {
        val mainActivity = activity as? MainActivity ?: return
        mainActivity.navigateToFragment(GroupLobbyFragment.newInstance(id, name, members, allUsersMap), "LOBBY_$id", "Lobby $name")
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<EditText>(R.id.etGroupName)
        val rvInvite = dialogView.findViewById<RecyclerView>(R.id.rvInviteFriends)
        rvInvite.layoutManager = LinearLayoutManager(context)
        val adapter = InviteFriendsAdapter(friendsList)
        rvInvite.adapter = adapter
        AlertDialog.Builder(requireContext()).setTitle("Nuovo Gruppo").setView(dialogView).setPositiveButton("Crea") { _, _ ->
            val name = etGroupName.text.toString()
            if (name.isNotBlank()) createGroup(name, adapter.getSelectedFriends())
        }.setNegativeButton("Annulla", null).show()
    }

    private fun createGroup(name: String, friendIds: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = RetrofitClient.api.createGroup(name, userId)
                friendIds.forEach { RetrofitClient.api.inviteToGroup(res.id, userId, it) }
                withContext(Dispatchers.Main) { loadData() }
            } catch (e: Exception) {}
        }
    }

    private fun respondToFriendRequest(id: String, s: String) {
        CoroutineScope(Dispatchers.IO).launch { try { RetrofitClient.api.respondToFriendRequest(id, s); withContext(Dispatchers.Main) { loadData() } } catch (e: Exception) {} }
    }

    private fun respondToGroupInvite(id: String, s: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch { try { RetrofitClient.api.respondInvite(id, userId, s); withContext(Dispatchers.Main) { loadData() } } catch (e: Exception) {} }
    }
}
