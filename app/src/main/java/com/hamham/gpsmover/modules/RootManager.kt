package com.hamham.gpsmover.modules
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Sealed class representing the result of executing a root command.
 * This class hierarchy ensures type-safe handling of command execution results.
 */
sealed class RootCommandResult {
    /**
     * Represents a successful command execution.
     * @property output The command's output from stdout (trimmed)
     */
    data class Success(val output: String) : RootCommandResult()

    /**
     * Represents a failed command execution.
     * @property message The error message or stderr output
     * @property exitCode The process exit code, -1 if process failed to start
     */
    data class Error(val message: String, val exitCode: Int = -1) : RootCommandResult()
}

/**
 * Singleton manager for handling root access operations and executing privileged commands.
 * Provides functionality to:
 * - Check and request root access
 * - Execute commands with root privileges
 * - Handle timeouts and resource cleanup
 * 
 * Usage example:
 * ```
 * if (RootManager.isRootGranted()) {
 *     when (val result = RootManager.executeRootCommand("some command")) {
 *         is RootCommandResult.Success -> handleSuccess(result.output)
 *         is RootCommandResult.Error -> handleError(result.message)
 *     }
 * }
 * ```
 */
object RootManager {
    private const val TAG = "RootManager"
    // Default timeout for command execution
    private const val DEFAULT_TIMEOUT = 5L
    // Command used to verify root access
    private const val ROOT_CHECK_COMMAND = "id"
    // Short timeout for root check to avoid UI blocking
    private const val ROOT_TEST_TIMEOUT = 1L
    
    // Cache for root access status to avoid repeated permission requests
    private var cachedRootStatus: Boolean? = null
    private var lastRootCheck: Long = 0
    private const val ROOT_CHECK_CACHE_DURATION = 30_000L // 30 seconds

    /**
     * Checks and requests root access, logging the result.
     * This method should be called early in the application lifecycle
     * to ensure root access is available before attempting any root operations.
     * 
     * The result is logged:
     * - INFO level for successful root access or successful request
     * - WARN level for denied root access
     */
    fun checkAndRequestRoot() {
        Log.i(TAG, "🔍 Checking and requesting root access...")
        val isRooted = isRootGranted(requestIfNotAvailable = true)
        if (isRooted) {
            Log.i(TAG, "✅ Root access is available and ready")
        } else {
            Log.w(TAG, "❌ Root access not available")
        }
    }

    /**
     * Checks if the device has root access by attempting to execute a command with su.
     * Uses caching to avoid repeated permission requests for silent operations.
     * 
     * Implementation details:
     * 1. Checks cached result first (valid for 30 seconds)
     * 2. If not cached or expired, attempts to check root access silently
     * 3. Only requests permissions if explicitly requested
     * 
     * @param requestIfNotAvailable Whether to request root access if not available
     * @return true if root access is available and working, false otherwise
     */
    fun isRootGranted(requestIfNotAvailable: Boolean = false): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Return cached result if still valid and not requesting fresh check
        if (!requestIfNotAvailable) {
            cachedRootStatus?.let { cached ->
                if (currentTime - lastRootCheck < ROOT_CHECK_CACHE_DURATION) {
                    Log.d(TAG, "📋 Using cached root status: $cached")
                    return cached
                }
            }
        }
        
