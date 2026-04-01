package com.example.myapplication.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

class StatsFragment : Fragment() {

    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalRuns: TextView
    private lateinit var tvTotalCalories: TextView
    private lateinit var tvGoalsAchieved: TextView
    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)
        
        tvTotalDistance = view.findViewById(R.id.tvStatsTotalDistance)
        tvTotalRuns = view.findViewById(R.id.tvStatsTotalRuns)
        tvTotalCalories = view.findViewById(R.id.tvStatsTotalCalories)
        tvGoalsAchieved = view.findViewById(R.id.tvStatsGoalsAchieved)
        
        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        loadStatistics()
        
        return view
    }

    private fun loadStatistics() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        database.child("users").child(userId).child("recent_activities")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalDist = 0.0
                    var totalCal = 0
                    var totalRuns = 0
                    var goalsAchieved = 0
                    
                    for (activity in snapshot.children) {
                        totalRuns++
                        
                        // Estrai statistiche (distanza e calorie sono spesso nel primo elemento di "stats" per le corse singole)
                        val statsSnapshot = activity.child("stats")
                        for (stat in statsSnapshot.children) {
                            val dist = (stat.child("distance").value as? Number)?.toDouble() ?: 0.0
                            val cal = (stat.child("calories").value as? Number)?.toInt() ?: 0
                            
                            // In una corsa di gruppo, sommiamo solo le NOSTRE statistiche se l'attività è di gruppo? 
                            // Per semplicità ora sommiamo quello che troviamo, assumendo che in recent_activities ci siano i dati personali.
                            totalDist += dist
                            totalCal += cal
                        }
                        
                        // Verifica se l'obiettivo è stato raggiunto
                        val achieved = activity.child("goalAchieved").value as? Boolean ?: false
                        if (achieved) {
                            goalsAchieved++
                        }
                    }
                    
                    updateUI(totalDist, totalRuns, totalCal, goalsAchieved)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI(dist: Double, runs: Int, cal: Int, goals: Int) {
        if (!isAdded) return
        
        tvTotalDistance.text = String.format(Locale.getDefault(), "%.2f km", dist)
        tvTotalRuns.text = runs.toString()
        tvTotalCalories.text = "$cal kcal"
        tvGoalsAchieved.text = "$goals obiettivi completati"
    }
}
