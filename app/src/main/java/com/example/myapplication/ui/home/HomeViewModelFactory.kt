package com.example.myapplication.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.home.ActivityRepositoryImpl
import com.example.myapplication.data.home.WeatherRepositoryImpl
import com.example.myapplication.data.home.WeatherRetrofitClient

class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val weatherRepo = WeatherRepositoryImpl(WeatherRetrofitClient.api)
            val database = AppDatabase.getDatabase(context)
            val activityRepo = ActivityRepositoryImpl(database.runDao())
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(weatherRepo, activityRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