        var process: Process? = null
        return try {
            Log.d(TAG, "🔍 Checking root access...")
            
            // Force cleanup before new process
            System.gc()
            Thread.sleep(50)
            
            process = Runtime.getRuntime().exec("su")
            
            // Write commands and close output stream immediately
            val outputStream = process.outputStream
            outputStream.write("$ROOT_CHECK_COMMAND\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()

            val rootGranted = if (!process.waitFor(ROOT_TEST_TIMEOUT, TimeUnit.SECONDS)) {
                Log.w(TAG, "⏰ Root check timed out")
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                false
            } else {
                val exitCode = process.exitValue()
                exitCode == 0
            }

            // Update cache
            cachedRootStatus = rootGranted
            lastRootCheck = currentTime

            if (rootGranted) {
                Log.d(TAG, "✅ Root access is available")
            } else {
                Log.w(TAG, "❌ Root access denied or not available")
                // Only request if explicitly asked
                if (requestIfNotAvailable) {
                    Log.i(TAG, "🔓 Attempting to request root access...")
                    val requestResult = requestRootAccess()
                    if (requestResult) {
                        cachedRootStatus = true
                        lastRootCheck = currentTime
                        return true
                    }
                }
            }
            
            rootGranted
        } catch (e: Exception) {
            when (e) {
                is IOException -> Log.e(TAG, "❌ Failed to execute su command", e)
                is SecurityException -> Log.e(TAG, "❌ Security exception while checking root", e)
                else -> Log.e(TAG, "❌ Unexpected error checking root access", e)
            }
            
            // Only request if explicitly asked and no cached positive result
            if (requestIfNotAvailable && cachedRootStatus != true) {
                Log.i(TAG, "🔓 Attempting to request root access after error...")
                val requestResult = requestRootAccess()
                if (requestResult) {
                    cachedRootStatus = true
                    lastRootCheck = currentTime
                    return true
                }
            }
            
            // Cache negative result
            cachedRootStatus = false
            lastRootCheck = currentTime
            false
        } finally {
            // Ensure complete cleanup
            try {
                process?.let { p ->
                    try {
                        p.inputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.outputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.errorStream?.close()
                    } catch (_: Exception) {}
                    
                    if (p.isAlive) {
                        p.destroyForcibly()
                        p.waitFor(1, TimeUnit.SECONDS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up root check process", e)
            }
        }
    }

    /**
     * Attempts to request root access by executing a simple su command.
     * This will trigger the superuser permission dialog if available.
     * Forces a permission request even if root appears unavailable.
     * 
     * @return true if root request was successful, false otherwise
     */
    private fun requestRootAccess(): Boolean {
        var process: Process? = null
        return try {
            Log.i(TAG, "🔓 Requesting root access (forcing permission dialog)...")
            
            // Clear any cached status to force fresh check
            cachedRootStatus = null
            lastRootCheck = 0
            
            // Force cleanup before new process
            System.gc()
            Thread.sleep(100)
            
            process = Runtime.getRuntime().exec("su")
            
            // Write commands and close output stream immediately
            val outputStream = process.outputStream
            outputStream.write("echo 'GPS Mover requesting root access'\n".toByteArray())
            outputStream.flush()
            outputStream.write("id\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()

            val requestSuccessful = if (!process.waitFor(10, TimeUnit.SECONDS)) {
                Log.w(TAG, "⏰ Root request timed out")
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                false
            } else {
                val exitCode = process.exitValue()
                val output = try {
                    process.inputStream.bufferedReader().use { it.readText() }.trim()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read output from root request: ${e.message}")
                    ""
                }
                
                val success = exitCode == 0 && output.isNotEmpty()
                
                if (success) {
                    Log.i(TAG, "✅ Root access granted! Output: $output")
                    // Update cache with positive result
                    cachedRootStatus = true
                    lastRootCheck = System.currentTimeMillis()
                } else {
                    Log.w(TAG, "❌ Root access request denied (exit code: $exitCode, output: '$output')")
                    // Don't cache negative result immediately - user might grant it
                }
                
                success
            }
            
            requestSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting root access", e)
            false
        } finally {
            // Ensure complete cleanup
            try {
                process?.let { p ->
                    try {
                        p.inputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.outputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.errorStream?.close()
                    } catch (_: Exception) {}
                    
                    if (p.isAlive) {
                        p.destroyForcibly()
                        p.waitFor(1, TimeUnit.SECONDS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up request process", e)
            }
        }
    }

    /**
     * Clears the cached root status, forcing a fresh check on next call.
     * Useful when root status might have changed (e.g., after granting/revoking permissions).
     */
    fun clearRootCache() {
        Log.d(TAG, "🧹 Clearing root access cache")
        cachedRootStatus = null
        lastRootCheck = 0
    }

    /**
     * Forces a complete reset of the root manager state.
     * This method should be called when you suspect the root system is stuck
     * or when you want to start fresh with root operations.
     */
    fun resetRootManager() {
        Log.i(TAG, "🔄 Resetting root manager state completely")
        clearRootCache()
        
        // Force garbage collection to clean up any hanging processes
        System.gc()
        
        // Give the system time to clean up
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        
        Log.i(TAG, "✅ Root manager reset completed")
    }

    /**
     * Checks for root access specifically for silent installations.
     * This method will actively request root permissions if not available,
     * which is necessary for silent update functionality.
     * 
     * @return true if root access is available after potential request, false otherwise
     */
    fun isRootGrantedForSilentInstall(): Boolean {
        Log.d(TAG, "🔍 Checking root access for silent installation...")
        
        // First check if we have cached positive result
        val currentTime = System.currentTimeMillis()
        cachedRootStatus?.let { cached ->
            if (cached && (currentTime - lastRootCheck < ROOT_CHECK_CACHE_DURATION)) {
                Log.d(TAG, "📋 Using cached positive root status for silent install")
                return true
            }
        }
        
        // Clear cache to force fresh check
        clearRootCache()
        
        // Try to get root access, requesting if needed
        val hasRoot = isRootGranted(requestIfNotAvailable = true)
        
        if (hasRoot) {
            Log.i(TAG, "✅ Root access confirmed for silent installation")
        } else {
            Log.w(TAG, "❌ Root access not available for silent installation")
        }
        
        return hasRoot
    }

    /**
     * Executes a shell command with root privileges and returns the result.
     * 
     * This method handles:
     * - Root access verification and request
     * - Command execution with timeout
     * - Output and error stream management
     * - Resource cleanup
     * - Exception handling
     * 
     * Example usage:
     * ```
     * when (val result = executeRootCommand("pm install -r /path/to/app.apk")) {
     *     is RootCommandResult.Success -> println("Installation successful: ${result.output}")
     *     is RootCommandResult.Error -> println("Installation failed: ${result.message}")
     * }
     * ```
     * 
     * @param command The shell command to execute as root
     * @param timeout Maximum time in seconds to wait for command completion (default: 5 seconds)
     * @return [RootCommandResult] containing either Success with output or Error with message
     * @throws TimeoutException if the command execution exceeds the specified timeout
     * @throws IOException if there's an error reading/writing to the process streams
     * @throws SecurityException if there's a security violation executing the command
     */
    @Throws(TimeoutException::class, IOException::class, SecurityException::class)
    fun executeRootCommand(command: String, timeout: Long = DEFAULT_TIMEOUT): RootCommandResult {
        Log.d(TAG, "🚀 Executing root command: $command")
        
        // Clear cache before checking to ensure fresh state
        clearRootCache()
        
        // Check root access with request if needed
        if (!isRootGranted(requestIfNotAvailable = true)) {
            Log.e(TAG, "❌ Root access not available for command execution")
            return RootCommandResult.Error("Root access not granted")
        }

        var process: Process? = null
        try {
            // Force garbage collection to clean up any previous processes
            System.gc()
            Thread.sleep(100) // Small delay to ensure cleanup
            
            process = Runtime.getRuntime().exec("su")
            
            // Use individual streams instead of buffered to avoid hanging
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            val errorStream = process.errorStream
            
            // Write command with explicit flushing
            outputStream.write("$command\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close() // Close immediately after writing
            
            // Wait for process with timeout
            val processFinished = process.waitFor(timeout, TimeUnit.SECONDS)
            
            if (!processFinished) {
                Log.w(TAG, "⏰ Command timed out, force destroying process")
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS) // Give it time to die
                throw TimeoutException("Command execution timed out after $timeout seconds")
            }

            // Read output and error streams
            val output = try {
                inputStream.bufferedReader().use { it.readText() }.trim()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read output stream: ${e.message}")
                ""
            }
            
            val error = try {
                errorStream.bufferedReader().use { it.readText() }.trim()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read error stream: ${e.message}")
                ""
            }
            
            val exitCode = process.exitValue()
            
            Log.d(TAG, "Command finished with exit code: $exitCode, output: '$output', error: '$error'")

            return when {
                exitCode == 0 -> {
                    Log.d(TAG, "✅ Command executed successfully: $command")
                    RootCommandResult.Success(output)
                }
                else -> {
                    Log.w(TAG, "⚠️ Command failed with exit code $exitCode: $command, error: $error")
                    RootCommandResult.Error(if (error.isNotEmpty()) error else "Command failed with exit code $exitCode", exitCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during command execution: $command", e)
            try {
                process?.destroyForcibly()
                process?.waitFor(1, TimeUnit.SECONDS)
            } catch (destroyException: Exception) {
                Log.e(TAG, "❌ Error destroying process after exception", destroyException)
            }
            
            when (e) {
                is TimeoutException -> throw e
                is IOException -> throw e
                is SecurityException -> throw e
                else -> {
                    return RootCommandResult.Error(e.message ?: "Unknown error")
                }
            }
        } finally {
            // Ensure complete cleanup
            try {
                process?.let { p ->
                    try {
                        p.inputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.outputStream?.close()
                    } catch (_: Exception) {}
                    try {
                        p.errorStream?.close()
                    } catch (_: Exception) {}
                    
                    if (p.isAlive) {
                        p.destroyForcibly()
                        p.waitFor(1, TimeUnit.SECONDS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in final cleanup", e)
            }
            
            // Clear cache after execution to force fresh check next time
            clearRootCache()
            
            // Force garbage collection to clean up resources
            System.gc()
        }
    }
}
