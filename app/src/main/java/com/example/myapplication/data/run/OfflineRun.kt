package com.example.myapplication.data.run

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_runs")
data class OfflineRun(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val distance: Double,
    val speed: Double,
    val calories: Int,
    val isGoalAchieved: Boolean,
    val seconds: Int,
    val stepCount: Int,
    val goalValueText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
