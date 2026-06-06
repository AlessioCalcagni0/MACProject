package com.example.myapplication.data.run

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: LocalRunEntity): Long

    @Update
    suspend fun updateRun(run: LocalRunEntity): Int

    @Query("SELECT * FROM local_runs WHERE runId = :runId")
    suspend fun getRunById(runId: String): LocalRunEntity?

    @Query("SELECT * FROM local_runs WHERE isSynced = 0 OR isHistorySynced = 0")
    suspend fun getUnsyncedRuns(): List<LocalRunEntity>

    @Query("SELECT * FROM local_runs WHERE (isSynced = 0 OR isHistorySynced = 0) AND endTime IS NOT NULL")
    suspend fun getCompletedUnsyncedRuns(): List<LocalRunEntity>

    @Query("SELECT * FROM local_runs WHERE (isSynced = 0 OR isHistorySynced = 0) AND endTime IS NOT NULL")
    fun getCompletedUnsyncedRunsFlow(): Flow<List<LocalRunEntity>>

    @Query("SELECT * FROM local_runs WHERE isSynced = 0 OR isHistorySynced = 0")
    fun getUnsyncedRunsFlow(): Flow<List<LocalRunEntity>>

    // Cache locale filtrata per account
    @Query("SELECT * FROM local_runs WHERE userId = :userId AND endTime IS NOT NULL ORDER BY startTime DESC")
    fun getCompletedRunsFlow(userId: String): Flow<List<LocalRunEntity>>

    @Query("SELECT * FROM local_runs WHERE userId = :userId AND endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getCompletedRuns(userId: String): List<LocalRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocalLocationEntity): Long

    @Query("SELECT * FROM local_locations WHERE runId = :runId AND isSynced = 0")
    suspend fun getUnsyncedLocations(runId: String): List<LocalLocationEntity>

    @Update
    suspend fun updateLocations(locations: List<LocalLocationEntity>): Int

    // Elimina una corsa specifica dalla cache
    @Query("DELETE FROM local_runs WHERE runId = :runId")
    suspend fun deleteRunById(runId: String): Int

    // Elimina le posizioni di una corsa specifica
    @Query("DELETE FROM local_locations WHERE runId = :runId")
    suspend fun deleteLocationsForRun(runId: String): Int

    // Pulizia delle corse sincronizzate di uno specifico account
    @Query("DELETE FROM local_runs WHERE userId = :userId AND isSynced = 1 AND isHistorySynced = 1")
    suspend fun deleteSyncedRunsForUser(userId: String): Int

    // Vecchia funzione: lasciala pure, ma non usarla nella Home
    @Query("DELETE FROM local_runs WHERE isSynced = 1 AND isHistorySynced = 1")
    suspend fun deleteSyncedRuns(): Int
}