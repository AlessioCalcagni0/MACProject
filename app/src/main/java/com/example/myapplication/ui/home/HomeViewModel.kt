package com.example.myapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WeatherResponse
import com.example.myapplication.domain.home.ActivityRepository
import com.example.myapplication.domain.home.WeatherRepository
import com.example.myapplication.domain.home.PastRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HomeViewModel(
    private val weatherRepository: WeatherRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    fun loadData(userId: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                // Fetch weather
                val weather = weatherRepository.getWeatherForecast(lat, lon)
                val (weatherDesc, isBadWeather) = mapWeatherCode(weather.current.weather_code)
                val advice = getRunningAdvice(weather.current.temperature_2m, isBadWeather)
                
                // Observe recent activities
                activityRepository.getRecentActivities(userId)
                    .catch { e ->
                        _uiState.value = HomeUiState.Error(e.message ?: "Error loading activities")
                    }
                    .collect { activities ->
                        _uiState.value = HomeUiState.Success(weather, activities, weatherDesc, advice)
                    }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
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
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val weather: WeatherResponse, 
        val activities: List<PastRunData>,
        val weatherDescription: String,
        val runningAdvice: String
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
