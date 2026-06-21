package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    // Scripts queries
    @Query("SELECT * FROM python_scripts ORDER BY name ASC")
    fun getAllScriptsFlow(): Flow<List<PythonScript>>

    @Query("SELECT * FROM python_scripts WHERE id = :id")
    suspend fun getScriptById(id: Int): PythonScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: PythonScript): Long

    @Update
    suspend fun updateScript(script: PythonScript)

    @Delete
    suspend fun deleteScript(script: PythonScript)

    // Script Runs queries
    @Query("SELECT * FROM script_runs ORDER BY startTime DESC")
    fun getAllRunsFlow(): Flow<List<ScriptRun>>

    @Query("SELECT * FROM script_runs WHERE id = :id")
    suspend fun getRunById(id: Int): ScriptRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: ScriptRun): Long

    @Update
    suspend fun updateRun(run: ScriptRun)

    @Query("UPDATE script_runs SET logs = :logs WHERE id = :id")
    suspend fun updateRunLogs(id: Int, logs: String)

    @Query("DELETE FROM script_runs WHERE id = :id")
    suspend fun deleteRunById(id: Int)

    @Query("DELETE FROM script_runs")
    suspend fun deleteAllRuns()
}
