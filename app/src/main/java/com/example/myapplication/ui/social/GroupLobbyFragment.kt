package com.example.myapplication.ui.social

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.Serializable

class GroupLobbyFragment : Fragment() {

    private var groupName: String? = null
    private var groupId: String? = null
    private var memberIds: List<String>? = null
    private var sessionId: String? = null

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
        private const val ARG_SESSION_ID = "session_id"

        fun newInstance(
            groupId: String,
            groupName: String,
            members: List<String>,
            namesMap: Map<String, String>,
            sessionId: String
        ): GroupLobbyFragment {
            val fragment = GroupLobbyFragment()
            val args = Bundle()

            args.putString(ARG_GROUP_ID, groupId)
            args.putString(ARG_GROUP_NAME, groupName)
            args.putStringArrayList(ARG_MEMBERS, ArrayList(members))
            args.putSerializable(ARG_NAMES_MAP, namesMap as Serializable)
            args.putString(ARG_SESSION_ID, sessionId)

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
            sessionId = it.getString(ARG_SESSION_ID)

            @Suppress("UNCHECKED_CAST")
            val initialNames = it.getSerializable(ARG_NAMES_MAP) as? Map<String, String>

            if (initialNames != null) {
                dynamicNamesMap.putAll(initialNames)
            }
        }

        database = FirebaseDatabase.getInstance(DB_URL).reference
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_group_lobby, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.lobbyToolbar)
        val tvName = view.findViewById<TextView>(R.id.tvLobbyGroupName)
        val rvMembers = view.findViewById<RecyclerView>(R.id.rvLobbyMembers)
        val btnStart = view.findViewById<MaterialButton>(R.id.btnStartRunNow)

        tvName.text = groupName

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        rvMembers.layoutManager = LinearLayoutManager(context)
        adapter = LobbyMembersAdapter(emptyList(), dynamicNamesMap)
        rvMembers.adapter = adapter

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        database.child("lobbies")
            .child(groupId!!)
            .child("organizer")
            .get()
            .addOnSuccessListener { snapshot ->
                isOrganizer = currentUserId == snapshot.value as? String

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

        btnStart.setOnClickListener {
            showNamingDialog()
        }

        listenToLobby()
    }

    private fun showNamingDialog() {
        val et = EditText(requireContext())
        et.hint = groupName ?: "Group Run"

        AlertDialog.Builder(requireContext())
            .setTitle("Name your run")
            .setMessage("Enter a name for this session.")
            .setView(et)
            .setPositiveButton("Start") { _, _ ->
                val runName = et.text.toString().ifBlank { groupName ?: "Run" }
                startGroupRun(runName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startGroupRun(runName: String) {
        val gId = groupId ?: return
        val currentSessionId = sessionId ?: return

        val updates = mapOf(
            "status" to "started",
            "startTime" to ServerValue.TIMESTAMP,
            "runName" to runName,
            "sessionId" to currentSessionId
        )

        database.child("lobbies")
            .child(gId)
            .updateChildren(updates)
    }

    private fun listenToLobby() {
        val gId = groupId ?: return

        lobbyRef = database.child("lobbies").child(gId)

        membersValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (hasNavigatedToRun) return

                val firebaseSessionId = snapshot.child("sessionId").value as? String

                if (sessionId.isNullOrBlank() || firebaseSessionId != sessionId) {
                    Log.w(
                        "Lobby",
                        "Ignoring old lobby state. Expected sessionId=$sessionId, found=$firebaseSessionId"
                    )
                    return
                }

                val status = snapshot.child("status").value as? String

                if (status == "started") {
                    hasNavigatedToRun = true

                    val runName = snapshot.child("runName").value as? String
                        ?: groupName
                        ?: "Run"

                    navigateToGroupRunScreen(runName)
                    return
                }

                val namesSnap = snapshot.child("names")

                if (namesSnap.exists()) {
                    for (child in namesSnap.children) {
                        val userId = child.key
                        val name = child.value

                        if (userId != null && name != null) {
                            dynamicNamesMap[userId] = name.toString()
                        }
                    }

                    adapter.updateNames(dynamicNamesMap)
                }

                presentMembers.clear()

                val reservedKeys = listOf(
                    "status",
                    "organizer",
                    "startTime",
                    "runName",
                    "names",
                    "sessionId",
                    "createdAt"
                )

                for (child in snapshot.children) {
                    if (child.key !in reservedKeys) {
                        presentMembers[child.key ?: continue] = child.value.toString()
                    }
                }

                updateUI()

                if (isOrganizer) {
                    checkAllReady()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Lobby", "Database error: ${error.message}")
            }
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
            btnStart.text = "START RUN"
        } else {
            btnStart.isEnabled = false
            btnStart.alpha = 0.5f
            btnStart.text = "WAITING ($readyCount/$totalNeeded)"
        }
    }

    private fun navigateToGroupRunScreen(runName: String) {
        val activity = activity as? MainActivity ?: return

        val fragment = GroupRunFragment.newInstance(
            groupId!!,
            runName,
            memberIds ?: emptyList(),
            dynamicNamesMap
        )

        activity.navigateToFragment(fragment, "RUN_ACTIVE_${groupId}_$sessionId", runName)
    }

    private fun updateUI() {
        val list = mutableListOf<LobbyMemberState>()

        memberIds?.forEach { id ->
            if (!dynamicNamesMap.containsKey(id)) {
                fetchNameFromUsersNode(id)
            }

            list.add(
                LobbyMemberState(
                    userId = id,
                    status = presentMembers[id] ?: "waiting"
                )
            )
        }

        adapter.updateList(list)
    }

    private fun fetchNameFromUsersNode(uid: String) {
        database.child("users")
            .child(uid)
            .child("username")
            .get()
            .addOnSuccessListener {
                val name = it.value?.toString()

                if (name != null) {
                    dynamicNamesMap[uid] = name
                    adapter.updateNames(dynamicNamesMap)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (::lobbyRef.isInitialized) {
            lobbyRef.removeEventListener(membersValueListener)
        }
    }
}

data class LobbyMemberState(
    val userId: String,
    val status: String
)

class LobbyMembersAdapter(
    private var members: List<LobbyMemberState>,
    private var namesMap: Map<String, String>
) : RecyclerView.Adapter<LobbyMembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMemberName)
        val tvStatus: TextView = view.findViewById(R.id.tvMemberStatus)
    }

    fun updateList(newList: List<LobbyMemberState>) {
        members = newList
        notifyDataSetChanged()
    }

    fun updateNames(newNames: Map<String, String>) {
        val updatedMap = namesMap.toMutableMap()
        updatedMap.putAll(newNames)
        namesMap = updatedMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lobby_member, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = members[position]
        val isMe = item.userId == FirebaseAuth.getInstance().currentUser?.uid

        val name = when {
            isMe -> "You"
            namesMap.containsKey(item.userId) -> namesMap[item.userId]
            else -> "Member (${item.userId.take(4)})"
        }

        holder.tvName.text = name

        val statusText = if (item.status == "ready") "READY" else "WAITING..."
        holder.tvStatus.text = statusText

        if (item.status == "ready") {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            holder.tvStatus.setBackgroundResource(R.drawable.bg_dialog_rounded)
            holder.tvStatus.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#666666"))
            holder.tvStatus.setBackgroundResource(R.drawable.bg_dialog_rounded)
            holder.tvStatus.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
        }
    }

    override fun getItemCount() = members.size
}