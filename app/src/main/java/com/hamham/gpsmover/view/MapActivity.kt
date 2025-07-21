package com.hamham.gpsmover.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.R
import com.hamham.gpsmover.databinding.ActivityMapBinding
import com.hamham.gpsmover.favorites.FavoritesPage
import com.hamham.gpsmover.helpers.SnackbarType
import com.hamham.gpsmover.helpers.checkSinglePermission
import com.hamham.gpsmover.helpers.performHapticClick
import com.hamham.gpsmover.helpers.showCustomSnackbar
import com.hamham.gpsmover.modules.*
import com.hamham.gpsmover.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

@AndroidEntryPoint
class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    // --- Main Variables ---
    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    private lateinit var mMap: GoogleMap
    val viewModel by viewModels<MainViewModel>()
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    var lat by Delegates.notNull<Double>()
    var lon by Delegates.notNull<Double>()
    private var xposedDialog: MaterialAlertDialogBuilder? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: androidx.appcompat.app.AlertDialog
    private var settingsPageInstance: SettingsPage? = null
    private var isFavoritesPageInitialized = false
    private var currentPage = "map"

    // --- Broadcast Receivers ---
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread { viewModel.refreshIsStarted() }
        }
    }
    private val updateSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runOnUiThread {
                val settingsPage = settingsPageInstance
                if (settingsPage != null && settingsPage.visibility == View.VISIBLE) {
                    settingsPage.updateSummaries()
                }
            }
        }
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        // Startup sequence
        DbManager.checkAndMigrateDatabase(this)
        DeviceManager.updateDeviceInfo(this)
        UpdateManager.checkUpdate(this) {
            RulesManager.applicationDisabled(this) {
                // Ban check will be handled in onResume
            }
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        initializeMap()
        setFloatActionButton()
        isModuleEnable()
        setupSearchBar()
        setupBottomNavigation()
        setupFavoritesPage()
        val mapContainer = findViewById<View>(R.id.map_container)
        mapContainer.findViewById<View>(R.id.add_fav_fab).setOnClickListener {
            it.performHapticClick()
            addFavouriteDialog()
        }
        mapContainer.findViewById<View>(R.id.my_location_fab).setOnClickListener {
            it.performHapticClick()
            moveToMyRealLocation()
        }
        when (currentPage) {
            "map" -> showMapPage()
            "favorites" -> showFavoritesPage()
            "settings" -> showSettingsPage()
            else -> showMapPage()
        }
        viewModel.isStarted.observe(this) { setFloatActionButton() }
    }
    override fun onResume() {
        super.onResume()
        DeviceManager.checkBanStatus(this)
    }
    override fun onPause() {
        super.onPause()
        dismissAllDialogs()
        try { unregisterReceiver(updateReceiver) } catch (_: IllegalArgumentException) {}
        try { unregisterReceiver(updateSettingsReceiver) } catch (_: IllegalArgumentException) {}
    }
    override fun onDestroy() {
        super.onDestroy()
        dismissAllDialogs()
        try { unregisterReceiver(updateReceiver) } catch (_: IllegalArgumentException) {}
        try { unregisterReceiver(updateSettingsReceiver) } catch (_: IllegalArgumentException) {}
    }

    // --- UI Functions ---
    /** Safely dismiss all open dialogs. */
    private fun dismissAllDialogs() {
        try {
            xposedDialog?.create()?.dismiss()
            xposedDialog = null
            if (::dialog.isInitialized && dialog.isShowing) {
                dialog.dismiss()
            }
            if (::alertDialog.isInitialized) {
                try {
                    alertDialog.create().dismiss()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    /** Setup the bottom navigation bar and link to page switching. */
    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            bottomNavigation.performHapticClick()
            when (menuItem.itemId) {
                R.id.navigation_map -> {
                    showMapPage()
                    true
                }
                R.id.navigation_favorites -> {
                    showFavoritesPage()
                    true
                }
                R.id.navigation_settings -> {
                    showSettingsPage()
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
    /** Navigate to the map page. */
    private fun navigateToMapPage() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNavigation.selectedItemId != R.id.navigation_map) {
            bottomNavigation.selectedItemId = R.id.navigation_map
        }
        showMapPage()
    }
    /** Show the map page and hide others. */
    private fun showMapPage() {
        currentPage = "map"
        val mapContainer = findViewById<View>(R.id.map_container)
        val favoritesPage = findViewById<View>(R.id.favorites_page)
        val settingsPage = findViewById<View>(R.id.settings_page)
        favoritesPage.animate().alpha(0f).setDuration(300).withEndAction { favoritesPage.visibility = View.GONE }.start()
        settingsPage.animate().alpha(0f).setDuration(300).withEndAction { settingsPage.visibility = View.GONE }.start()
        mapContainer.visibility = View.VISIBLE
        mapContainer.alpha = 0f
        mapContainer.animate().alpha(1f).setDuration(300).start()
        mapContainer.findViewById<View>(R.id.start).visibility = View.VISIBLE
        mapContainer.findViewById<View>(R.id.stop).visibility = if (viewModel.isStarted.value == true) View.VISIBLE else View.GONE
        mapContainer.findViewById<View>(R.id.add_fav_fab).visibility = View.VISIBLE
        mapContainer.findViewById<View>(R.id.my_location_fab).visibility = View.VISIBLE
        setFloatActionButton()
    }
    /** Show the favorites page and hide others. */
    fun showFavoritesPage() {
        currentPage = "favorites"
        val mapContainer = findViewById<View>(R.id.map_container)
        val favoritesPage = findViewById<View>(R.id.favorites_page)
        val settingsPage = findViewById<View>(R.id.settings_page)
        mapContainer.animate().alpha(0f).setDuration(300).withEndAction { mapContainer.visibility = View.GONE }.start()
        settingsPage.animate().alpha(0f).setDuration(300).withEndAction { settingsPage.visibility = View.GONE }.start()
        favoritesPage.visibility = View.VISIBLE
        favoritesPage.alpha = 0f
        favoritesPage.animate().alpha(1f).setDuration(300).start()
        mapContainer.findViewById<View>(R.id.start).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.stop).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.add_fav_fab).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.my_location_fab).visibility = View.GONE
    }
    /** Show the settings page and hide others. */
    private fun showSettingsPage() {
        currentPage = "settings"
        val mapContainer = findViewById<View>(R.id.map_container)
        val favoritesPage = findViewById<View>(R.id.favorites_page)
        val settingsPage = findViewById<View>(R.id.settings_page)
        mapContainer.animate().alpha(0f).setDuration(300).withEndAction { mapContainer.visibility = View.GONE }.start()
        favoritesPage.animate().alpha(0f).setDuration(300).withEndAction { favoritesPage.visibility = View.GONE }.start()
        settingsPage.visibility = View.VISIBLE
        settingsPage.alpha = 0f
        settingsPage.animate().alpha(1f).setDuration(300).start()
        mapContainer.findViewById<View>(R.id.start).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.stop).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.add_fav_fab).visibility = View.GONE
        mapContainer.findViewById<View>(R.id.my_location_fab).visibility = View.GONE
        setupSettingsPage()
        settingsPageInstance?.updateSummaries()
    }
    /** Setup the favorites page by linking it to the ViewModel and handling item clicks. */
    private fun setupFavoritesPage() {
        val favoritesPage = findViewById<FavoritesPage>(R.id.favorites_page)
        favoritesPage.setViewModel(viewModel, lifecycleScope)
        favoritesPage.setOnFavoriteClick { favourite ->
            navigateToMapPage()
            lat = favourite.lat ?: 0.0
            lon = favourite.lng ?: 0.0
            val selectedLatLng = LatLng(lat, lon)
            mLatLng = selectedLatLng
            viewModel.update(true, lat, lon)
            mMarker?.position = selectedLatLng
            mMarker?.isVisible = true
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 12.0f))
            setFloatActionButton()
        }
    }
    /** Setup the settings page and link to ViewModel. */
    private fun setupSettingsPage() {
        val settingsPage = findViewById<SettingsPage>(R.id.settings_page)
        settingsPage.setViewModel(viewModel)
        settingsPage.setOnSettingsChangedListener {
            if (::mMap.isInitialized) {
                mMap.mapType = viewModel.mapType
            }
        }
        settingsPage.setOnBackClick {
            navigateToMapPage()
        }
        settingsPageInstance = settingsPage
    }
    /** Initialize the map fragment and get the map asynchronously. */
    private fun initializeMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }
    /** Check if Xposed module is enabled and show a warning if not. */
    private fun isModuleEnable() {
        viewModel.isXposed.observe(this) { isXposed ->
            xposedDialog?.create()?.dismiss()
            xposedDialog = null
            if (!isXposed) {
                xposedDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error_xposed_module_missing)
                    .setMessage(R.string.error_xposed_module_missing_desc)
                    .setCancelable(BuildConfig.DEBUG)
                xposedDialog?.create()?.show()
            }
        }
    }
    /** Setup floating action buttons (start/stop) and link to events. */
    private fun setFloatActionButton() {
        val mapContainer = findViewById<View>(R.id.map_container)
        val startButton = mapContainer.findViewById<View>(R.id.start)
        val stopButton = mapContainer.findViewById<View>(R.id.stop)
        if (viewModel.isStarted.value == true) {
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
        } else {
            startButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
        }
        startButton.setOnClickListener {
            it.performHapticClick()
            viewModel.update(true, lat, lon)
            mLatLng.let {
                mMarker?.position = it!!
            }
            mMarker?.isVisible = true
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
        }
        stopButton.setOnClickListener {
            it.performHapticClick()
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
            }
            mMarker?.isVisible = false
            stopButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE
        }
    }
    /** Setup the search bar and link to search logic. */
    private fun setupSearchBar() {
        val mapContainer = findViewById<View>(R.id.map_container)
        val searchEditText = mapContainer.findViewById<EditText>(R.id.search_edit_text)
        val searchSendButton = mapContainer.findViewById<ImageButton>(R.id.search_send_button)
        val doSearch: () -> Unit = {
            val query = searchEditText.text.toString()
            SearchManager.performSearch(
                this,
                query,
                onSuccess = { latLng ->
                    lat = latLng.latitude
                    lon = latLng.longitude
                    moveMapToNewLocation()
                    searchEditText.text?.clear()
                },
                onError = { message ->
                    showCustomSnackbar(message, SnackbarType.ERROR)
                }
            )
        }
        searchSendButton.setOnClickListener { doSearch() }
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                doSearch()
                true
            } else {
                false
            }
        }
    }
    /** Show a dialog to add a location to favorites. */
    private fun addFavouriteDialog() {
        dismissAllDialogs()
        val view = layoutInflater.inflate(R.layout.dialog_layout, null)
        val editText = view.findViewById<TextInputEditText>(R.id.search_edittxt)
        val actionButton = view.findViewById<MaterialButton>(R.id.dialog_action_button)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        actionButton.text = getString(R.string.dialog_button_add)
        actionButton.setOnClickListener {
            val s = editText.text.toString()
            if (!mMarker?.isVisible!!) {
                showCustomSnackbar("Location not select", SnackbarType.ERROR)
            } else {
                val fav = com.hamham.gpsmover.favorites.Favourite(
                    id = System.currentTimeMillis(),
                    address = s,
                    lat = lat,
                    lng = lon,
                    order = viewModel.allFavList.value.size
                )
                viewModel.insertFavourite(fav)
                dismissAllDialogs()
            }
        }
        alertDialog = MaterialAlertDialogBuilder(this)
            .setView(view)
        dialog = alertDialog.create()
        try {
            if (!isFinishing && !isDestroyed) {
                dialog.show()
            }
        } catch (e: Exception) {
            showCustomSnackbar("Failed to show dialog", SnackbarType.ERROR)
        }
    }
    /** Move to the user's real location on the map. */
    private fun moveToMyRealLocation() {
        if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (::mMap.isInitialized) {
                if (!mMap.isMyLocationEnabled) {
                    mMap.isMyLocationEnabled = true
                }
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val realLocation = LatLng(it.latitude, it.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(realLocation, 15.0f))
                    } ?: run {
                        showCustomSnackbar("Cannot get your current location", SnackbarType.ERROR)
                    }
                }.addOnFailureListener {
                    showCustomSnackbar("Failed to get your current location", SnackbarType.ERROR)
                }
            }
        } else {
            val permList = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this, permList, 99)
            showCustomSnackbar("Location permission required", SnackbarType.INFO)
        }
    }

    // --- Map Logic Functions ---
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap) {
            mapType = viewModel.mapType
            val zoom = 12.0f
            lat = viewModel.getLat
            lon = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                mMarker = addMarker(
                    MarkerOptions().position(it!!).draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            }
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mMap.isMyLocationEnabled = true
            } else {
                val permList = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                ActivityCompat.requestPermissions(
                    this@MapActivity,
                    permList,
                    99
                )
            }
            setPadding(0, 0, 0, 170)
            setOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted.value == true) {
                mMarker?.let {
                    it.isVisible = true
                    it.showInfoWindow()
                }
            }
        }
    }
    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng
        mMarker?.let { marker ->
            mLatLng.let {
                marker.position = it!!
                marker.isVisible = true
                mMap.animateCamera(CameraUpdateFactory.newLatLng(it))
                lat = it.latitude
                lon = it.longitude
            }
        }
    }
    fun moveMapToNewLocation() {
        mLatLng = LatLng(lat, lon)
        mLatLng?.let { latLng ->
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))
            mMarker?.apply {
                position = latLng
                isVisible = true
                showInfoWindow()
            }
        }
    }
}