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

object ParticipantColorProvider {

    private val colors = listOf(
        Color.parseColor("#2196F3"), // blu
        Color.parseColor("#F44336"), // rosso
        Color.parseColor("#4CAF50"), // verde
        Color.parseColor("#9C27B0")  // viola
    )

    fun getColorForUser(
        userId: String,
        memberIds: List<String>,
        fallbackOrder: List<String> = emptyList()
    ): Int {
        val indexFromMembers = memberIds.indexOf(userId)

        if (indexFromMembers >= 0) {
            return colors[indexFromMembers % colors.size]
        }

        val indexFromFallback = fallbackOrder.indexOf(userId)

        if (indexFromFallback >= 0) {
            return colors[indexFromFallback % colors.size]
        }

        return colors[0]
    }
}

class ParticipantStatsAdapter(
    private var stats: List<ParticipantLiveStats>,
    private var namesMap: Map<String, String>,
    private var memberIds: List<String> = emptyList()
) : RecyclerView.Adapter<ParticipantStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvParticipantName)
        val tvDistance: TextView = view.findViewById(R.id.tvParticipantDistance)
        val tvCalories: TextView = view.findViewById(R.id.tvParticipantCalories)
        val tvSpeed: TextView = view.findViewById(R.id.tvParticipantSpeed)
        val indicator: View = view.findViewById(R.id.viewUserIndicator)
    }

    init {
        stats = sortByCorrectOrder(stats)
    }

    fun updateData(newData: List<ParticipantLiveStats>) {
        stats = sortByCorrectOrder(newData)
        notifyDataSetChanged()
    }

    fun updateNames(newNames: Map<String, String>) {
        namesMap = newNames
        notifyDataSetChanged()
    }

    fun updateMemberIds(newMemberIds: List<String>) {
        memberIds = newMemberIds
        stats = sortByCorrectOrder(stats)
        notifyDataSetChanged()
    }

    private fun fallbackOrder(): List<String> {
        return stats.map { it.userId }.distinct()
    }

    private fun sortByCorrectOrder(list: List<ParticipantLiveStats>): List<ParticipantLiveStats> {
        val fallback = list.map { it.userId }.distinct()

        return list.sortedWith(
            compareBy<ParticipantLiveStats> {
                val indexFromMembers = memberIds.indexOf(it.userId)
                val indexFromFallback = fallback.indexOf(it.userId)

                when {
                    indexFromMembers >= 0 -> indexFromMembers
                    indexFromFallback >= 0 -> indexFromFallback
                    else -> Int.MAX_VALUE
                }
            }.thenBy { it.userId }
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_participant_stats, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = stats[position]
        val isMe = stat.userId == FirebaseAuth.getInstance().currentUser?.uid

        val displayName = when {
            isMe -> "You"
            namesMap.containsKey(stat.userId) -> namesMap[stat.userId]
            else -> "Participant ${position + 1}"
        }

        val fallback = fallbackOrder()

        val color = ParticipantColorProvider.getColorForUser(
            userId = stat.userId,
            memberIds = memberIds,
            fallbackOrder = fallback
        )

        holder.tvName.text = displayName

        holder.tvDistance.text =
            String.format(Locale.getDefault(), "Distance: %.2f km", stat.distance)

        holder.tvCalories.text =
            String.format(Locale.getDefault(), "Calories: %d kcal", stat.calories)

        holder.tvSpeed.text =
            String.format(Locale.getDefault(), "Average speed: %.1f km/h", stat.speed)

        holder.indicator.setBackgroundColor(color)
    }

    override fun getItemCount(): Int = stats.size
}