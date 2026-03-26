package com.example.myapplication.ui.social

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class ParticipantStatsAdapter(
    private var stats: List<ParticipantLiveStats>,
    private val namesMap: Map<String, String>
) : RecyclerView.Adapter<ParticipantStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvParticipantName)
        val tvSpeed: TextView = view.findViewById(R.id.tvParticipantSpeed)
        val tvCalories: TextView = view.findViewById(R.id.tvParticipantCalories)
        val indicator: View = view.findViewById(R.id.viewUserIndicator)
    }

    fun updateData(newData: List<ParticipantLiveStats>) {
        stats = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_participant_stats, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = stats[position]
        val isMe = s.userId == FirebaseAuth.getInstance().currentUser?.uid
        holder.tvName.text = namesMap[s.userId] ?: "Runner"
        holder.tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", s.speed)
        holder.tvCalories.text = String.format(Locale.getDefault(), "%.2f km | %d kcal", s.distance, s.calories)
        holder.indicator.setBackgroundColor(if (isMe) Color.BLUE else Color.RED)
    }

    override fun getItemCount() = stats.size
}
