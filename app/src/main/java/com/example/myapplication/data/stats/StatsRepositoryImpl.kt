package com.example.myapplication.data.stats

import com.example.myapplication.data.run.RunDao
import com.example.myapplication.data.run.LocalRunEntity
import com.example.myapplication.domain.stats.StatsRepository
import com.example.myapplication.domain.home.PastRunData
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class StatsRepositoryImpl(private val runDao: RunDao) : StatsRepository {
    private val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
    private val gson = Gson()

    override fun getRecentActivities(userId: String): Flow<List<PastRunData>> {
        val firebaseFlow = callbackFlow<List<PastRunData>> {
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
                        
                        val participantNames = mutableMapOf<String, String>()
                        child.child("participantNames").children.forEach { n ->
                            n.key?.let { participantNames[it] = n.value?.toString() ?: "" }
                        }
                        
                        list.add(PastRunData(runId, name, time, stats, photos, type, coverPhoto, duration, participantNames))
                    }
                    trySend(list)
                }
                override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            }
            val ref = database.child("users").child(userId).child("recent_activities")
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }.onStart { emit(emptyList<PastRunData>()) }

        val localFlow: Flow<List<PastRunData>> = runDao.getCompletedUnsyncedRunsFlow().map { entities: List<LocalRunEntity> ->
            entities.map { run: LocalRunEntity ->
                val photos: List<String> = run.photosJson?.let {
                    gson.fromJson(it, object : TypeToken<List<String>>() {}.type)
                } ?: emptyList()
                
                val stats: List<ParticipantLiveStats> = run.statsJson?.let {
                    gson.fromJson(it, object : TypeToken<List<ParticipantLiveStats>>() {}.type)
                } ?: listOf(ParticipantLiveStats(run.userId, run.speed, run.calories, run.distance))
                
                PastRunData(
                    id = run.runId,
                    name = "Run (Offline)",
                    timestamp = run.startTime,
                    stats = stats,
                    photos = photos,
                    type = "single",
                    coverPhoto = photos.firstOrNull(),
                    duration = run.seconds
                )
            }
        }

        return combine(firebaseFlow, localFlow) { firebaseList, localList ->
            val localOnly = localList.filter { local -> firebaseList.none { it.id == local.id } }
            (localOnly + firebaseList).sortedByDescending { it.timestamp }
        }
    }
}
