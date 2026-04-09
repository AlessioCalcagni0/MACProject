package com.example.myapplication.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.data.social.SocialRepositoryImpl
import com.example.myapplication.domain.social.SocialRepository

class SocialViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            val repository = SocialRepositoryImpl(RetrofitClient.api)
            @Suppress("UNCHECKED_CAST")
            return SocialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
