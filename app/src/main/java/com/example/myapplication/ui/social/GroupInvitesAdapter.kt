package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupInviteResponse
import com.google.android.material.button.MaterialButton

class GroupInvitesAdapter(
    private val invites: List<GroupInviteResponse>,
    private val onRespond: (String, String) -> Unit
) : RecyclerView.Adapter<GroupInvitesAdapter.InviteViewHolder>() {

    class InviteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvInviteMessage)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAcceptInvite)
        val btnReject: MaterialButton = view.findViewById(R.id.btnRejectInvite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_invite, parent, false)
        return InviteViewHolder(view)
    }

    override fun onBindViewHolder(holder: InviteViewHolder, position: Int) {
        val invite = invites[position]
        // Utilizza i dati corretti dal tuo backend (invitedBy è un oggetto UserResponse)
        val inviterName = invite.invitedBy?.display_name ?: invite.invitedBy?.email ?: "Un utente"
        holder.tvMessage.text = "$inviterName ti ha invitato al gruppo ${invite.group_name ?: "Senza Nome"}"
        
        holder.btnAccept.setOnClickListener { onRespond(invite.id, "accepted") }
        holder.btnReject.setOnClickListener { onRespond(invite.id, "rejected") }
    }

    override fun getItemCount() = invites.size
}
