package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.FriendRequestResponse
import com.google.android.material.button.MaterialButton

class FriendRequestsAdapter(
    private val requests: List<FriendRequestResponse>,
    private val onResponse: (String, String) -> Unit
) : RecyclerView.Adapter<FriendRequestsAdapter.RequestViewHolder>() {

    class RequestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmail: TextView = view.findViewById(R.id.tvRequestEmail)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAccept)
        val btnReject: MaterialButton = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        holder.tvEmail.text = request.from_user_email
        
        holder.btnAccept.setOnClickListener { onResponse(request.id, "accepted") }
        holder.btnReject.setOnClickListener { onResponse(request.id, "rejected") }
    }

    override fun getItemCount() = requests.size
}
