package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "python_scripts")
data class PythonScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "script_runs")
data class ScriptRun(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scriptId: Int,
    val scriptName: String,
    val status: String, // "PENDING", "RUNNING", "COMPLETED", "FAILED", "KILLED"
    val logs: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val exitCode: Int? = null
) : Serializable
