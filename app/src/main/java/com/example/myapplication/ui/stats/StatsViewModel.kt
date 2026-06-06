package com.example.myapplication.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.stats.StatsRepository
import com.example.myapplication.domain.home.PastRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class StatsViewModel(private val statsRepository: StatsRepository) : ViewModel() {

    private val _activities = MutableStateFlow<List<PastRunData>>(emptyList())
    val activities: StateFlow<List<PastRunData>> = _activities

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var lastUserId: String? = null

    fun loadActivities(userId: String) {
        lastUserId = userId
        viewModelScope.launch {
            _error.value = null
            statsRepository.getRecentActivities(userId)
                .catch { e ->
                    _error.value = e.message ?: "Error loading activities"
                }
                .collect { list ->
                    _activities.value = list
                }
        }
    }

    fun retryConnection() {
        val uid = lastUserId
        if (uid != null && _error.value != null) {
            loadActivities(uid)
        }
    }
}
