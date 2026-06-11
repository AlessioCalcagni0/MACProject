package com.example.myapplication.data.home

import com.example.myapplication.data.run.LocalRunEntity
import com.example.myapplication.data.run.RunDao
import com.example.myapplication.domain.home.ActivityRepository
import com.example.myapplication.domain.home.PastRunData
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ActivityRepositoryImpl(
    private val runDao: RunDao
) : ActivityRepository {

    private val database =
        FirebaseDatabase.getInstance("https://maccproject-7f15c-default-rtdb.europe-west1.firebasedatabase.app").reference

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
                        val duration =
                            (child.child("durationSeconds").value as? Number)?.toInt() ?: 0

                        val stats = mutableListOf<ParticipantLiveStats>()
                        child.child("stats").children.forEach { s ->
                            val uId = s.child("userId").value?.toString() ?: ""
                            val speed = (s.child("speed").value as? Number)?.toDouble() ?: 0.0
                            val calories = (s.child("calories").value as? Number)?.toInt() ?: 0
                            val dist = (s.child("distance").value as? Number)?.toDouble() ?: 0.0

                            stats.add(
                                ParticipantLiveStats(
                                    userId = uId,
                                    speed = speed,
                                    calories = calories,
                                    distance = dist
                                )
                            )
                        }

                        val photos = mutableListOf<String>()
                        child.child("photos").children.forEach { p ->
                            p.getValue(String::class.java)?.let { photos.add(it) }
                        }

                        val participantNames = mutableMapOf<String, String>()
                        child.child("participantNames").children.forEach { n ->
                            n.key?.let { key ->
                                participantNames[key] = n.value?.toString() ?: ""
                            }
                        }

                        list.add(
                            PastRunData(
                                id = runId,
                                name = name,
                                timestamp = time,
                                stats = stats,
                                photos = photos,
                                type = type,
                                coverPhoto = coverPhoto,
                                duration = duration,
                                participantNames = participantNames
                            )
                        )
                    }

                    trySend(list)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }

            val ref = database
                .child("users")
                .child(userId)
                .child("recent_activities")
                .limitToLast(20)

            ref.addValueEventListener(listener)

            awaitClose {
                ref.removeEventListener(listener)
            }
        }.onStart {
            emit(emptyList())
        }

        // IMPORTANTISSIMO:
        // la cache locale viene letta solo per l'utente loggato.
        // Così un account nuovo non vede le corse di un altro account.
        val localFlow: Flow<List<PastRunData>> =
            runDao.getCompletedRunsFlow(userId).map { entities ->
                entities.map { run ->
                    run.toPastRunData()
                }
            }

        return combine(firebaseFlow, localFlow) { firebaseList, localList ->
            val localOnly = localList.filter { local ->
                firebaseList.none { firebase ->
                    firebase.id == local.id
                }
            }

            (localOnly + firebaseList).sortedByDescending { it.timestamp }
        }
    }

    private fun LocalRunEntity.toPastRunData(): PastRunData {
        val photos: List<String> = photosJson?.let {
            gson.fromJson(it, object : TypeToken<List<String>>() {}.type)
        } ?: emptyList()

        return PastRunData(
            id = runId,
            name = if (isHistorySynced) "Single Run" else "Run (Offline)",
            timestamp = startTime,
            stats = listOf(
                ParticipantLiveStats(
                    userId = userId,
                    speed = speed,
                    calories = calories,
                    distance = distance
                )
            ),
            photos = photos,
            type = "single",
            coverPhoto = photos.firstOrNull(),
            duration = seconds,
            participantNames = emptyMap()
        )
    }
}