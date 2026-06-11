package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.data.UserResponse
import com.google.firebase.database.FirebaseDatabase

class FriendsAdapter(private val friends: List<UserResponse>) :
    RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    private val databaseUrl =
        "https://maccproject-7f15c-default-rtdb.europe-west1.firebasedatabase.app"

    class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFriendPhoto: ImageView = view.findViewById(R.id.ivFriendPhoto)
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvEmail: TextView = view.findViewById(R.id.tvFriendEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)

        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]

        holder.tvName.text = friend.displayName
            ?: "${friend.name.orEmpty()} ${friend.surname.orEmpty()}".trim().ifBlank { friend.email ?: "Friend" }

        holder.tvEmail.text = friend.email ?: ""

        holder.ivFriendPhoto.clearColorFilter()

        Glide.with(holder.itemView.context)
            .load(R.drawable.ic_profile)
            .into(holder.ivFriendPhoto)

        val friendUid = friend.firebaseUid

        if (friendUid.isNotBlank()) {
            FirebaseDatabase
                .getInstance(databaseUrl)
                .reference
                .child("users")
                .child(friendUid)
                .child("profile_photo_url")
                .get()
                .addOnSuccessListener { snapshot ->
                    val photoUrl = snapshot.getValue(String::class.java)

                    if (!photoUrl.isNullOrBlank()) {
                        Glide.with(holder.itemView.context)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .circleCrop()
                            .into(holder.ivFriendPhoto)
                    } else {
                        Glide.with(holder.itemView.context)
                            .load(R.drawable.ic_profile)
                            .circleCrop()
                            .into(holder.ivFriendPhoto)
                    }
                }
                .addOnFailureListener {
                    Glide.with(holder.itemView.context)
                        .load(R.drawable.ic_profile)
                        .circleCrop()
                        .into(holder.ivFriendPhoto)
                }
        }
    }

    override fun getItemCount() = friends.size
}