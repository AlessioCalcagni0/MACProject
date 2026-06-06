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
    private val showActions: Boolean = true,
    private val onGroupClick: (GroupDetailedResponse) -> Unit = {},
    private val onEditClick: (GroupDetailedResponse) -> Unit = {},
    private val onDeleteClick: (GroupDetailedResponse) -> Unit = {}
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroupName: TextView = view.findViewById(R.id.tvGroupName)
        val tvMemberCount: TextView = view.findViewById(R.id.tvMemberCount)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditGroup)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)

        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]

        holder.tvGroupName.text = group.name

        val members = group.membersIds ?: emptyList()
        holder.tvMemberCount.text = "${members.size} members"

        val isCreator = currentUserId != null && group.creatorId == currentUserId
        val canManageGroup = showActions && isCreator

        if (canManageGroup) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE

            holder.btnEdit.setOnClickListener {
                onEditClick(group)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(group)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE

            holder.btnEdit.setOnClickListener(null)
            holder.btnDelete.setOnClickListener(null)
        }

        holder.itemView.setOnClickListener {
            onGroupClick(group)
        }
    }

    override fun getItemCount() = groups.size
}