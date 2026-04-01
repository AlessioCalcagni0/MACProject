package com.example.myapplication.ui.social

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.GroupDetailedResponse

class GroupsAdapter(
    private var groups: List<GroupDetailedResponse>,
    private val userNamesMap: Map<String, String> = emptyMap(),
    private val onGroupClick: (GroupDetailedResponse) -> Unit = {}
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroupName: TextView = view.findViewById(R.id.tvGroupName)
        val tvMemberCount: TextView = view.findViewById(R.id.tvMemberCount)
        val container: View = view // L'intera riga
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

        // Rendi i testi non cliccabili così il tocco passa al padre (la riga)
        holder.tvGroupName.isClickable = false
        holder.tvMemberCount.isClickable = false

        holder.container.setOnClickListener {
            // LOG DI EMERGENZA: apparirà in rosso nel Logcat
            Log.wtf("CLICK_CHECK", "!!! CLICK AVVENUTO SU: ${group.name} !!!")
            onGroupClick(group)
        }
    }

    override fun getItemCount() = groups.size
}
