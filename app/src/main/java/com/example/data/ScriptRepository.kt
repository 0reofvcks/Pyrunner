package com.example.data

import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val scriptDao: ScriptDao) {
    val allScripts: Flow<List<PythonScript>> = scriptDao.getAllScriptsFlow()
    val allRuns: Flow<List<ScriptRun>> = scriptDao.getAllRunsFlow()

    suspend fun getScriptById(id: Int): PythonScript? {
        return scriptDao.getScriptById(id)
    }

    suspend fun insertScript(script: PythonScript): Long {
        return scriptDao.insertScript(script)
    }

    suspend fun updateScript(script: PythonScript) {
        scriptDao.updateScript(script)
    }

    suspend fun deleteScript(script: PythonScript) {
        scriptDao.deleteScript(script)
    }

    suspend fun getRunById(id: Int): ScriptRun? {
        return scriptDao.getRunById(id)
    }

    suspend fun insertRun(run: ScriptRun): Long {
        return scriptDao.insertRun(run)
    }

    suspend fun updateRun(run: ScriptRun) {
        scriptDao.updateRun(run)
    }

    suspend fun updateRunLogs(id: Int, logs: String) {
        scriptDao.updateRunLogs(id, logs)
    }

    suspend fun deleteRunById(id: Int) {
        scriptDao.deleteRunById(id)
    }

    suspend fun deleteAllRuns() {
        scriptDao.deleteAllRuns()
    }
}
