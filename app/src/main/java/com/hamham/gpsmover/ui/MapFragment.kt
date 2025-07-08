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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private var _binding: ActivityMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private val notificationsChannel by lazy { NotificationsChannel() }
    private var favListAdapter: FavListAdapter = FavListAdapter()
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat by Delegates.notNull<Double>()
    private var lon by Delegates.notNull<Double>()
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: AlertDialog
    private val viewModel: com.hamham.gpsmover.viewmodel.MainViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = ActivityMapBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeMap()
        isModuleEnable()
        // updateChecker() // TODO: implement if needed
    }

    private fun initializeMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    private fun isModuleEnable(){
        // implement as needed
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        // implement as needed
    }

    override fun onMapClick(latLng: LatLng) {
        // implement as needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 