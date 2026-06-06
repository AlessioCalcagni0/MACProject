package com.example.myapplication.data.run

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.RetrofitClient
import com.example.myapplication.ui.social.ParticipantLiveStats
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()

    private val database =
        FirebaseDatabase.getInstance("https://maccproject-9de7e-default-rtdb.europe-west1.firebasedatabase.app").reference

    override suspend fun doWork(): Result {
        Log.d("PacemateSync", "SyncWorker: Avvio sincronizzazione in background")

        val db = AppDatabase.getDatabase(applicationContext)
        val runDao = db.runDao()
        val apiService = RetrofitClient.api

        val unsyncedRuns = runDao.getUnsyncedRuns()

        if (unsyncedRuns.isEmpty()) {
            Log.d("PacemateSync", "SyncWorker: Nessuna corsa da sincronizzare")
            return Result.success()
        }

        var hasFailures = false

        for (run in unsyncedRuns) {
            try {
                Log.d("PacemateSync", "SyncWorker: Elaborazione corsa ${run.runId}")

                // 1. Sincronizza startRun se necessario
                if (!run.isSynced) {
                    try {
                        val response = apiService.startRun(run.userId)

                        run.remoteRunId = response.run_id
                        run.isSynced = true

                        runDao.insertRun(run)

                        Log.d(
                            "PacemateSync",
                            "SyncWorker: Inizio corsa registrato online per ${run.runId}"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "PacemateSync",
                            "SyncWorker: Errore registrazione inizio corsa ${run.runId}",
                            e
                        )

                        hasFailures = true
                        continue
                    }
                }

                val remoteId = run.remoteRunId

                if (remoteId.isNullOrBlank()) {
                    Log.e(
                        "PacemateSync",
                        "SyncWorker: remoteRunId mancante per ${run.runId}"
                    )

                    hasFailures = true
                    continue
                }

                // 2. Sincronizza posizioni
                val locations = runDao.getUnsyncedLocations(run.runId)

                if (locations.isNotEmpty()) {
                    for (loc in locations) {
                        try {
                            apiService.sendLocation(
                                runId = remoteId,
                                lat = loc.latitude,
                                lng = loc.longitude
                            )

                            loc.isSynced = true
                            runDao.updateLocations(listOf(loc))
                        } catch (e: Exception) {
                            Log.e(
                                "PacemateSync",
                                "SyncWorker: Errore sync posizione per ${run.runId}",
                                e
                            )

                            hasFailures = true
                        }
                    }
                }

                // 3. Upload foto locali, incluso snapshot mappa
                val originalPhotos: List<String> = run.photosJson?.let {
                    gson.fromJson(it, object : TypeToken<List<String>>() {}.type)
                } ?: emptyList()

                val updatedPhotos = originalPhotos.map { path ->
                    uploadPhotoIfLocal(
                        path = path,
                        userId = run.userId
                    ) ?: path
                }

                if (updatedPhotos != originalPhotos) {
                    run.photosJson = gson.toJson(updatedPhotos)
                    runDao.insertRun(run)
                }

                // 4. Sincronizza fine corsa + Firebase recent_activities
                if (run.endTime != null && !run.isHistorySynced) {
                    var backendSuccess = false
                    var firebaseSuccess = false

                    try {
                        apiService.endRun(remoteId, run.distance)
                        backendSuccess = true
                    } catch (e: Exception) {
                        Log.e(
                            "PacemateSync",
                            "SyncWorker: Errore endRun backend per ${run.runId}",
                            e
                        )

                        hasFailures = true
                    }

                    try {
                        val stats: List<ParticipantLiveStats> = run.statsJson?.let {
                            gson.fromJson(
                                it,
                                object : TypeToken<List<ParticipantLiveStats>>() {}.type
                            )
                        } ?: emptyList()

                        val historyRef = database
                            .child("users")
                            .child(run.userId)
                            .child("recent_activities")
                            .child(run.runId)

                        val historyData = mapOf(
                            "id" to run.runId,
                            "groupName" to "Single Run",
                            "type" to "single",
                            "timestamp" to run.startTime,
                            "durationSeconds" to run.seconds,
                            "stats" to stats,
                            "photos" to updatedPhotos,
                            "participants" to listOf(run.userId),
                            "goalAchieved" to run.isGoalAchieved,
                            "goalTarget" to run.goalValueText,
                            "steps" to run.stepCount,
                            "coverPhoto" to (updatedPhotos.firstOrNull() ?: "")
                        )

                        historyRef.setValue(historyData).await()
                        firebaseSuccess = true
                    } catch (e: Exception) {
                        Log.e(
                            "PacemateSync",
                            "SyncWorker: Errore Firebase recent_activities per ${run.runId}",
                            e
                        )

                        hasFailures = true
                    }

                    // Cancella dalla cache SOLO se tutto è online.
                    if (backendSuccess && firebaseSuccess) {
                        run.isSynced = true
                        run.isHistorySynced = true
                        runDao.insertRun(run)

                        runDao.deleteLocationsForRun(run.runId)
                        runDao.deleteRunById(run.runId)

                        Log.d(
                            "PacemateSync",
                            "SyncWorker: Corsa ${run.runId} sincronizzata e rimossa dalla cache locale"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "PacemateSync",
                    "SyncWorker: Errore generico corsa ${run.runId}",
                    e
                )

                hasFailures = true
            }
        }

        return if (hasFailures) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun uploadPhotoIfLocal(
        path: String,
        userId: String
    ): String? {
        if (!path.startsWith("/")) return path

        val file = File(path)

        if (!file.exists()) return path

        return try {
            val storageRef = FirebaseStorage
                .getInstance()
                .reference
                .child("runs")
                .child(userId)
                .child(file.name)

            storageRef.putFile(Uri.fromFile(file)).await()

            val downloadUrl = storageRef.downloadUrl.await().toString()

            Log.d(
                "PacemateSync",
                "SyncWorker: Foto locale caricata su Firebase Storage: $downloadUrl"
            )

            downloadUrl
        } catch (e: Exception) {
            Log.e(
                "PacemateSync",
                "SyncWorker: Errore upload foto locale $path",
                e
            )

            null
        }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "run_sync_work",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}