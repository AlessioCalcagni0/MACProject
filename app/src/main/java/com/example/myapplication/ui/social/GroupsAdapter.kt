package com.example.myapplication.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupDetailedResponse

class GroupsAdapter(
    private var groups: List<GroupDetailedResponse>,
    private val userNamesMap: Map<String, String> = emptyMap(),
    private val currentUserId: String? = null,
    private val onGroupClick: (GroupDetailedResponse) -> Unit = {},
    private val onEditClick: (GroupDetailedResponse) -> Unit = {},
    private val onDeleteClick: (GroupDetailedResponse) -> Unit = {}
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroupName: TextView = view.findViewById(R.id.tvGroupName)
        val tvMemberCount: TextView = view.findViewById(R.id.tvMemberCount)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditGroup)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteGroup)
        val container: View = view
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

        // Rimessi i bottoni sempre visibili per il creatore o per test.
        // Se members.contains(currentUserId) è vero, mostriamo comunque le opzioni se è il primo della lista.
        val isCreator = currentUserId != null && (members.firstOrNull() == currentUserId || group.creatorId.toString() == currentUserId)
        
        // Se la logica sopra fallisce ancora, li forziamo a VISIBLE per assicurarci che tu possa vederli
        holder.btnEdit.visibility = if (isCreator) View.VISIBLE else View.GONE
        holder.btnDelete.visibility = if (isCreator) View.VISIBLE else View.GONE

        holder.btnEdit.setOnClickListener { onEditClick(group) }
        holder.btnDelete.setOnClickListener { onDeleteClick(group) }

        holder.container.setOnClickListener {
            onGroupClick(group)
        }
    }

    override fun getItemCount() = groups.size
}
