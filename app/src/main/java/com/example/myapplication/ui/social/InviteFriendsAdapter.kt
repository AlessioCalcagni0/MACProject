package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.UserResponse

class InviteFriendsAdapter(
    private val friends: List<UserResponse>,
    private val initialSelectedIds: List<String> = emptyList()
) : RecyclerView.Adapter<InviteFriendsAdapter.InviteViewHolder>() {

    private val selectedFriendIds = mutableSetOf<String>().apply {
        addAll(initialSelectedIds)
    }

    class InviteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val checkBox: CheckBox = view.findViewById(R.id.cbInvite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_invite_friend, parent, false)
        return InviteViewHolder(view)
    }

    override fun onBindViewHolder(holder: InviteViewHolder, position: Int) {
        val friend = friends[position]
        // Updated to use displayName from UserResponse
        holder.tvName.text = friend.displayName ?: friend.email
        
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedFriendIds.contains(friend.firebaseUid)
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFriendIds.add(friend.firebaseUid)
            } else {
                selectedFriendIds.remove(friend.firebaseUid)
            }
        }
    }

    override fun getItemCount() = friends.size

    fun getSelectedFriends(): List<String> = selectedFriendIds.toList()
}
