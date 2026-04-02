package com.example.myapplication.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class StatsFragment : Fragment() {

    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalRuns: TextView
    private lateinit var tvTotalCalories: TextView
    private lateinit var tvChartTitle: TextView
    private lateinit var chartView: StatsChartView
    private lateinit var btnSpeed: MaterialButton
    private lateinit var btnDistance: MaterialButton
    private lateinit var btnCalories: MaterialButton
    
    private lateinit var database: DatabaseReference
    
    private val speedList = mutableListOf<Float>()
    private val distanceList = mutableListOf<Float>()
    private val caloriesList = mutableListOf<Float>()
    private val dateLabels = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stats, container, false)
        
        tvTotalDistance = view.findViewById(R.id.tvStatsTotalDistance)
        tvTotalRuns = view.findViewById(R.id.tvStatsTotalRuns)
        tvTotalCalories = view.findViewById(R.id.tvStatsTotalCalories)
        tvChartTitle = view.findViewById(R.id.tvChartTitle)
        chartView = view.findViewById(R.id.statsChartView)
        
        btnSpeed = view.findViewById(R.id.btnShowSpeed)
        btnDistance = view.findViewById(R.id.btnShowDistance)
        btnCalories = view.findViewById(R.id.btnShowCalories)
        
        database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
        
        setupButtons()
        loadStatistics()
        
        return view
    }

    private fun setupButtons() {
        btnSpeed.setOnClickListener {
            tvChartTitle.text = "Andamento Velocità"
            chartView.setData(speedList, dateLabels, "km/h")
        }
        btnDistance.setOnClickListener {
            tvChartTitle.text = "Andamento Distanza"
            chartView.setData(distanceList, dateLabels, "km")
        }
        btnCalories.setOnClickListener {
            tvChartTitle.text = "Andamento Calorie"
            chartView.setData(caloriesList, dateLabels, "kcal")
        }
    }

    private fun loadStatistics() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        database.child("users").child(userId).child("recent_activities")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalDist = 0.0
                    var totalCal = 0
                    var totalRuns = 0
                    
                    speedList.clear()
                    distanceList.clear()
                    caloriesList.clear()
                    dateLabels.clear()
                    
                    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    
                    val activities = snapshot.children.sortedBy { 
                        it.child("timestamp").value as? Long ?: 0L 
                    }

                    for (activity in activities) {
                        totalRuns++
                        
                        val timestamp = activity.child("timestamp").value as? Long ?: 0L
                        dateLabels.add(if (timestamp > 0) sdf.format(Date(timestamp)) else "-")

                        var runDist = 0.0
                        var runCal = 0
                        var runAvgSpeed = 0.0
                        
                        val statsSnapshot = activity.child("stats")
                        val statsCount = statsSnapshot.childrenCount
                        
                        for (stat in statsSnapshot.children) {
                            val dist = (stat.child("distance").value as? Number)?.toDouble() ?: 0.0
                            val cal = (stat.child("calories").value as? Number)?.toInt() ?: 0
                            val speed = (stat.child("speed").value as? Number)?.toDouble() ?: 0.0
                            
                            runDist += dist
                            runCal += cal
                            runAvgSpeed += speed
                        }
                        
                        if (statsCount > 0) runAvgSpeed /= statsCount

                        totalDist += runDist
                        totalCal += runCal
                        
                        speedList.add(runAvgSpeed.toFloat())
                        distanceList.add(runDist.toFloat())
                        caloriesList.add(runCal.toFloat())
                    }
                    
                    updateUI(totalDist, totalRuns, totalCal)
                    // Default view: Speed
                    chartView.setData(speedList, dateLabels, "km/h")
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI(dist: Double, runs: Int, cal: Int) {
        if (!isAdded) return
        
        tvTotalDistance.text = String.format(Locale.getDefault(), "%.2f km", dist)
        tvTotalRuns.text = runs.toString()
        tvTotalCalories.text = "$cal kcal"
    }
}
