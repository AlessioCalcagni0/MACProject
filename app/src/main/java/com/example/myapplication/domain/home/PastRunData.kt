package com.example.myapplication.domain.home

import com.example.myapplication.ui.social.ParticipantLiveStats

data class PastRunData(
    val id: String,
    val name: String,
    val timestamp: Long,
    val stats: List<ParticipantLiveStats>,
    val photos: List<String>,
    val type: String,
    val coverPhoto: String?,
    val duration: Int,
    val participantNames: Map<String, String> = emptyMap() // MAPPA DEI NOMI AGGIUNTA
)
