package com.example.myapplication.ui.stats

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.domain.home.PastRunData
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
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
    
    private lateinit var viewModel: StatsViewModel
    
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
        
        val factory = StatsViewModelFactory()
        viewModel = ViewModelProvider(this, factory)[StatsViewModel::class.java]
        
        setupButtons()
        observeViewModel()
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            viewModel.loadActivities(userId)
        }
        
        return view
    }

    private fun setupButtons() {
        btnSpeed.setOnClickListener {
            updateButtonColors(btnSpeed)
            tvChartTitle.text = "Speed Progress"
            chartView.setData(speedList, dateLabels, "km/h")
        }
        btnDistance.setOnClickListener {
            updateButtonColors(btnDistance)
            tvChartTitle.text = "Distance Progress"
            chartView.setData(distanceList, dateLabels, "km")
        }
        btnCalories.setOnClickListener {
            updateButtonColors(btnCalories)
            tvChartTitle.text = "Calories Progress"
            chartView.setData(caloriesList, dateLabels, "kcal")
        }
    }

    private fun updateButtonColors(selected: MaterialButton) {
        val activeColor = Color.parseColor("#00BFFF")
        val inactiveColor = Color.WHITE

        listOf(btnSpeed, btnDistance, btnCalories).forEach { btn ->
            if (btn == selected) {
                btn.backgroundTintList = ColorStateList.valueOf(activeColor)
                btn.setTextColor(Color.WHITE)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(inactiveColor)
                btn.setTextColor(activeColor)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activities.collect { activities ->
                processActivities(activities)
            }
        }
    }

    private fun processActivities(activities: List<PastRunData>) {
        var totalDist = 0.0
        var totalCal = 0
        var totalRuns = 0
        
        speedList.clear()
        distanceList.clear()
        caloriesList.clear()
        dateLabels.clear()
        
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        
        val sortedActivities = activities.sortedBy { it.timestamp }

        for (activity in sortedActivities) {
            totalRuns++
            dateLabels.add(if (activity.timestamp > 0) sdf.format(Date(activity.timestamp)) else "-")

            var runDist = 0.0
            var runCal = 0
            var runAvgSpeed = 0.0
            
            val runStats = activity.stats
            val statsCount = runStats.size
            
            for (stat in runStats) {
                runDist += stat.distance
                runCal += stat.calories
                runAvgSpeed += stat.speed
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
        tvChartTitle.text = "Speed Progress"
        updateButtonColors(btnSpeed)
        chartView.setData(speedList, dateLabels, "km/h")
    }

    private fun updateUI(dist: Double, runs: Int, cal: Int) {
        if (!isAdded) return
        
        tvTotalDistance.text = String.format(Locale.getDefault(), "%.2f km", dist)
        tvTotalRuns.text = runs.toString()
        tvTotalCalories.text = "$cal kcal"
    }
}
