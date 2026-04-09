package com.example.myapplication.domain.home

import com.example.myapplication.data.WeatherResponse

interface WeatherRepository {
    suspend fun getWeatherForecast(lat: Double, lon: Double): WeatherResponse
}
