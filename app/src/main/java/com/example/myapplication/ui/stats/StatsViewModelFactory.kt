package com.example.myapplication.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.stats.StatsRepositoryImpl

class StatsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            val statsRepo = StatsRepositoryImpl()
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(statsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
