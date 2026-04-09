package com.example.myapplication.domain.home

import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun getRecentActivities(userId: String): Flow<List<PastRunData>>
}
