package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.UserResponse

class InviteFriendsAdapter(private val friends: List<UserResponse>) :
    RecyclerView.Adapter<InviteFriendsAdapter.InviteViewHolder>() {

    private val selectedFriendIds = mutableSetOf<String>()

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
        holder.tvName.text = friend.display_name ?: friend.email
        
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedFriendIds.contains(friend.id)
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFriendIds.add(friend.id)
            } else {
                selectedFriendIds.remove(friend.id)
            }
        }
    }

    override fun getItemCount() = friends.size

    fun getSelectedFriends(): List<String> = selectedFriendIds.toList()
}
