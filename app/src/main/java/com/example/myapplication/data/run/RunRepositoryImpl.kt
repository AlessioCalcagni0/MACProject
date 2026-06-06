package com.example.myapplication.data.run

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.myapplication.data.ApiService
import com.example.myapplication.domain.run.RunRepository
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.*

class RunRepositoryImpl(
    private val apiService: ApiService,
    private val runDao: RunDao,
    private val context: Context
) : RunRepository {
    private val database = FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference
    private val gson = Gson()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun startRun(userId: String): String? = withContext(Dispatchers.IO) {
        val localId = UUID.randomUUID().toString()
        Log.d("PacemateSync", "startRun: Locale $localId")
        
        val localRun = LocalRunEntity(
            runId = localId,
            userId = userId,
            startTime = System.currentTimeMillis(),
            isSynced = false
        )
        runDao.insertRun(localRun)

        repositoryScope.launch {
            try {
                val response = apiService.startRun(userId)
                val updatedRun = runDao.getRunById(localId) ?: localRun
                updatedRun.remoteRunId = response.run_id
                updatedRun.isSynced = true
                runDao.insertRun(updatedRun)
                Log.d("PacemateSync", "startRun background: sync success $localId")
            } catch (e: Exception) {
                Log.w("PacemateSync", "startRun background: offline, will sync later")
                scheduleSync()
            }
        }
        localId
    }

    override suspend fun sendLocation(runId: String, lat: Double, lng: Double) {
        repositoryScope.launch {
            val location = LocalLocationEntity(
                runId = runId, 
                latitude = lat, 
                longitude = lng, 
                timestamp = System.currentTimeMillis(),
                isSynced = false
            )
            runDao.insertLocation(location)

            val localRun = runDao.getRunById(runId)
            if (localRun != null && localRun.isSynced && localRun.remoteRunId != null) {
                try {
                    apiService.sendLocation(localRun.remoteRunId!!, lat, lng)
                    location.isSynced = true
                    runDao.updateLocations(listOf(location))
                } catch (e: Exception) {
                    scheduleSync()
                }
            }
        }
    }

    override suspend fun endRun(
        runId: String, userId: String, distance: Double, speed: Double, calories: Int,
        isGoalAchieved: Boolean, seconds: Int, stepCount: Int, goalValueText: String,
        capturedPhotos: List<String>, stats: List<ParticipantLiveStats>
    ) {
        // Usiamo withContext per garantire l'ordine ma senza bloccare il chiamante a lungo
        withContext(Dispatchers.IO) {
            val localRun = runDao.getRunById(runId) ?: LocalRunEntity(
                runId = runId,
                userId = userId,
                startTime = System.currentTimeMillis()
            )

            localRun.apply {
                this.endTime = System.currentTimeMillis()
                this.distance = distance
                this.speed = speed
                this.calories = calories
                this.isGoalAchieved = isGoalAchieved
                this.seconds = seconds
                this.stepCount = stepCount
                this.goalValueText = goalValueText
                this.photosJson = gson.toJson(capturedPhotos)
                this.statsJson = gson.toJson(stats)
                this.isHistorySynced = false
            }
            runDao.insertRun(localRun)

            repositoryScope.launch {
                try {
                    saveRunToHistory(runId, userId, distance, speed, calories, isGoalAchieved, seconds, stepCount, goalValueText, capturedPhotos, stats)
                } catch (e: Exception) {
                    Log.e("PacemateSync", "History save deferred")
                }
                scheduleSync()
            }
        }
    }

    override suspend fun saveRunToHistory(
        runId: String, userId: String, distance: Double, speed: Double, calories: Int,
        isGoalAchieved: Boolean, seconds: Int, stepCount: Int, goalValueText: String,
        capturedPhotos: List<String>, stats: List<ParticipantLiveStats>
    ): String? = withContext(Dispatchers.IO) {
        val historyRef = database.child("users").child(userId).child("recent_activities").child(runId)
        val historyData = mapOf(
            "id" to runId,
            "groupName" to "Single Run",
            "type" to "single",
            "timestamp" to System.currentTimeMillis(),
            "durationSeconds" to seconds,
            "stats" to stats,
            "photos" to capturedPhotos,
            "participants" to listOf(userId),
            "goalAchieved" to isGoalAchieved,
            "goalTarget" to goalValueText,
            "steps" to stepCount,
            "coverPhoto" to (capturedPhotos.firstOrNull() ?: "")
        )
        try {
            historyRef.setValue(historyData)
            runId
        } catch (e: Exception) {
            scheduleSync()
            null
        }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "run_sync_work", 
            ExistingWorkPolicy.REPLACE, 
            syncRequest
        )
    }
}
