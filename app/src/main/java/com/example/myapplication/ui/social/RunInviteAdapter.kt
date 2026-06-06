package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.android.material.button.MaterialButton

data class RunInvite(
    val groupId: String,
    val groupName: String,
    val members: List<String> = emptyList(),
    val sessionId: String = ""
)

class RunInviteAdapter(
    private var invites: List<RunInvite>,
    private val onAction: (RunInvite, String) -> Unit
) : RecyclerView.Adapter<RunInviteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvRunInviteMessage)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAcceptRun)
        val btnReject: MaterialButton = view.findViewById(R.id.btnRejectRun)
    }

    fun updateData(newList: List<RunInvite>) {
        invites = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_invite, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val invite = invites[position]

        holder.tvMessage.text = "Group '${invite.groupName}' is starting a run!"

        holder.btnAccept.setOnClickListener {
            onAction(invite, "accepted")
        }

        holder.btnReject.setOnClickListener {
            onAction(invite, "rejected")
        }
    }

    override fun getItemCount() = invites.size
}