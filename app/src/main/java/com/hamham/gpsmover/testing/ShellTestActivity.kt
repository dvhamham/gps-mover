package com.hamham.gpsmover.testing

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamham.gpsmover.R
import com.hamham.gpsmover.modules.CollectionsManager

/**
 * Test Activity for Shell Command System
 * Use this activity to test the shell command execution system
 */
class ShellTestActivity : AppCompatActivity() {
    
    private lateinit var commandEditText: EditText
    private lateinit var countEditText: EditText
    private lateinit var waitEditText: EditText
    private lateinit var statusTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create layout programmatically
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        
        // Title
        val titleText = TextView(this).apply {
            text = "Shell Command System Test"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)
        
        // Command input
        val commandLabel = TextView(this).apply {
            text = "Command:"
            textSize = 16f
        }
        layout.addView(commandLabel)
        
        commandEditText = EditText(this).apply {
            setText("echo 'Test execution #\$RANDOM'")
            setPadding(16, 16, 16, 16)
        }
        layout.addView(commandEditText)
        
        // Count input
        val countLabel = TextView(this).apply {
            text = "Count:"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }
        layout.addView(countLabel)
        
        countEditText = EditText(this).apply {
            setText("3")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
        }
        layout.addView(countEditText)
        
        // Wait input
        val waitLabel = TextView(this).apply {
            text = "Wait (seconds):"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }
        layout.addView(waitLabel)
        
        waitEditText = EditText(this).apply {
            setText("2")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(16, 16, 16, 16)
        }
        layout.addView(waitEditText)
        
        // Buttons
        val runTestButton = Button(this).apply {
            text = "Run Test"
            setOnClickListener { runShellTest() }
            setPadding(0, 32, 0, 16)
        }
        layout.addView(runTestButton)
        
        val quickTestButton = Button(this).apply {
            text = "Quick Test (echo 3x2s)"
            setOnClickListener { runQuickTest() }
        }
        layout.addView(quickTestButton)
        
        val statusButton = Button(this).apply {
            text = "Check Status"
            setOnClickListener { checkStatus() }
            setPadding(0, 16, 0, 0)
        }
        layout.addView(statusButton)
        
        val resetButton = Button(this).apply {
            text = "Force Reset"
            setOnClickListener { forceReset() }
        }
        layout.addView(resetButton)
        
        val debugButton = Button(this).apply {
            text = "Debug State"
            setOnClickListener { debugExecutionState() }
        }
        layout.addView(debugButton)
        
        val restartListenerButton = Button(this).apply {
            text = "Restart Listener"
            setOnClickListener { restartListener() }
        }
        layout.addView(restartListenerButton)
        
        val addTestCommandButton = Button(this).apply {
            text = "Add Test Command"
            setOnClickListener { addTestCommand() }
        }
        layout.addView(addTestCommandButton)
        
        val emergencyStopButton = Button(this).apply {
            text = "🚨 EMERGENCY STOP"
            setBackgroundColor(0xFFFF4444.toInt()) // Red background
            setTextColor(0xFFFFFFFF.toInt()) // White text
            setOnClickListener { emergencyStop() }
        }
        layout.addView(emergencyStopButton)
        
        // Status display
        val statusLabel = TextView(this).apply {
            text = "System Status:"
            textSize = 16f
            setPadding(0, 32, 0, 8)
        }
        layout.addView(statusLabel)
        
        statusTextView = TextView(this).apply {
            text = "Ready for testing"
            textSize = 12f
            setBackgroundColor(0xFFEEEEEE.toInt())
            setPadding(16, 16, 16, 16)
        }
        layout.addView(statusTextView)
        
        setContentView(layout)
        
        // Initial status check
        checkStatus()
        
        Log.i("ShellTestActivity", "🧪 Shell Command Test Activity created")
    }
    
    private fun runShellTest() {
        val command = commandEditText.text.toString().trim()
        val count = countEditText.text.toString().toIntOrNull() ?: 1
        val wait = waitEditText.text.toString().toIntOrNull() ?: 0
        
        if (command.isEmpty()) {
            statusTextView.text = "❌ Error: Empty command"
            return
        }
        
        Log.i("ShellTestActivity", "🚀 Starting manual test - Command: '$command', Count: $count, Wait: ${wait}s")
        
        statusTextView.text = "🔄 Setting up test in database...\nCommand: $command\nCount: $count\nWait: ${wait}s"
        
        // Set up test in database
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        CollectionsManager.testShellCommandExecution(this)
        
        // Update status
        statusTextView.text = "✅ Test command sent to database\nMonitor logs for execution progress..."
        
        // Check status after a moment
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkStatus()
        }, 2000)
    }
    
    private fun runQuickTest() {
        Log.i("ShellTestActivity", "🧪 Running quick test")
        statusTextView.text = "🔄 Running quick test: echo 3 times with 2s wait..."
        
        CollectionsManager.testShellCommandExecution(this)
        
        // Check status after test setup
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkStatus()
        }, 1000)
    }
    
    private fun checkStatus() {
        val status = CollectionsManager.checkExecutionStatus()
        statusTextView.text = status
        Log.i("ShellTestActivity", "📊 Status checked")
    }
    
    private fun forceReset() {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId != null) {
            CollectionsManager.forceResetExecutionState(androidId)
            statusTextView.text = "🔄 Forced reset completed"
            Log.i("ShellTestActivity", "🔄 Forced reset executed")
            
            // Check status after reset
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                checkStatus()
            }, 1000)
        } else {
            statusTextView.text = "❌ Error: Cannot get Android ID"
        }
    }
    
    private fun debugExecutionState() {
        Log.i("ShellTestActivity", "🔍 Running debug state check")
        statusTextView.text = "🔍 Checking debug state..."
        
        val debugInfo = CollectionsManager.debugShellExecutionState(this)
        statusTextView.text = debugInfo
        
        Log.i("ShellTestActivity", "🔍 Debug info retrieved")
    }
    
    private fun restartListener() {
        Log.i("ShellTestActivity", "🔄 Restarting shell command listener")
        statusTextView.text = "🔄 Restarting listener..."
        
        CollectionsManager.forceRestartShellListener(this)
        
        // Check status after restart
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            statusTextView.text = "✅ Listener restarted - checking status..."
            checkStatus()
        }, 2000)
    }
    
    private fun addTestCommand() {
        val command = commandEditText.text.toString().trim().ifEmpty { "ls -la" }
        val count = countEditText.text.toString().toIntOrNull() ?: 1
        val wait = waitEditText.text.toString().toIntOrNull() ?: 0
        
        Log.i("ShellTestActivity", "➕ Adding test command: '$command'")
        statusTextView.text = "➕ Adding test command...\nCommand: $command\nCount: $count\nWait: ${wait}s"
        
        CollectionsManager.addTestShellCommand(this, command, count, wait)
        
        // Check status after adding command
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkStatus()
        }, 3000)
    }
    
    private fun emergencyStop() {
        Log.e("ShellTestActivity", "🚨 Emergency stop triggered by user")
        statusTextView.text = "🚨 EMERGENCY STOP in progress..."
        
        val result = CollectionsManager.emergencyStopExecution(this)
        statusTextView.text = result
        
        // Check status after emergency stop
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkStatus()
        }, 2000)
    }
}
