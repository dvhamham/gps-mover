package com.hamham.gpsmover.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.ProgressDialog
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.R
import com.hamham.gpsmover.adapter.FavListAdapter
import com.hamham.gpsmover.databinding.ActivityMapBinding
import com.hamham.gpsmover.utils.NotificationsChannel
import com.hamham.gpsmover.utils.ext.*
import com.hamham.gpsmover.viewmodel.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.properties.Delegates

@AndroidEntryPoint
class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    private lateinit var mMap: GoogleMap
    private val viewModel by viewModels<MainViewModel>()
    private val update by lazy { viewModel.getAvailableUpdate() }
    private val notificationsChannel by lazy { NotificationsChannel() }
    private var favListAdapter: FavListAdapter = FavListAdapter()
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat by Delegates.notNull<Double>()
    private var lon by Delegates.notNull<Double>()
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: AlertDialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        initializeMap()
        setFloatActionButton()
        isModuleEnable()
        updateChecker()

        // Add click listener for the star FAB to open favourites dialog
        findViewById<View>(R.id.star_fab).setOnClickListener {
            openFavouriteListDialog()
        }

        // Add click listener for expandable FAB
        findViewById<View>(R.id.expandable_fab).setOnClickListener {
            toggleExpandableFAB()
        }

        // Add click listeners for expandable FAB items
        findViewById<View>(R.id.settings_fab).setOnClickListener {
            val sheet = SettingsBottomSheet()
            sheet.show(supportFragmentManager, "SettingsBottomSheet")
            collapseExpandableFAB()
        }

        findViewById<View>(R.id.add_fav_fab).setOnClickListener {
            addFavouriteDialog()
            collapseExpandableFAB()
        }

        findViewById<View>(R.id.search_fab).setOnClickListener {
            searchDialog()
            collapseExpandableFAB()
        }
    }


    private fun initializeMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    private fun isModuleEnable(){
        viewModel.isXposed.observe(this){ isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed){
                xposedDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog).run {
                    setTitle(R.string.error_xposed_module_missing)
                    setMessage(R.string.error_xposed_module_missing_desc)
                    setCancelable(BuildConfig.DEBUG)
                    show()
                }
            }
        }

    }


    private fun setFloatActionButton() {
        if (viewModel.isStarted) {
            binding.start.visibility = View.GONE
            binding.stop.visibility = View.VISIBLE
        }

        binding.start.setOnClickListener {
            viewModel.update(true, lat, lon)
            mLatLng.let {
                mMarker?.position = it!!
            }
            mMarker?.isVisible = true
            binding.start.visibility = View.GONE
            binding.stop.visibility = View.VISIBLE
            lifecycleScope.launch {
                mLatLng?.getAddress(this@MapActivity)?.let { address ->
                    address.collect{ value ->
                        showStartNotification(value)
                    }
                }
            }
        }
        binding.stop.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
            }
            mMarker?.isVisible = false
            binding.stop.visibility = View.GONE
            binding.start.visibility = View.VISIBLE
            cancelNotification()
        }
    }


    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap){
            mapType = viewModel.mapType
            val zoom = 12.0f
            lat = viewModel.getLat
            lon  = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                mMarker = addMarker(
                    MarkerOptions().position(it!!).draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
                )
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            }
            uiSettings.isZoomControlsEnabled = false
            if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                mMap.isMyLocationEnabled = true;
            }else {
                val permList = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                ActivityCompat.requestPermissions(
                    this@MapActivity,
                    permList,
                    99
                )
            }
            setPadding(0,0,0,170)
            setOnMapClickListener(this@MapActivity)
            if (viewModel.isStarted){
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


    private fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat, lon)
            mLatLng.let { latLng ->
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng!!, 12.0f))
                mMarker?.apply {
                    position = latLng
                    isVisible = true
                    showInfoWindow()
                }
            }
        }

    }


    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
    }


    private fun aboutDialog(){
        alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
        layoutInflater.inflate(R.layout.about,null).apply {
            val  tittle = findViewById<TextView>(R.id.design_about_title)
            val  version = findViewById<TextView>(R.id.design_about_version)
            val  info = findViewById<TextView>(R.id.design_about_info)
            tittle.text = getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME
            info.text = getString(R.string.about_info)
        }.run {
            alertDialog.setView(this)
            alertDialog.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
       when(item.itemId){
            R.id.search -> searchDialog()
            R.id.add_fav -> addFavouriteDialog()
            R.id.get_favourite -> openFavouriteListDialog()
            R.id.settings -> {
                val sheet = SettingsBottomSheet()
                sheet.show(supportFragmentManager, "SettingsBottomSheet")
            }
            R.id.about -> aboutDialog()

            else -> super.onOptionsItemSelected(item)
        }
        return true

    }


    private fun searchDialog() {
        alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null)
        val editText = view.findViewById<EditText>(R.id.search_edittxt)
        val actionButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_action_button)
        editText.hint = getString(R.string.search_hint)
        actionButton.text = getString(R.string.search)
        val progressBar = ProgressDialog(this)
        progressBar.setMessage("Searching...")
        actionButton.setOnClickListener {
            if (isNetworkConnected()) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val getInput = editText.text.toString()
                    if (getInput.isNotEmpty()) {
                        getSearchAddress(getInput).let {
                            it.collect { result ->
                                when (result) {
                                    is SearchProgress.Progress -> {
                                        progressBar.show()
                                    }
                                    is SearchProgress.Complete -> {
                                        lat = result.lat
                                        lon = result.lon
                                        progressBar.dismiss()
                                        moveMapToNewLocation(true)
                                        dialog.dismiss()
                                    }
                                    is SearchProgress.Fail -> {
                                        progressBar.dismiss()
                                        showToast(result.error!!)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                showToast(getString(R.string.no_internet))
            }
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()
    }

    private fun addFavouriteDialog(){
        alertDialog =  MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
            val view = layoutInflater.inflate(R.layout.dialog_layout,null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
        val actionButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_action_button)
        editText.hint = getString(R.string.add_fav_dialog_title)
        actionButton.text = getString(R.string.dialog_button_add)
        actionButton.setOnClickListener {
                val s = editText.text.toString()
                if (!mMarker?.isVisible!!){
                  showToast("Location not select")
                }else{
                    viewModel.storeFavorite(s, lat, lon)
                    viewModel.response.observe(this@MapActivity){
                        if (it == (-1).toLong()) showToast("Can't save") else showToast("Save")
                    }
                dialog.dismiss()
            }
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()
    }


    private fun openFavouriteListDialog() {
        getAllUpdatedFavList()
        val favs = favListAdapter.currentList
        if (favs.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Empty")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        favListAdapter.onItemClick = {
            it.let {
                lat = it.lat!!
                lon = it.lng!!
                val selectedLatLng = LatLng(lat, lon)
                mLatLng = selectedLatLng
                viewModel.update(true, lat, lon)
                mMarker?.position = selectedLatLng
                mMarker?.isVisible = true
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 12.0f))
                binding.start.visibility = View.GONE
                binding.stop.visibility = View.VISIBLE
                lifecycleScope.launch {
                    mLatLng?.getAddress(this@MapActivity)?.let { address ->
                        address.collect { value ->
                            showStartNotification(value)
                        }
                    }
                }
            }
            if (dialog.isShowing) dialog.dismiss()
        }
        favListAdapter.onItemDelete = {
            viewModel.deleteFavourite(it)
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()
    }


    private fun getAllUpdatedFavList(){
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect {
                    favListAdapter.submitList(it)
                }
            }
        }
    }

    private var isExpanded = false

    private fun toggleExpandableFAB() {
        if (isExpanded) {
            collapseExpandableFAB()
        } else {
            expandFAB()
        }
    }

    private fun expandFAB() {
        val settingsFab = findViewById<View>(R.id.settings_fab)
        val addFavFab = findViewById<View>(R.id.add_fav_fab)
        val searchFab = findViewById<View>(R.id.search_fab)
        val expandableFab = findViewById<View>(R.id.expandable_fab)

        // Animate the FABs to appear
        settingsFab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .start()

        addFavFab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setStartDelay(100)
            .start()

        searchFab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setStartDelay(200)
            .start()

        // Rotate the main FAB
        expandableFab.animate()
            .rotation(180f)
            .setDuration(200)
            .start()

        isExpanded = true
    }

    private fun collapseExpandableFAB() {
        val settingsFab = findViewById<View>(R.id.settings_fab)
        val addFavFab = findViewById<View>(R.id.add_fav_fab)
        val searchFab = findViewById<View>(R.id.search_fab)
        val expandableFab = findViewById<View>(R.id.expandable_fab)

        // Animate the FABs to disappear
        searchFab.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .start()

        addFavFab.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .setStartDelay(100)
            .start()

        settingsFab.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .setStartDelay(200)
            .start()

        // Rotate the main FAB back
        expandableFab.animate()
            .rotation(0f)
            .setDuration(200)
            .start()

        isExpanded = false
    }


    private fun updateDialog(){
        alertDialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
        alertDialog.setTitle(R.string.update_available)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton(getString(R.string.update_button)) { _, _ ->
            MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(this@MapActivity)
                    dialog.dismiss()
                }
                lifecycleScope.launch {
                    viewModel.downloadState.collect {
                        when (it) {
                            is MainViewModel.State.Downloading -> {
                                if (it.progress > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = it.progress
                                }
                            }
                            is MainViewModel.State.Done -> {
                                viewModel.openPackageInstaller(this@MapActivity, it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }

                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    this@MapActivity,
                                    R.string.bs_update_download_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()

                            }
                            else -> {}
                        }
                    }
                }
                update?.let { it ->
                    viewModel.startDownload(this@MapActivity, it)
                } ?: run {
                    dialog.dismiss()
                }
            }.run {
                dialog = create()
                dialog.show()
            }
        }
        dialog = alertDialog.create()
        dialog.show()

    }

    private fun updateChecker(){
        lifecycleScope.launchWhenResumed {
            viewModel.update.collect{
                if (it!= null){
                    updateDialog()
                }
            }
        }
    }


    private suspend fun getSearchAddress(address: String) = callbackFlow {
        withContext(Dispatchers.IO){
            trySend(SearchProgress.Progress)
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)

            if (matcher.matches()){
                delay(3000)
                trySend(SearchProgress.Complete(matcher.group().split(",")[0].toDouble(),matcher.group().split(",")[1].toDouble()))
            }else {
                try {

                    val list: List<Address>? = Geocoder(this@MapActivity).getFromLocationName(address,5)
                    list?.let {
                        if (it.size == 1){
                            trySend(SearchProgress.Complete(list[0].latitude,list[1].longitude))
                        }else {
                            trySend(SearchProgress.Fail(getString(R.string.address_not_found)))
                        }
                    }
                } catch (io : IOException){
                    trySend(SearchProgress.Fail(getString(R.string.no_internet)))
                }
            }
        }

        awaitClose { this.cancel() }
    }




    private fun showStartNotification(address: String){
        notificationsChannel.showNotification(this){
            it.setSmallIcon(R.drawable.ic_stop)
            it.setContentTitle(getString(R.string.location_set))
            it.setContentText(address)
            it.setAutoCancel(true)
            it.setCategory(Notification.CATEGORY_EVENT)
            it.priority = NotificationCompat.PRIORITY_HIGH
        }

    }


    private fun cancelNotification(){
        notificationsChannel.cancelAllNotifications(this)
    }


}


sealed class SearchProgress {
    object Progress : SearchProgress()
    data class Complete(val lat: Double , val lon : Double) : SearchProgress()
    data class Fail(val error: String?) : SearchProgress()
}