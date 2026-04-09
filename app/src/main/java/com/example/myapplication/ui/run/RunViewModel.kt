package com.example.myapplication.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.run.RunRepository
import com.example.myapplication.ui.social.ParticipantLiveStats
import kotlinx.coroutines.launch

class RunViewModel(private val runRepository: RunRepository) : ViewModel() {

    fun startRun(userId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val runId = runRepository.startRun(userId)
            onResult(runId)
        }
    }

    fun sendLocation(runId: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            runRepository.sendLocation(runId, lat, lng)
        }
    }

    fun endRun(runId: String, distance: Double) {
        viewModelScope.launch {
            runRepository.endRun(runId, distance)
        }
    }

    fun saveRunToHistory(
        userId: String,
        distance: Double,
        speed: Double,
        calories: Int,
        isGoalAchieved: Boolean,
        seconds: Int,
        stepCount: Int,
        goalValueText: String,
        capturedPhotos: List<String>,
        stats: List<ParticipantLiveStats>,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val key = runRepository.saveRunToHistory(
                userId, distance, speed, calories, isGoalAchieved,
                seconds, stepCount, goalValueText, capturedPhotos, stats
            )
            onResult(key)
        }
    }
}
