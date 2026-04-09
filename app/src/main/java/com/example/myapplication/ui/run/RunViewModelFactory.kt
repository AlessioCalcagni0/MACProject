package com.example.myapplication.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.run.RunRepositoryImpl

class RunViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunViewModel::class.java)) {
            val runRepo = RunRepositoryImpl(RetrofitClient.api)
            @Suppress("UNCHECKED_CAST")
            return RunViewModel(runRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
