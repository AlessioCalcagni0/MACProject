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
    private var namesMap: Map<String, String>
) : RecyclerView.Adapter<ParticipantStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvParticipantName)
        val tvDistance: TextView = view.findViewById(R.id.tvParticipantDistance)
        val tvCalories: TextView = view.findViewById(R.id.tvParticipantCalories)
        val tvSpeed: TextView = view.findViewById(R.id.tvParticipantSpeed)
        val indicator: View = view.findViewById(R.id.viewUserIndicator)
    }

    fun updateData(newData: List<ParticipantLiveStats>) {
        stats = newData
        notifyDataSetChanged()
    }

    fun updateNames(newNames: Map<String, String>) {
        namesMap = newNames
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_participant_stats, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = stats[position]
        val isMe = s.userId == FirebaseAuth.getInstance().currentUser?.uid

        val displayName = when {
            isMe -> "You"
            namesMap.containsKey(s.userId) -> namesMap[s.userId]
            else -> "Participant ${position + 1}"
        }

        holder.tvName.text = displayName
        holder.tvDistance.text =
            String.format(Locale.getDefault(), "Distance: %.2f km", s.distance)
        holder.tvCalories.text =
            String.format(Locale.getDefault(), "Calories: %d kcal", s.calories)
        holder.tvSpeed.text =
            String.format(Locale.getDefault(), "Average speed: %.1f km/h", s.speed)

        holder.indicator.setBackgroundColor(if (isMe) Color.BLUE else Color.RED)
    }
    override fun getItemCount() = stats.size
}
