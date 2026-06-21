package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PythonScript
import com.example.data.ScriptRun
import com.example.data.ScriptRepository
import com.example.python.PythonManager
import com.example.python.PythonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PipPackageInfo(val name: String, val version: String)

class ScriptViewModel(private val repository: ScriptRepository) : ViewModel() {

    // Python Installation UI state flows
    val installState = PythonManager.installState
    val installProgress = PythonManager.installProgress
    val installStatusText = PythonManager.installStatusText

    // Reactive DB collections
    val allScripts: StateFlow<List<PythonScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRuns: StateFlow<List<ScriptRun>> = repository.allRuns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamically scanned pip packages
    private val _installedPackages = MutableStateFlow<List<PipPackageInfo>>(emptyList())
    val installedPackages: StateFlow<List<PipPackageInfo>> = _installedPackages

    private val _isRefreshingPackages = MutableStateFlow(false)
    val isRefreshingPackages: StateFlow<Boolean> = _isRefreshingPackages

    init {
        // Initialize default scripts if DB is empty
        viewModelScope.launch {
            allScripts.take(2).collect { list ->
                if (list.isEmpty()) {
                    createDefaultDemoScripts()
                }
            }
        }
    }

    private suspend fun createDefaultDemoScripts() {
        val helloWorld = PythonScript(
            name = "hello_world.py",
            content = """# Hello World python script
import sys
import time

print("Hello from python background runner!")
print("Operating System ABI:", sys.platform)
print("Python executable:", sys.executable)
print("Python version:", sys.version)

print("Starting a short 5-step loop:")
for i in range(1, 6):
    print(f"Step {i}/5 executing...")
    time.sleep(1)

print("Background PyRunner task finished successfully!")
""".trimIndent()
        )

        val sysDiagnostics = PythonScript(
            name = "diagnostics.py",
            content = """# Diagnostic system details tool
import sys
import os
import platform

print("=== SYSTEM DIAGNOSTICS ===")
print("OS Platform: ", platform.system())
print("OS Release:  ", platform.release())
print("Architecture:", platform.machine())
print("Byte Order:  ", sys.byteorder)
print("Current Dir: ", os.getcwd())
print("\n=== SYSTEM PATHS ===")
for p in sys.path:
    print(" -", p)
print("\n=== ENVIRONMENT VARIABLES ===")
for key, value in os.environ.items():
    if any(x in key.upper() for x in ["PATH", "HOME", "LD_", "PY"]):
        print(f"{key}: {value}")
print("\nDiagnostics complete!")
""".trimIndent()
        )

        repository.insertScript(helloWorld)
        repository.insertScript(sysDiagnostics)
    }

    fun checkPythonStatus(context: Context) {
        PythonManager.checkInstallation(context)
        if (PythonManager.installState.value == PythonManager.InstallState.INSTALLED) {
            refreshInstalledPackages(context)
        }
    }

    fun startInstallation(context: Context) {
        viewModelScope.launch {
            val success = PythonManager.installPythonAndPip(context)
            if (success) {
                refreshInstalledPackages(context)
            }
        }
    }

    // CRUD operations
    fun saveScript(name: String, content: String, id: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val nameWithExt = if (name.endsWith(".py")) name else "$name.py"
            val script = PythonScript(id = id, name = nameWithExt, content = content)
            if (id == 0) {
                repository.insertScript(script)
            } else {
                repository.updateScript(script)
            }
        }
    }

    fun deleteScript(script: PythonScript) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteScript(script)
        }
    }

    // Process triggers
    fun launchScript(context: Context, script: PythonScript) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Create a run history record as Pending
            val runId = repository.insertRun(
                ScriptRun(
                    scriptId = script.id,
                    scriptName = script.name,
                    status = "PENDING"
                )
            ).toInt()

            // 2. Start running via the background execution service Channel
            PythonService.startScript(context, script.id, runId)
        }
    }

    fun launchPipInstall(context: Context, packageArgument: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanPkgName = packageArgument.trim()
            if (cleanPkgName.isEmpty()) return@launch

            // 1. Create a run record representing pip installer task
            val runId = repository.insertRun(
                ScriptRun(
                    scriptId = -100, // special ID for pip tasks
                    scriptName = "pip install $cleanPkgName",
                    status = "PENDING"
                )
            ).toInt()

            // 2. Start running via Pip Channel
            PythonService.runPip(context, "install $cleanPkgName", runId)
            
            // Monitor when this finishes and refresh packages
            var monitorJob: kotlinx.coroutines.Job? = null
            monitorJob = viewModelScope.launch {
                repository.allRuns.collect { runs ->
                    val updatedPipRun = runs.firstOrNull { it.id == runId }
                    if (updatedPipRun != null && (updatedPipRun.status == "COMPLETED" || updatedPipRun.status == "FAILED")) {
                        refreshInstalledPackages(context)
                        monitorJob?.cancel() // Stop collecting once complete
                    }
                }
            }
        }
    }

    fun terminateRun(context: Context, run: ScriptRun) {
        PythonService.killScript(context, run.id)
    }

    fun deleteRunHistory(run: ScriptRun) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRunById(run.id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllRuns()
        }
    }

    fun refreshInstalledPackages(context: Context) {
        val pythonBin = PythonManager.getPythonBinary(context)
        if (!pythonBin.exists()) {
            _installedPackages.value = emptyList()
            return
        }

        _isRefreshingPackages.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(
                    pythonBin.absolutePath,
                    "-m",
                    "pip",
                    "list"
                )
                val pythonHome = PythonManager.getPythonHome(context).absolutePath
                pb.environment()["PYTHONHOME"] = pythonHome
                pb.environment()["LD_LIBRARY_PATH"] = "$pythonHome/lib"
                pb.environment()["PATH"] = "$pythonHome/bin:" + (System.getenv("PATH") ?: "")

                val process = pb.start()
                val lines = process.inputStream.bufferedReader().use { it.readLines() }
                process.waitFor()

                val packages = mutableListOf<PipPackageInfo>()
                var parsedDivider = false
                for (line in lines) {
                    if (line.contains("---")) {
                        parsedDivider = true
                        continue
                    }
                    if (parsedDivider) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            packages.add(PipPackageInfo(parts[0], parts[1]))
                        }
                    }
                }
                _installedPackages.value = packages
            } catch (e: Exception) {
                Log.e("ScriptViewModel", "Error listing pip packages", e)
            } finally {
                _isRefreshingPackages.value = false
            }
        }
    }
}

// Custom ViewModel Factory class
class ViewModelFactory(private val repository: ScriptRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScriptViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScriptViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
