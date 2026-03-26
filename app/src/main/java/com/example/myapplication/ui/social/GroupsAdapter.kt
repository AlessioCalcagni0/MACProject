package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupDetailedResponse

class GroupsAdapter(
    private val groups: List<GroupDetailedResponse>,
    private val userNamesMap: Map<String, String> = emptyMap(),
    private val onGroupClick: (GroupDetailedResponse) -> Unit = {}
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroupName: TextView = view.findViewById(R.id.tvGroupName)
        val tvMemberCount: TextView = view.findViewById(R.id.tvMemberCount)
        val tvMembersList: TextView = view.findViewById(R.id.tvMembersList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.tvGroupName.text = group.name
        val members = group.membersIds ?: emptyList()
        holder.tvMemberCount.text = "${members.size} partecipanti"
        
        // Converte gli ID in nomi usando la mappa fornita
        val names = members.map { id -> userNamesMap[id] ?: "Utente ($id)" }
        holder.tvMembersList.text = "Membri: " + names.joinToString(", ")

        holder.itemView.setOnClickListener { onGroupClick(group) }
    }

    override fun getItemCount() = groups.size
}
