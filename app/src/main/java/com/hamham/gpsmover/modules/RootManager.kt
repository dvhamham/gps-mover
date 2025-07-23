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

    /**
     * Checks if root access is available and logs the result.
     * This method should be called early in the application lifecycle
     * to ensure root access is available before attempting any root operations.
     * 
     * The result is logged:
     * - DEBUG level for successful root access
     * - WARN level for denied root access
     */
    fun checkAndRequestRoot() {
        val isRooted = isRootGranted()
        if (isRooted) {
            Log.d(TAG, "Root access granted")
        } else {
            Log.w(TAG, "Root access denied")
        }
    }

    /**
     * Checks if the device has root access by attempting to execute a command with su.
     * Uses a quick timeout to prevent hanging and implements proper resource cleanup.
     * 
     * Implementation details:
     * 1. Attempts to execute 'su' command
     * 2. Runs a simple 'id' command to verify root access
     * 3. Uses a short timeout to quickly determine root status
     * 4. Properly cleans up resources regardless of the outcome
     * 
     * @return true if root access is available and working, false otherwise
     */
    fun isRootGranted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("$ROOT_CHECK_COMMAND\n")
                writer.write("exit\n")
                writer.flush()
            }

            if (!process.waitFor(ROOT_TEST_TIMEOUT, TimeUnit.SECONDS)) {
                process.destroy()
                Log.w(TAG, "Root check timed out")
                return false
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            when (e) {
                is IOException -> Log.e(TAG, "Failed to execute su command", e)
                is SecurityException -> Log.e(TAG, "Security exception while checking root", e)
                else -> Log.e(TAG, "Unexpected error checking root access", e)
            }
            false
        }
    }

    /**
     * Executes a shell command with root privileges and returns the result.
     * 
     * This method handles:
     * - Root access verification
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
        if (!isRootGranted()) {
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
                    Log.d(TAG, "Command executed successfully: $command")
                    RootCommandResult.Success(output)
                }
                else -> {
                    Log.w(TAG, "Command failed with exit code $exitCode: $command, error: $error")
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
                    Log.e(TAG, "Error executing root command: $command", e)
                    return RootCommandResult.Error(e.message ?: "Unknown error")
                }
            }
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process", e)
            }
        }
    }
}
