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
        
        // Return cached result if still valid
        cachedRootStatus?.let { cached ->
            if (currentTime - lastRootCheck < ROOT_CHECK_CACHE_DURATION) {
                Log.d(TAG, "📋 Using cached root status: $cached")
                return cached
            }
        }
        
        return try {
            Log.d(TAG, "🔍 Checking root access...")
            val process = Runtime.getRuntime().exec("su")
            
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("$ROOT_CHECK_COMMAND\n")
                writer.write("exit\n")
                writer.flush()
            }

            val rootGranted = if (!process.waitFor(ROOT_TEST_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroy()
                Log.w(TAG, "⏰ Root check timed out")
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
        return try {
            Log.i(TAG, "🔓 Requesting root access (forcing permission dialog)...")
            
            // Clear any cached status to force fresh check
            cachedRootStatus = null
            lastRootCheck = 0
            
            val process = Runtime.getRuntime().exec("su")
            
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("echo 'GPS Mover requesting root access'\n")
                writer.write("id\n")  // This should trigger the permission dialog
                writer.write("exit\n")
                writer.flush()
            }

            val requestSuccessful = if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy()
                Log.w(TAG, "⏰ Root request timed out")
                false
            } else {
                val exitCode = process.exitValue()
                val output = try {
                    process.inputStream.bufferedReader().use { it.readText() }.trim()
                } catch (e: Exception) {
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
        
        // First check root silently (don't request if not available for silent operations)
        if (!isRootGranted(requestIfNotAvailable = false)) {
            Log.e(TAG, "❌ Root access not available for command execution")
            return RootCommandResult.Error("Root access not granted")
        }

        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("su")
            
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("$command\n")
                writer.write("exit\n")
                writer.flush()
            }

            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroy()
                throw TimeoutException("Command execution timed out after $timeout seconds")
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.exitValue()

            return when {
                exitCode == 0 -> {
                    Log.d(TAG, "✅ Command executed successfully: $command")
                    RootCommandResult.Success(output)
                }
                else -> {
                    Log.w(TAG, "⚠️ Command failed with exit code $exitCode: $command, error: $error")
                    RootCommandResult.Error(error, exitCode)
                }
            }
        } catch (e: Exception) {
            process?.destroy()
            when (e) {
                is TimeoutException -> throw e
                is IOException -> throw e
                is SecurityException -> throw e
                else -> {
                    Log.e(TAG, "❌ Error executing root command: $command", e)
                    return RootCommandResult.Error(e.message ?: "Unknown error")
                }
            }
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error destroying process", e)
            }
        }
    }
}
