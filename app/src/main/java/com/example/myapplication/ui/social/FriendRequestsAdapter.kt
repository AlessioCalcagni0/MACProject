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
    private val onResponse: (String, String, String) -> Unit
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
        // Mostra il display_name se presente, altrimenti l'email
        val senderInfo = request.fromUser.display_name ?: request.fromUser.email
        holder.tvEmail.text = senderInfo
        
        holder.btnAccept.setOnClickListener { onResponse(request.id, "accepted", request.fromUser.id) }
        holder.btnReject.setOnClickListener { onResponse(request.id, "rejected", request.fromUser.id) }
    }

    override fun getItemCount() = requests.size
}
