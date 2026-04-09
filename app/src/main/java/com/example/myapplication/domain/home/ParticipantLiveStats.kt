package com.example.myapplication.domain.home

import java.io.Serializable

data class ParticipantLiveStats(
    val userId: String = "",
    val speed: Double = 0.0,
    val calories: Int = 0,
    val distance: Double = 0.0
) : Serializable
