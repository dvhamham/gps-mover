package com.hamham.gpsmover.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.hamham.gpsmover.R
import com.hamham.gpsmover.databinding.ActivityMapBinding
import com.hamham.gpsmover.AppInitializer
import com.hamham.gpsmover.modules.CollectionsManager
import com.hamham.gpsmover.modules.UpdateManager
import com.hamham.gpsmover.modules.RootManager
import com.hamham.gpsmover.utils.PermissionHelper
import com.hamham.gpsmover.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import android.app.AlertDialog
import android.view.LayoutInflater

@AndroidEntryPoint
class MapActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    val viewModel by viewModels<MainViewModel>()
    private var currentPage = "map"
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loginDialog: AlertDialog
    private var xposedDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request root permissions early in the application lifecycle
        RootManager.checkAndRequestRoot()
        
        // Configure the system window for modern edge-to-edge design
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        // حمّل الخريطة مباشرة
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                .commit()
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        // إذا كان تسجيل الخروج، أظهر الديلوك فورًا بعد رسم الخريطة
        if (intent.getBooleanExtra("showLoginDialogImmediately", false)) {
            window.decorView.post { showLoginDialog() }
            return
        }
        // باقي عمليات التهيئة
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        setupBottomNavigation()
        checkXposedModule()
        if (user != null) {
            AppInitializer.initializeAppData(this)
            // Initialize database collections and schema once only
            CollectionsManager.initializeCollections(this)
            
            // Request all required permissions for background execution
            PermissionHelper.requestAllPermissions(this)
            
            // Check for updates after initialization
            UpdateManager.checkUpdate(this) {
                Log.d("MapActivity", "Update check completed in onCreate")
            }
        } else {
            // Initialize basic collections structure even without user
            CollectionsManager.initializeCollections(this)
            showLoginDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-setup bottom navigation in case it was broken during logout/login cycle
        setupBottomNavigation()
        // Check comprehensive app status (killall and device ban)
        CollectionsManager.checkAppStatus(this)
        com.hamham.gpsmover.modules.CustomMessage.showIfEnabled(this)
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Clear any existing listeners to prevent conflicts
        bottomNavigation.setOnItemSelectedListener(null)
        
        // Set up the listener again
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            bottomNavigation.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            when (menuItem.itemId) {
                R.id.navigation_map -> {
                    if (supportFragmentManager.findFragmentByTag("MapFragment") == null) {
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in, R.anim.fade_out,
                                R.anim.fade_in, R.anim.fade_out
                            )
                            .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                            .commit()
                    }
                    currentPage = "map"
                    true
                }
                R.id.navigation_favorites -> {
                    if (supportFragmentManager.findFragmentByTag("FavoritesFragment") == null) {
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in, R.anim.fade_out,
                                R.anim.fade_in, R.anim.fade_out
                            )
                            .replace(R.id.map_fragment_container, FavoritesFragment(), "FavoritesFragment")
                            .commit()
                    }
                    currentPage = "favorites"
                    true
                }
                R.id.navigation_settings -> {
                    if (supportFragmentManager.findFragmentByTag("SettingsFragment") == null) {
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in, R.anim.fade_out,
                                R.anim.fade_in, R.anim.fade_out
                            )
                            .replace(R.id.map_fragment_container, SettingsFragment(), "SettingsFragment")
                            .commit()
                    }
                    currentPage = "settings"
                    true
                }
                else -> false
            }
        }
        
        // Set initial selected item
        when (currentPage) {
            "map" -> bottomNavigation.selectedItemId = R.id.navigation_map
            "favorites" -> bottomNavigation.selectedItemId = R.id.navigation_favorites
            "settings" -> bottomNavigation.selectedItemId = R.id.navigation_settings
            else -> bottomNavigation.selectedItemId = R.id.navigation_map
        }
    }

    private fun promptLoginIfNeeded() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // انتظر 10 ثوانٍ ثم انتقل إلى LoginActivity
            window.decorView.postDelayed({
                val userCheck = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (userCheck == null) {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }, 10000)
        }
    }

    private fun showLoginDialog() {
        // تحقق أولاً من تفعيل Xposed/LSPosed
        if (!com.hamham.gpsmover.xposed.XposedSelfHooks.isXposedModuleEnabled()) {
            checkXposedModule()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        loginDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        loginDialog.setCanceledOnTouchOutside(false)
        val loginButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.login_button)
        loginButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    val signInIntent = googleSignInClient.signInIntent
                    signInIntent.putExtra("override_account", null as String?)
                    startActivityForResult(signInIntent, RC_SIGN_IN)
                }
            }
        }
        loginDialog.show()
    }

    private fun checkXposedModule() {
        if (xposedDialog?.isShowing == true) return
        if (!com.hamham.gpsmover.xposed.XposedSelfHooks.isXposedModuleEnabled()) {
            xposedDialog = AlertDialog.Builder(this)
                .setTitle("LSPosed/Xposed Not Active")
                .setMessage("You must enable the LSPosed/Xposed module for GPS Mover to work.\nPlease enable the module and reboot your device.")
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    
    /**
     * Handle activity results for system settings (battery optimization, overlay permission)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle permission-related activity results
        PermissionHelper.onActivityResult(this, requestCode, resultCode, data)
        
        // Handle Google Sign-In result
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            loginDialog.dismiss()
                            // Initialize app data after successful login
                            AppInitializer.initializeAppData(this)
                            // Update database with new user information - only on first successful login
                            CollectionsManager.onUserLoginSuccess(this)
                            // Re-setup bottom navigation after login to ensure it works properly
                            setupBottomNavigation()
                            
                            // Request all required permissions after successful login
                            PermissionHelper.requestAllPermissions(this)
                            
                            // Check for updates after successful login
                            UpdateManager.checkUpdate(this) {
                                Log.d("MapActivity", "Update check completed after login")
                            }
                        } else {
                            android.widget.Toast.makeText(this, "Authentication Failed.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Sign-in failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}