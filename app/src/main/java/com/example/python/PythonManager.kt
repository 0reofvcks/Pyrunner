package com.example.python

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PythonManager {
    private const val TAG = "PythonManager"
    
    // Stable Release 20240107 with Python 3.10.13
    private const val RELEASE_TAG = "20240107"
    private const val PYTHON_VERSION = "3.10.13"

    // Installation states
    enum class InstallState {
        NOT_INSTALLED,
        DOWNLOADING,
        EXTRACTING,
        INSTALLED,
        ERROR
    }

    private val _installState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val installState: StateFlow<InstallState> = _installState

    private val _installProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val installProgress: StateFlow<Float> = _installProgress

    private val _installStatusText = MutableStateFlow("Tap to start installation")
    val installStatusText: StateFlow<String> = _installStatusText

    fun checkInstallation(context: Context) {
        val pythonBin = getPythonBinary(context)
        if (pythonBin.exists() && pythonBin.canExecute()) {
            _installState.value = InstallState.INSTALLED
            _installStatusText.value = "Python $PYTHON_VERSION installed and ready"
        } else {
            _installState.value = InstallState.NOT_INSTALLED
            _installStatusText.value = "Python $PYTHON_VERSION not installed"
        }
    }

    fun getPythonHome(context: Context): File {
        return File(context.filesDir, "python")
    }

    fun getPythonBinary(context: Context): File {
        return File(getPythonHome(context), "bin/python3")
    }

    fun getPipBinary(context: Context): File {
        return File(getPythonHome(context), "bin/pip3")
    }

    private fun getCpuArchitecture(): String {
        val abi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else Build.CPU_ABI
        return when {
            abi.startsWith("arm64") || abi.startsWith("aarch64") -> "aarch64"
            abi.contains("64") -> "x86_64"
            abi.startsWith("arm") -> "armv7"
            else -> "i686"
        }
    }

    private fun getDownloadUrl(): String {
        val arch = getCpuArchitecture()
        val target = when (arch) {
            "aarch64" -> "aarch64-unknown-linux-android"
            "x86_64" -> "x86_64-unknown-linux-android"
            "armv7" -> "armv7-unknown-linux-android"
            else -> "i686-unknown-linux-android"
        }
        return "https://github.com/indygreg/python-build-standalone/releases/download/$RELEASE_TAG/cpython-$PYTHON_VERSION+$RELEASE_TAG-$target-install_only.tar.gz"
    }

    suspend fun installPythonAndPip(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            _installState.value = InstallState.DOWNLOADING
            _installProgress.value = 0f
            _installStatusText.value = "Detecting CPU architecture: ${getCpuArchitecture()}"

            val downloadUrl = getDownloadUrl()
            _installStatusText.value = "Downloading Python runtime..."
            Log.d(TAG, "Starting download from $downloadUrl")

            val client = OkHttpClient()
            val request = Request.Builder().url(downloadUrl).build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _installState.value = InstallState.ERROR
                _installStatusText.value = "Server error during download: ${response.code}"
                return@withContext false
            }

            val body = response.body
            if (body == null) {
                _installState.value = InstallState.ERROR
                _installStatusText.value = "Empty file received from server"
                return@withContext false
            }

            val cacheFile = File(context.cacheDir, "python_installer.tar.gz")
            val totalBytes = body.contentLength()
            
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(cacheFile)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalBytes > 0) {
                    val progress = totalBytesRead.toFloat() / totalBytes.toFloat()
                    _installProgress.value = progress
                    val percentage = (progress * 100).toInt()
                    _installStatusText.value = "Downloading: $percentage% (${totalBytesRead / 1024 / 1024}MB / ${totalBytes / 1024 / 1024}MB)"
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Extracting Python
            _installState.value = InstallState.EXTRACTING
            _installProgress.value = 0.5f
            _installStatusText.value = "Decompressing Python and modules..."
            Log.d(TAG, "Extraction started")

            val pythonHome = getPythonHome(context)
            if (pythonHome.exists()) {
                pythonHome.deleteRecursively()
            }
            pythonHome.mkdirs()

            // Run system tar command via process
            val tarProcess = ProcessBuilder()
                .command("tar", "-xzf", cacheFile.absolutePath, "-C", context.filesDir.absolutePath)
                .redirectErrorStream(true)
                .start()

            val tarOutput = tarProcess.inputStream.bufferedReader().use { it.readText() }
            val exitCode = tarProcess.waitFor()
            Log.d(TAG, "Tar finished with exit code $exitCode: $tarOutput")

            cacheFile.delete() // Clean cache file

            if (exitCode != 0) {
                _installState.value = InstallState.ERROR
                _installStatusText.value = "Extraction failed: tar exited with status $exitCode"
                return@withContext false
            }

            // Set executable permission on binaries
            _installStatusText.value = "Ajusting permissions and paths..."
            val binDir = File(pythonHome, "bin")
            if (binDir.exists() && binDir.isDirectory) {
                val chmod = ProcessBuilder()
                    .command("chmod", "-R", "755", binDir.absolutePath)
                    .start()
                chmod.waitFor()
            }

            // Confirm binary executes
            val pythonBin = getPythonBinary(context)
            if (pythonBin.exists()) {
                pythonBin.setExecutable(true)
                
                // Let's test execution
                val testRun = ProcessBuilder()
                    .command(pythonBin.absolutePath, "--version")
                    .redirectErrorStream(true)
                    .start()
                val testOut = testRun.inputStream.bufferedReader().use { it.readText() }.trim()
                testRun.waitFor()
                Log.d(TAG, "Test python run: $testOut")

                _installState.value = InstallState.INSTALLED
                _installStatusText.value = "Successfully installed! $testOut"
                return@withContext true
            } else {
                _installState.value = InstallState.ERROR
                _installStatusText.value = "Executable binary not found at standard path"
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Python", e)
            _installState.value = InstallState.ERROR
            _installStatusText.value = "Installation failed: ${e.localizedMessage}"
            return@withContext false
        }
    }
}
