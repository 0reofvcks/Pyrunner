package com.example.python

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.data.ScriptDatabase
import com.example.data.ScriptRepository
import com.example.data.ScriptRun
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PythonService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineContextScope(Dispatchers.IO + serviceJob)
    
    private val activeProcesses = ConcurrentHashMap<Int, Process>()
    private lateinit var repository: ScriptRepository

    companion object {
        private const val TAG = "PythonService"
        
        const val ACTION_START_SCRIPT = "com.example.python.action.START_SCRIPT"
        const val ACTION_KILL_SCRIPT = "com.example.python.action.KILL_SCRIPT"
        const val ACTION_RUN_PIP = "com.example.python.action.RUN_PIP"
        
        const val EXTRA_SCRIPT_ID = "extra_script_id"
        const val EXTRA_RUN_ID = "extra_run_id"
        const val EXTRA_PIP_ARGS = "extra_pip_args"

        fun startScript(context: Context, scriptId: Int, runId: Int) {
            val intent = Intent(context, PythonService::class.java).apply {
                action = ACTION_START_SCRIPT
                putExtra(EXTRA_SCRIPT_ID, scriptId)
                putExtra(EXTRA_RUN_ID, runId)
            }
            context.startService(intent)
        }

        fun killScript(context: Context, runId: Int) {
            val intent = Intent(context, PythonService::class.java).apply {
                action = ACTION_KILL_SCRIPT
                putExtra(EXTRA_RUN_ID, runId)
            }
            context.startService(intent)
        }

        fun runPip(context: Context, args: String, runId: Int) {
            val intent = Intent(context, PythonService::class.java).apply {
                action = ACTION_RUN_PIP
                putExtra(EXTRA_PIP_ARGS, args)
                putExtra(EXTRA_RUN_ID, runId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = ScriptDatabase.getDatabase(this)
        repository = ScriptRepository(database.scriptDao())
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val runId = intent?.getIntExtra(EXTRA_RUN_ID, -1) ?: -1
        
        if (runId != -1) {
            when (action) {
                ACTION_START_SCRIPT -> {
                    val scriptId = intent.getIntExtra(EXTRA_SCRIPT_ID, -1)
                    if (scriptId != -1) {
                        launchScript(scriptId, runId)
                    }
                }
                ACTION_KILL_SCRIPT -> {
                    killScriptProcess(runId)
                }
                ACTION_RUN_PIP -> {
                    val args = intent.getStringExtra(EXTRA_PIP_ARGS) ?: ""
                    launchPip(args, runId)
                }
            }
        }
        
        return START_NOT_STICKY
    }

    private fun launchScript(scriptId: Int, runId: Int) {
        serviceScope.launch {
            val script = repository.getScriptById(scriptId) ?: return@launch
            val runDb = repository.getRunById(runId) ?: return@launch

            // Create scripts folder if it doesn't exist
            val scriptsDir = File(filesDir, "scripts")
            if (!scriptsDir.exists()) {
                scriptsDir.mkdirs()
            }

            // Write python content to disk in filesDir/scripts/name
            val scriptFile = File(scriptsDir, script.name)
            scriptFile.writeText(script.content)

            executePythonCommand(
                runId = runId,
                command = arrayOf(PythonManager.getPythonBinary(this@PythonService).absolutePath, scriptFile.absolutePath),
                workingDirectory = scriptsDir
            )
        }
    }

    private fun launchPip(argsString: String, runId: Int) {
        serviceScope.launch {
            val runDb = repository.getRunById(runId) ?: return@launch
            
            // split command arguments carefully
            val argsList = mutableListOf<String>()
            argsList.add(PythonManager.getPythonBinary(this@PythonService).absolutePath)
            argsList.add("-m")
            argsList.add("pip")
            
            argsString.split("\\s+".toRegex()).forEach {
                if (it.isNotBlank()) {
                    argsList.add(it)
                }
            }
            
            // Bypass SSL verifications to prevent issues on various Android platforms
            argsList.add("--trusted-host")
            argsList.add("pypi.org")
            argsList.add("--trusted-host")
            argsList.add("files.pythonhosted.org")
            argsList.add("--trusted-host")
            argsList.add("pypi.python.org")

            executePythonCommand(
                runId = runId,
                command = argsList.toTypedArray(),
                workingDirectory = filesDir
            )
        }
    }

    private suspend fun executePythonCommand(
        runId: Int,
        command: Array<String>,
        workingDirectory: File
    ) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Update run status in DB
            val initialRun = repository.getRunById(runId)
            if (initialRun != null) {
                repository.updateRun(initialRun.copy(status = "RUNNING", startTime = startTime))
            }

            val pythonHome = PythonManager.getPythonHome(this@PythonService).absolutePath
            val pb = ProcessBuilder(*command)
                .directory(workingDirectory)
                .redirectErrorStream(true)

            // Inject Python runtime paths
            pb.environment()["PYTHONHOME"] = pythonHome
            pb.environment()["LD_LIBRARY_PATH"] = "$pythonHome/lib"
            pb.environment()["PATH"] = "$pythonHome/bin:" + (System.getenv("PATH") ?: "")
            pb.environment()["PYTHONUNBUFFERED"] = "1" // Force real-time logs

            val process = pb.start()
            activeProcesses[runId] = process

            val logBuffer = StringBuilder()
            var lastFlushTime = System.currentTimeMillis()
            var unflushedLines = 0

            // Helper to flush buffer to DB
            suspend fun flushLogs() {
                val currentLogs = logBuffer.toString()
                repository.updateRunLogs(runId, currentLogs)
                lastFlushTime = System.currentTimeMillis()
                unflushedLines = 0
            }

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logBuffer.append(line).append("\n")
                    unflushedLines++

                    // Flush buffer to Room every 1000ms or 30 unlogged lines to optimize DB writes
                    if (System.currentTimeMillis() - lastFlushTime > 1000 || unflushedLines >= 30) {
                        flushLogs()
                    }
                }
            }

            val exitCode = process.waitFor()
            activeProcesses.remove(runId)

            val duration = System.currentTimeMillis() - startTime
            val finalRun = repository.getRunById(runId)
            if (finalRun != null) {
                // If the run status was updated to KILLED, preserve it
                if (finalRun.status == "KILLED") {
                    repository.updateRun(
                        finalRun.copy(
                            logs = logBuffer.toString(),
                            duration = duration,
                            exitCode = exitCode
                        )
                    )
                } else {
                    val finalStatus = if (exitCode == 0) "COMPLETED" else "FAILED"
                    repository.updateRun(
                        finalRun.copy(
                            status = finalStatus,
                            logs = logBuffer.toString(),
                            duration = duration,
                            exitCode = exitCode
                        )
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Process execution failed", e)
            activeProcesses.remove(runId)
            
            val duration = System.currentTimeMillis() - startTime
            val crashedRun = repository.getRunById(runId)
            if (crashedRun != null) {
                repository.updateRun(
                    crashedRun.copy(
                        status = "FAILED",
                        logs = crashedRun.logs + "\nProcess crashed, error: ${e.localizedMessage}\n",
                        duration = duration,
                        exitCode = -1
                    )
                )
            }
        }
    }

    private fun killScriptProcess(runId: Int) {
        val process = activeProcesses[runId]
        if (process != null) {
            process.destroy()
            activeProcesses.remove(runId)
            serviceScope.launch {
                val run = repository.getRunById(runId)
                if (run != null) {
                    repository.updateRun(run.copy(status = "KILLED"))
                }
            }
            Log.d(TAG, "Process kill signals sent for runId: $runId")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        // clean up all processes upon service shutdown
        activeProcesses.values.forEach { it.destroy() }
        activeProcesses.clear()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// Simple Helper for Coroutine Scope
class CoroutineContextScope(context: java.util.concurrent.Executor) : CoroutineScope {
    override val coroutineContext = context.asCoroutineDispatcher()
}

// Custom simple executor scope construct
fun CoroutineContextScope(context: kotlin.coroutines.CoroutineContext): CoroutineScope = object : CoroutineScope {
    override val coroutineContext = context
}
