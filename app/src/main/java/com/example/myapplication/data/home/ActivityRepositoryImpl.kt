package com.example.myapplication.data.home

import com.example.myapplication.domain.home.ActivityRepository
import com.example.myapplication.domain.home.PastRunData
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ActivityRepositoryImpl : ActivityRepository {
    private val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference

    override fun getRecentActivities(userId: String): Flow<List<PastRunData>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PastRunData>()
                for (child in snapshot.children) {
                    val time = child.child("timestamp").value as? Long ?: 0L
                    if (time <= 0L) continue

                    val runId = child.key ?: ""
                    val name = child.child("groupName").value?.toString() ?: "Run"
                    val type = child.child("type").value?.toString() ?: "group"
                    val coverPhoto = child.child("coverPhoto").value?.toString()
                    val duration = (child.child("durationSeconds").value as? Number)?.toInt() ?: 0
                    
                    val stats = mutableListOf<ParticipantLiveStats>()
                    child.child("stats").children.forEach { s ->
                        val uId = s.child("userId").value?.toString() ?: ""
                        val speed = (s.child("speed").value as? Number)?.toDouble() ?: 0.0
                        val calories = (s.child("calories").value as? Number)?.toInt() ?: 0
                        val dist = (s.child("distance").value as? Number)?.toDouble() ?: 0.0
                        stats.add(ParticipantLiveStats(uId, speed, calories, dist))
                    }
                    
                    val photos = mutableListOf<String>()
                    child.child("photos").children.forEach { p ->
                        p.getValue(String::class.java)?.let { photos.add(it) }
                    }

                    // RECUPERO MAPPA DEI NOMI SALVATA
                    val participantNames = mutableMapOf<String, String>()
                    child.child("participantNames").children.forEach { n ->
                        val id = n.key ?: ""
                        val actualName = n.value?.toString() ?: ""
                        if (id.isNotBlank() && actualName.isNotBlank()) {
                            participantNames[id] = actualName
                        }
                    }
                    
                    list.add(PastRunData(runId, name, time, stats, photos, type, coverPhoto, duration, participantNames))
                }
                trySend(list.reversed())
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = database.child("users").child(userId).child("recent_activities").limitToLast(15)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
