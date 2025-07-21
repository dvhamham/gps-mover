package com.hamham.gpsmover.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.hamham.gpsmover.R
import com.hamham.gpsmover.xposed.PrefManager
import com.hamham.gpsmover.viewmodel.MainViewModel
import com.hamham.gpsmover.helpers.showCustomSnackbar
import com.hamham.gpsmover.helpers.SnackbarType
import androidx.appcompat.app.AppCompatDelegate
import com.hamham.gpsmover.helpers.performHapticClick
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

class SettingsPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private var googleSignInClient: GoogleSignInClient? = null
    private val RC_SIGN_IN = 9001
    private var viewModel: MainViewModel? = null
    private var onSettingsChanged: (() -> Unit)? = null
    private var onBackClick: (() -> Unit)? = null
    private var onAccountStateChanged: (() -> Unit)? = null

    private val updateSettingsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateSummaries()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.fragment_settings, this, true)
        setupViews()
        updateSummaries()
        setupAccountSection()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(updateSettingsReceiver, android.content.IntentFilter("com.hamham.gpsmover.UPDATE_SETTINGS"), android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(updateSettingsReceiver)
    }

    fun setViewModel(viewModel: MainViewModel) {
        this.viewModel = viewModel
        updateSummaries()
    }

    fun setOnSettingsChangedListener(listener: () -> Unit) {
        this.onSettingsChanged = listener
    }

    fun setOnBackClick(listener: () -> Unit) {
        this.onBackClick = listener
    }

    fun setOnAccountStateChangedListener(listener: () -> Unit) {
        onAccountStateChanged = listener
    }

    private fun setupViews() {
        // Dark Theme
        findViewById<MaterialButton>(R.id.dark_theme_button).setOnClickListener {
            it.performHapticClick()
            showDarkThemeDialog()
        }

        // Map Type
        findViewById<MaterialButton>(R.id.map_type_button).setOnClickListener {
            it.performHapticClick()
            showMapTypeDialog()
        }

        // Advanced Hook Switch
        findViewById<SwitchMaterial>(R.id.advance_hook_switch).apply {
            isChecked = PrefManager.isHookSystem
            setOnCheckedChangeListener { _, isChecked ->
                performHapticClick()
                PrefManager.isHookSystem = isChecked
                onSettingsChanged?.invoke()
            }
        }

        // Accuracy Settings
        findViewById<MaterialButton>(R.id.accuracy_button).setOnClickListener {
            it.performHapticClick()
            showAccuracyDialog()
        }

        // Random Position
        findViewById<MaterialButton>(R.id.random_position_button).setOnClickListener {
            it.performHapticClick()
            showRandomPositionDialog()
        }

        // تم حذف زر الاستيراد (import_button) وكل منطق الدايلوج المرتبط به نهائيًا
    }

    private fun setupAccountSection() {
        // تهيئة GoogleSignInClient دائماً
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)

        val signInButton = findViewById<MaterialButton>(R.id.sign_in_button)
        val signOutButton = findViewById<MaterialButton>(R.id.sign_out_button)
        val userInfoLayout = findViewById<LinearLayout>(R.id.user_info_layout)
        val userName = findViewById<TextView>(R.id.user_name)
        val userEmail = findViewById<TextView>(R.id.user_email)
        val userAvatar = findViewById<ImageView>(R.id.user_avatar)

        val user = firebaseAuth.currentUser
        if (user != null) {
            // User is signed in
            signInButton.visibility = View.GONE
            userInfoLayout.visibility = View.VISIBLE
            userName.text = user.displayName ?: ""
            userEmail.text = user.email ?: ""
            // Load avatar if available
            val photoUrl = user.photoUrl
            if (photoUrl != null) {
                // Use a simple thread to load the image (or use Glide/Picasso if available)
                Thread {
                    try {
                        val input = java.net.URL(photoUrl.toString()).openStream()
                        val bitmap = BitmapFactory.decodeStream(input)
                        val circularBitmap = getCircularBitmap(bitmap)
                        post { userAvatar.setImageBitmap(circularBitmap) }
                    } catch (e: Exception) {
                        // fallback to default
                        post { userAvatar.setImageResource(R.drawable.ic_account_circle) }
                    }
                }.start()
            } else {
                userAvatar.setImageResource(R.drawable.ic_account_circle)
            }
        } else {
            // Not signed in
            signInButton.visibility = View.VISIBLE
            userInfoLayout.visibility = View.GONE
        }

        signInButton.setOnClickListener {
            it.performHapticClick()
            val signInIntent = googleSignInClient!!.signInIntent
            if (context is Activity) {
                (context as Activity).startActivityForResult(signInIntent, RC_SIGN_IN)
            } else {
                Snackbar.make(this, "Sign-in only available in Activity context", Snackbar.LENGTH_SHORT).show()
            }
            onAccountStateChanged?.invoke()
        }

        signOutButton.setOnClickListener {
            it.performHapticClick()
            googleSignInClient?.signOut()?.addOnCompleteListener {
                googleSignInClient?.revokeAccess()?.addOnCompleteListener {
                    firebaseAuth.signOut()
                    val intent = Intent(context, MapActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    intent.putExtra("showLoginDialogImmediately", true)
                    context.startActivity(intent)
                    if (context is Activity) {
                        (context as Activity).finish()
                    }
                }
            }
        }
    }

    // Call this from Activity's onActivityResult
    fun handleSignInResult(requestCode: Int, data: Intent?) {
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                Snackbar.make(this, "Sign-in failed: ${e.localizedMessage}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    setupAccountSection() // Refresh UI
                } else {
                    Snackbar.make(this, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    fun updateSummaries() {
        // Dark Theme Summary
        val darkThemeSummary = when (PrefManager.darkTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> context.getString(R.string.light)
            AppCompatDelegate.MODE_NIGHT_YES -> context.getString(R.string.dark)
            else -> context.getString(R.string.system)
        }
        findViewById<TextView>(R.id.dark_theme_summary).text = darkThemeSummary

        // Map Type Summary
        val mapTypeSummary = when (PrefManager.mapType) {
            1 -> "Normal"
            2 -> "Satellite"
            3 -> "Terrain"
            else -> "Normal"
        }
        findViewById<TextView>(R.id.map_type_summary).text = mapTypeSummary

        // Accuracy Summary
        val accuracyValue = PrefManager.accuracy?.toFloatOrNull() ?: 0.0f
        val accuracySummary = if (accuracyValue > 0.0f) {
            "$accuracyValue m. (enabled)"
        } else {
            "Disabled"
        }
        findViewById<TextView>(R.id.accuracy_summary).text = accuracySummary

        // Random Position Summary
        val randomRange = PrefManager.randomPositionRange?.toDoubleOrNull() ?: 0.0
        val randomSummary = if (randomRange > 0.0) {
            "$randomRange m. (enabled)"
        } else {
            "Disabled"
        }
        findViewById<TextView>(R.id.random_position_summary).text = randomSummary
    }

    private fun showDarkThemeDialog() {
        val themes = arrayOf(context.getString(R.string.system), context.getString(R.string.light), context.getString(R.string.dark))
        val currentIndex = when (PrefManager.darkTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dark_theme))
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (PrefManager.darkTheme != newMode) {
                    PrefManager.darkTheme = newMode
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    onSettingsChanged?.invoke()
                }
                updateSummaries()
                dialog.dismiss()
            }
            .show()
    }

    private fun showMapTypeDialog() {
        val mapTypes = arrayOf("Normal", "Satellite", "Terrain")
        val currentIndex = PrefManager.mapType - 1

        MaterialAlertDialogBuilder(context)
            .setTitle("Map Type")
            .setSingleChoiceItems(mapTypes, currentIndex) { dialog, which ->
                PrefManager.mapType = which + 1
                viewModel?.updateMapType(PrefManager.mapType)
                onSettingsChanged?.invoke()
                updateSummaries()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAccuracyDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_accuracy, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.accuracy_edit)
        val okButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_action_button)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
        okButton.setOnClickListener {
            val newValue = editText.text.toString()
            if (newValue.isNotEmpty()) {
                PrefManager.accuracy = newValue
                onSettingsChanged?.invoke()
                updateSummaries()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showRandomPositionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_random_position, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.random_position_edit)
        val okButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_action_button)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()
        okButton.setOnClickListener {
            val newValue = editText.text.toString()
            if (newValue.isNotEmpty()) {
                PrefManager.randomPositionRange = newValue
                onSettingsChanged?.invoke()
                updateSummaries()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    // Helper function to make bitmap circular
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        path.addCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            Path.Direction.CCW
        )
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, (size - bitmap.width) / 2f, (size - bitmap.height) / 2f, paint)
        return output
    }
} 