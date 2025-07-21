package com.hamham.gpsmover.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.hamham.gpsmover.R
import com.hamham.gpsmover.databinding.ActivityMapBinding
import com.hamham.gpsmover.modules.DbManager
import com.hamham.gpsmover.modules.DeviceManager
import com.hamham.gpsmover.modules.RulesManager
import com.hamham.gpsmover.modules.UpdateManager
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

    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    val viewModel by viewModels<MainViewModel>()
    private var currentPage = "map"
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loginDialog: AlertDialog
    private var xposedDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        UpdateManager.checkUpdate(this) {
            DbManager.dbMigrate(this)
            DeviceManager.updateDeviceInfo(this)
            RulesManager.applicationDisabled(this) {
                setupBottomNavigation()
                checkXposedModule()
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    showLoginDialog()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DeviceManager.checkBanStatus(this)
        RulesManager.applicationDisabled(this) {}
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            bottomNavigation.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            when (menuItem.itemId) {
                R.id.navigation_map -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.fade_in, R.anim.fade_out,
                            R.anim.fade_in, R.anim.fade_out
                        )
                        .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                        .commit()
                    true
                }
                R.id.navigation_favorites -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.fade_in, R.anim.fade_out,
                            R.anim.fade_in, R.anim.fade_out
                        )
                        .replace(R.id.map_fragment_container, FavoritesFragment(), "FavoritesFragment")
                        .commit()
                    true
                }
                R.id.navigation_settings -> {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.fade_in, R.anim.fade_out,
                            R.anim.fade_in, R.anim.fade_out
                        )
                        .replace(R.id.map_fragment_container, SettingsFragment(), "SettingsFragment")
                        .commit()
                    true
                }
                else -> false
            }
        }
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
                    startActivityForResult(signInIntent, 9001)
                }
            }
        }
        loginDialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            loginDialog.dismiss()
                            // يمكنك هنا تحديث الواجهة أو إعادة تحميل البيانات إذا لزم الأمر
                        } else {
                            android.widget.Toast.makeText(this, "Authentication Failed.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Sign-in failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
}