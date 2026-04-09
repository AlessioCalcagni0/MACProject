package com.example.myapplication.domain.stats

import com.example.myapplication.domain.home.PastRunData
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun getRecentActivities(userId: String): Flow<List<PastRunData>>
}
