package com.example.myapplication.ui.run

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.run.RunRepositoryImpl

class RunViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val runRepo = RunRepositoryImpl(
                RetrofitClient.api,
                database.runDao(),
                context.applicationContext
            )
            @Suppress("UNCHECKED_CAST")
            return RunViewModel(runRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
