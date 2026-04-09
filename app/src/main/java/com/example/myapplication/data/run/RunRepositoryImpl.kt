package com.example.myapplication.data.run

import com.example.myapplication.data.ApiService
import com.example.myapplication.domain.run.RunRepository
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class RunRepositoryImpl(private val apiService: ApiService) : RunRepository {
    private val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference

    override suspend fun startRun(userId: String): String? {
        return try {
            apiService.startRun(userId).run_id
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun sendLocation(runId: String, lat: Double, lng: Double) {
        try {
            apiService.sendLocation(runId, lat, lng)
        } catch (e: Exception) { }
    }

    override suspend fun endRun(runId: String, distance: Double) {
        try {
            apiService.endRun(runId, distance)
        } catch (e: Exception) { }
    }

    override suspend fun saveRunToHistory(
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
    ): String? {
        val historyRef = database.child("users").child(userId).child("recent_activities").push()
        val key = historyRef.key
        val historyData = mapOf(
            "id" to key,
            "groupName" to "Single Run",
            "type" to "single",
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to seconds,
            "stats" to stats,
            "photos" to capturedPhotos,
            "participants" to listOf(userId),
            "goalAchieved" to isGoalAchieved,
            "goalTarget" to goalValueText,
            "steps" to stepCount,
            "coverPhoto" to (capturedPhotos.lastOrNull() ?: "")
        )
        return try {
            historyRef.setValue(historyData).await()
            key
        } catch (e: Exception) {
            null
        }
    }
}
