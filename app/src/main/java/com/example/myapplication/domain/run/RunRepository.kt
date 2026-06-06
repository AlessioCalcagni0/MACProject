package com.example.myapplication.domain.run

import com.example.myapplication.ui.social.ParticipantLiveStats

interface RunRepository {
    suspend fun startRun(userId: String): String?
    suspend fun sendLocation(runId: String, lat: Double, lng: Double)
    suspend fun endRun(
        runId: String,
        userId: String,
        distance: Double,
        speed: Double,
        calories: Int,
        isGoalAchieved: Boolean,
        seconds: Int,
        stepCount: Int,
        goalValueText: String,
        capturedPhotos: List<String>,
        stats: List<ParticipantLiveStats>
    )
    suspend fun saveRunToHistory(
        runId: String,
        userId: String,
        distance: Double,
        speed: Double,
        calories: Int,
        isGoalAchieved: Boolean,
        seconds: Int,
        stepCount: Int,
        goalValueText: String,
        capturedPhotos: List<String>,
        stats: List<ParticipantLiveStats>
    ): String?
}
