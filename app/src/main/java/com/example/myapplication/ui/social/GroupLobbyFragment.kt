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
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.Serializable

class GroupLobbyFragment : Fragment() {

    private var groupName: String? = null
    private var groupId: String? = null
    private var memberIds: List<String>? = null
    private var userNamesMap: Map<String, String>? = null
    private val dynamicNamesMap = mutableMapOf<String, String>()

    private lateinit var database: DatabaseReference
    private lateinit var lobbyRef: DatabaseReference
    private lateinit var membersValueListener: ValueEventListener
    
    private val presentMembers = mutableMapOf<String, String>()
    private lateinit var adapter: LobbyMembersAdapter
    private var isOrganizer: Boolean = false
    private var hasNavigatedToRun = false
    private val DB_URL = "https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app"

    companion object {
        private const val ARG_GROUP_NAME = "group_name"
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_MEMBERS = "members"
        private const val ARG_NAMES_MAP = "names_map"

        fun newInstance(groupId: String, groupName: String, members: List<String>, namesMap: Map<String, String>): GroupLobbyFragment {
            val fragment = GroupLobbyFragment()
            val args = Bundle()
            args.putString(ARG_GROUP_ID, groupId)
            args.putString(ARG_GROUP_NAME, groupName)
            args.putStringArrayList(ARG_MEMBERS, ArrayList(members))
            args.putSerializable(ARG_NAMES_MAP, namesMap as Serializable)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId = it.getString(ARG_GROUP_ID)
            groupName = it.getString(ARG_GROUP_NAME)
            memberIds = it.getStringArrayList(ARG_MEMBERS)
            @Suppress("UNCHECKED_CAST")
            userNamesMap = it.getSerializable(ARG_NAMES_MAP) as? Map<String, String>
        }
        database = FirebaseDatabase.getInstance(DB_URL).reference
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_group_lobby, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.lobbyToolbar)
        val tvName = view.findViewById<TextView>(R.id.tvLobbyGroupName)
        val rvMembers = view.findViewById<RecyclerView>(R.id.rvLobbyMembers)
        val btnStart = view.findViewById<MaterialButton>(R.id.btnStartRunNow)

        tvName.text = groupName
        toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        rvMembers.layoutManager = LinearLayoutManager(context)
        adapter = LobbyMembersAdapter(emptyList(), userNamesMap ?: emptyMap())
        rvMembers.adapter = adapter

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        database.child("lobbies").child(groupId!!).child("organizer").get().addOnSuccessListener { snapshot ->
            isOrganizer = (currentUserId == snapshot.value as? String)
            activity?.runOnUiThread {
                if (isOrganizer) {
                    btnStart.visibility = View.VISIBLE
                    btnStart.isEnabled = false
                    btnStart.alpha = 0.5f
                } else {
                    btnStart.visibility = View.GONE
                }
            }
        }

        btnStart.setOnClickListener { showNamingDialog() }

        listenToLobby()
    }

    private fun showNamingDialog() {
        val et = EditText(requireContext())
        et.hint = groupName ?: "Corsa di Gruppo"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Dai un nome alla tua corsa")
            .setMessage("Inserisci un nome per questa sessione.")
            .setView(et)
            .setPositiveButton("Inizia") { _, _ ->
                val runName = et.text.toString().ifBlank { groupName ?: "Corsa" }
                startGroupRun(runName)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun startGroupRun(runName: String) {
        val gId = groupId ?: return
        val updates = mapOf(
            "status" to "started",
            "startTime" to ServerValue.TIMESTAMP,
            "runName" to runName
        )
        database.child("lobbies").child(gId).updateChildren(updates)
    }

    private fun listenToLobby() {
        val gId = groupId ?: return
        lobbyRef = database.child("lobbies").child(gId)
        
        membersValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (hasNavigatedToRun) return

                val status = snapshot.child("status").value as? String
                if (status == "started") {
                    hasNavigatedToRun = true
                    val runName = snapshot.child("runName").value as? String ?: groupName ?: "Corsa"
                    navigateToGroupRunScreen(runName)
                    return
                }

                val namesSnap = snapshot.child("names")
                for (child in namesSnap.children) {
                    val uId = child.key ?: continue
                    val name = child.value?.toString() ?: continue
                    dynamicNamesMap[uId] = name
                }
                adapter.updateNames(dynamicNamesMap)

                presentMembers.clear()
                for (child in snapshot.children) {
                    if (child.key !in listOf("status", "organizer", "startTime", "runName", "names")) {
                        presentMembers[child.key ?: continue] = child.value.toString()
                    }
                }
                updateUI()
                if (isOrganizer) checkAllReady()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        lobbyRef.addValueEventListener(membersValueListener)
    }

    private fun checkAllReady() {
        val btnStart = view?.findViewById<MaterialButton>(R.id.btnStartRunNow) ?: return
        val readyCount = presentMembers.values.count { it == "ready" }
        val totalNeeded = memberIds?.size ?: 0

        if (readyCount >= totalNeeded && totalNeeded > 0) {
            btnStart.isEnabled = true
            btnStart.alpha = 1.0f
            btnStart.text = "INIZIA CORSA"
        } else {
            btnStart.isEnabled = false
            btnStart.alpha = 0.5f
            btnStart.text = "ATTENDI ($readyCount/$totalNeeded)"
        }
    }

    private fun navigateToGroupRunScreen(runName: String) {
        val activity = activity as? MainActivity ?: return
        val fragment = GroupRunFragment.newInstance(
            groupId!!, 
            runName, 
            memberIds ?: emptyList(), 
            dynamicNamesMap.ifEmpty { userNamesMap ?: emptyMap() }
        )
        activity.navigateToFragment(fragment, "RUN_ACTIVE", runName)
    }

    private fun updateUI() {
        val list = mutableListOf<LobbyMemberState>()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        memberIds?.forEach { id ->
            if (id != currentUserId) {
                list.add(LobbyMemberState(id, presentMembers[id] ?: "waiting"))
            }
        }
        adapter.updateList(list)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::lobbyRef.isInitialized) lobbyRef.removeEventListener(membersValueListener)
    }
}

data class LobbyMemberState(val userId: String, val status: String)

class LobbyMembersAdapter(private var members: List<LobbyMemberState>, private var namesMap: Map<String, String>) : RecyclerView.Adapter<LobbyMembersAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName: TextView = view.findViewById(android.R.id.text1) }
    
    fun updateList(newList: List<LobbyMemberState>) { members = newList; notifyDataSetChanged() }
    
    fun updateNames(newNames: Map<String, String>) {
        val updatedMap = namesMap.toMutableMap()
        updatedMap.putAll(newNames)
        namesMap = updatedMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false))
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = members[position]
        val statusText = when(item.status) { "ready" -> "PRONTO"; "rejected" -> "RIFIUTATO"; else -> "IN ATTESA..." }
        val name = namesMap[item.userId] ?: item.userId
        holder.tvName.text = "$name - $statusText"
    }
    override fun getItemCount() = members.size
}
