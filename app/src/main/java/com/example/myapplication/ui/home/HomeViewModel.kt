package com.example.myapplication.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WeatherResponse
import com.example.myapplication.domain.home.ActivityRepository
import com.example.myapplication.domain.home.WeatherRepository
import com.example.myapplication.domain.home.PastRunData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val weatherRepository: WeatherRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _weatherFlow = MutableStateFlow<WeatherData?>(null)
    private var lastUserId: String? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    fun loadData(userId: String, lat: Double, lon: Double) {
        if (lastUserId == userId && lastLat == lat && lastLon == lon && _uiState.value !is HomeUiState.Error) return
        
        lastUserId = userId
        lastLat = lat
        lastLon = lon
        
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            
            // 1. Avvia il recupero del meteo in background
            launch {
                try {
                    val weather = weatherRepository.getWeatherForecast(lat, lon)
                    val (weatherDesc, isBadWeather) = mapWeatherCode(weather.current.weather_code)
                    val advice = getRunningAdvice(weather.current.temperature_2m, isBadWeather)
                    _weatherFlow.value = WeatherData(weather, weatherDesc, advice)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Weather fetch failed", e)
                }
            }

            // 2. Combina le attività con il meteo (che può essere null inizialmente)
            activityRepository.getRecentActivities(userId)
                .combine(_weatherFlow) { activities, weather ->
                    if (weather != null) {
                        HomeUiState.Success(weather.response, activities, weather.description, weather.advice)
                    } else {
                        HomeUiState.PartialSuccess(activities)
                    }
                }
                .catch { e ->
                    Log.e("HomeViewModel", "Data flow error", e)
                    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    fun retryConnection() {
        val uid = lastUserId
        val lat = lastLat
        val lon = lastLon
        if (uid != null && lat != null && lon != null) {
            loadData(uid, lat, lon)
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, Boolean> {
        return when (code) {
            0 -> "Clear sky" to false
            in 1..3 -> "Partly cloudy" to false
            in 45..48 -> "Fog" to false
            in 51..67 -> "Rain" to true
            else -> "Variable" to true
        }
    }

    private fun getRunningAdvice(temp: Double, isBadWeather: Boolean): String {
        return if (isBadWeather) "Weather is not ideal. Indoor training? 🏠" 
        else if (temp in 10.0..22.0) "Perfect weather for running! 🏃‍♂️✨" 
        else "Decent weather for running. Have a good workout! 👍"
    }

    private data class WeatherData(val response: WeatherResponse, val description: String, val advice: String)
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val weather: WeatherResponse, 
        val activities: List<PastRunData>,
        val weatherDescription: String,
        val runningAdvice: String
    ) : HomeUiState()
    data class PartialSuccess(val activities: List<PastRunData>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
