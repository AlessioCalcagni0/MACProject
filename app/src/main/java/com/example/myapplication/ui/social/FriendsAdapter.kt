package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.UserResponse

class FriendsAdapter(private val friends: List<UserResponse>) :
    RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvEmail: TextView = view.findViewById(R.id.tvFriendEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.tvName.text = friend.display_name ?: friend.email
        holder.tvEmail.text = friend.email
    }

    override fun getItemCount() = friends.size
}
