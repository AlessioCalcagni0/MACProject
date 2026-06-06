package com.example.myapplication.data.run

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_runs")
data class LocalRunEntity(
    @PrimaryKey val runId: String, // ID Locale (UUID)
    val userId: String,
    val startTime: Long,
    var endTime: Long? = null,
    var distance: Double = 0.0,
    var speed: Double = 0.0,
    var calories: Int = 0,
    var isGoalAchieved: Boolean = false,
    var seconds: Int = 0,
    var stepCount: Int = 0,
    var goalValueText: String = "",
    var isSynced: Boolean = false, // True se startRun è stato chiamato con successo
    var isHistorySynced: Boolean = false, // True se endRun e Firebase sono completati
    var photosJson: String? = null,
    var statsJson: String? = null,
    var remoteRunId: String? = null // ID restituito dal backend Cloud Run
)

@Entity(tableName = "local_locations")
data class LocalLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    var isSynced: Boolean = false
)
