package com.example.myapplication.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.stats.StatsRepositoryImpl

class StatsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val statsRepo = StatsRepositoryImpl(database.runDao())
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(statsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
