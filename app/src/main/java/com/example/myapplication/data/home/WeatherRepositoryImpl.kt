package com.example.myapplication.data.home

import com.example.myapplication.data.WeatherApiService
import com.example.myapplication.data.WeatherResponse
import com.example.myapplication.domain.home.WeatherRepository

class WeatherRepositoryImpl(private val weatherApiService: WeatherApiService) : WeatherRepository {
    override suspend fun getWeatherForecast(lat: Double, lon: Double): WeatherResponse {
        return weatherApiService.getForecast(lat, lon)
    }
}
