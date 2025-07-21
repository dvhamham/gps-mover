package com.hamham.gpsmover.view

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hamham.gpsmover.R
import com.hamham.gpsmover.helpers.SnackbarType
import com.hamham.gpsmover.helpers.checkSinglePermission
import com.hamham.gpsmover.helpers.performHapticClick
import com.hamham.gpsmover.helpers.showCustomSnackbar
import com.hamham.gpsmover.modules.SearchManager
import com.hamham.gpsmover.viewmodel.MainViewModel

class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var mMap: GoogleMap
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat: Double = 0.0
    private var lon: Double = 0.0
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: androidx.appcompat.app.AlertDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.map_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeMap()
        setupUI(view)
        viewModel.isStarted.observe(viewLifecycleOwner) { setFloatActionButton(view) }
        // Observe moveToLatLng to move map when a favorite is clicked
        viewModel.moveToLatLng.observe(viewLifecycleOwner) { latLng ->
            if (latLng != null && ::mMap.isInitialized) {
                val targetZoom = 3.0f
                android.widget.Toast.makeText(requireContext(), "Moving to: ${latLng.latitude}, ${latLng.longitude} zoom: $targetZoom", android.widget.Toast.LENGTH_SHORT).show()
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, targetZoom))
                if (mMarker == null) {
                    mMarker = mMap.addMarker(
                        MarkerOptions().position(latLng).draggable(false)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(true)
                    )
                } else {
                    mMarker?.position = latLng
                    mMarker?.isVisible = true
                }
                mMarker?.showInfoWindow()
                viewModel.lastCameraLatLng = latLng
                viewModel.lastCameraZoom = targetZoom
                viewModel._justMovedToFavorite = true
            }
        }
    }

    private fun setupUI(view: View) {
        setFloatActionButton(view)
        setupSearchBar(view)
        view.findViewById<View>(R.id.add_fav_fab).setOnClickListener {
            it.performHapticClick()
            addFavouriteDialog()
        }
        view.findViewById<View>(R.id.my_location_fab).setOnClickListener {
            it.performHapticClick()
            moveToMyRealLocation()
        }
    }

    private fun initializeMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    private fun setFloatActionButton(view: View) {
        val startButton = view.findViewById<View>(R.id.start)
        val stopButton = view.findViewById<View>(R.id.stop)
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
            mLatLng.let { mMarker?.position = it!! }
            mMarker?.isVisible = true
            startButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
        }
        stopButton.setOnClickListener {
            it.performHapticClick()
            mLatLng.let { viewModel.update(false, it!!.latitude, it.longitude) }
            mMarker?.isVisible = false
            stopButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE
        }
    }

    private fun setupSearchBar(view: View) {
        val searchEditText = view.findViewById<EditText>(R.id.search_edit_text)
        val searchSendButton = view.findViewById<ImageButton>(R.id.search_send_button)
        val doSearch: () -> Unit = {
            val query = searchEditText.text.toString()
            SearchManager.performSearch(
                requireActivity(),
                query,
                onSuccess = { latLng ->
                    lat = latLng.latitude
                    lon = latLng.longitude
                    moveMapToNewLocation()
                    searchEditText.text?.clear()
                },
                onError = { message ->
                    requireActivity().showCustomSnackbar(message, SnackbarType.ERROR)
                }
            )
        }
        searchSendButton.setOnClickListener { doSearch() }
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                doSearch()
                true
            } else { false }
        }
    }

    private fun addFavouriteDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_layout, null)
        val editText = view.findViewById<TextInputEditText>(R.id.search_edittxt)
        val actionButton = view.findViewById<MaterialButton>(R.id.dialog_action_button)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        actionButton.text = getString(R.string.dialog_button_add)
        actionButton.setOnClickListener {
            val s = editText.text.toString()
            if (mMarker?.isVisible != true) {
                requireActivity().showCustomSnackbar("Location not select", SnackbarType.ERROR)
            } else {
                val fav = com.hamham.gpsmover.favorites.Favourite(
                    id = System.currentTimeMillis(),
                    address = s,
                    lat = lat,
                    lng = lon,
                    order = viewModel.allFavList.value.size
                )
                viewModel.insertFavourite(fav)
                dialog.dismiss()
            }
        }
        alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
        dialog = alertDialog.create()
        dialog.show()
    }

    private fun moveToMyRealLocation() {
        if (requireContext().checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (::mMap.isInitialized && mMap.isMyLocationEnabled.not()) {
                mMap.isMyLocationEnabled = true
            }
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val realLocation = LatLng(it.latitude, it.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(realLocation, 15.0f))
                } ?: run {
                    requireActivity().showCustomSnackbar("Cannot get your current location", SnackbarType.ERROR)
                }
            }.addOnFailureListener {
                requireActivity().showCustomSnackbar("Failed to get your current location", SnackbarType.ERROR)
            }
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99)
            requireActivity().showCustomSnackbar("Location permission required", SnackbarType.INFO)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        with(mMap) {
            mapType = viewModel.mapType
            val targetLatLng = viewModel.lastCameraLatLng ?: LatLng(viewModel.getLat, viewModel.getLng)
            val targetZoom = viewModel.lastCameraZoom ?: 12.0f
            mLatLng = targetLatLng
            lat = targetLatLng.latitude
            lon = targetLatLng.longitude
            mMarker = addMarker(
                MarkerOptions().position(targetLatLng).draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
            )
            // إذا كان هناك انتقال لمفضلة للتو، لا تحرك الكاميرا إطلاقًا
            if (viewModel._justMovedToFavorite) {
                viewModel._justMovedToFavorite = false
                // لا تحرك الكاميرا إطلاقًا
            } else {
                moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, targetZoom))
            }
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            if (requireContext().checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                isMyLocationEnabled = true
            } else {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99)
            }
            setPadding(0, 0, 0, 170)
            setOnMapClickListener(this@MapFragment)
            if (viewModel.isStarted.value == true) {
                mMarker?.isVisible = true
                mMarker?.showInfoWindow()
            }
            setOnCameraIdleListener {
                val pos = mMap.cameraPosition
                viewModel.lastCameraLatLng = pos.target
                viewModel.lastCameraZoom = pos.zoom
            }
            // Move to favorite location if requested
            viewModel.moveToLatLng.value?.let { favLatLng ->
                lat = favLatLng.latitude
                lon = favLatLng.longitude
                mLatLng = favLatLng
                animateCamera(CameraUpdateFactory.newLatLngZoom(favLatLng, 12.0f))
                if (mMarker == null) {
                    mMarker = addMarker(
                        MarkerOptions().position(favLatLng).draggable(false)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(true)
                    )
                } else {
                    mMarker?.position = favLatLng
                    mMarker?.isVisible = true
                }
                mMarker?.showInfoWindow()
            }
        }
    }

    override fun onMapClick(latLng: LatLng) {
        mLatLng = latLng
        mMarker?.let { marker ->
            marker.position = latLng
            marker.isVisible = true
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
            lat = latLng.latitude
            lon = latLng.longitude
        }
    }

    private fun moveMapToNewLocation() {
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