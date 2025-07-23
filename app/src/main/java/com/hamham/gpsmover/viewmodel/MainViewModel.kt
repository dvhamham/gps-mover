package com.hamham.gpsmover.viewmodel

import android.app.DownloadManager
import android.content.*
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.R
import com.hamham.gpsmover.favorites.Favourite
import com.hamham.gpsmover.favorites.FavouriteRepository
import com.hamham.gpsmover.xposed.PrefManager
import com.hamham.gpsmover.helpers.onMain
import com.hamham.gpsmover.xposed.XposedSelfHooks
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel responsible for managing app state and business logic.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefManger: PrefManager,
    private val downloadManager: DownloadManager,
    private val favouriteRepository: FavouriteRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Location and map settings
    val getLat = prefManger.getLat
    val getLng = prefManger.getLng
    val isStarted = MutableLiveData(prefManger.isStarted)
    val mapType = prefManger.mapType

    fun refreshIsStarted() {
        isStarted.value = prefManger.isStarted
    }

    // UI settings
    val darkTheme = MutableLiveData<String>().apply {
        value = when (prefManger.darkTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> "light"
            AppCompatDelegate.MODE_NIGHT_YES -> "dark"
            else -> "system"
        }
    }

    val accuracy = MutableLiveData(prefManger.accuracy?.toIntOrNull() ?: 5)
    val randomPosition = MutableLiveData(prefManger.isRandomPositionEnabled)
    val advancedHook = MutableLiveData(prefManger.isHookSystem)

    // Favorites state - now using Firestore directly via FavouriteRepository
    val allFavList = favouriteRepository.getAllFavourites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        // Initialize the repository listener when ViewModel is created
        favouriteRepository.initializeListener(context)
    }

    fun insertFavourite(favourite: Favourite) {
        viewModelScope.launch {
            favouriteRepository.addNewFavourite(context, favourite)
        }
    }

    fun deleteFavourite(favourite: Favourite) {
        viewModelScope.launch {
            favouriteRepository.deleteFavourite(context, favourite)
        }
    }

    fun updateFavouritesOrder(favourites: List<Favourite>) {
        viewModelScope.launch {
            favouriteRepository.updateFavouritesOrder(context, favourites)
        }
    }

    fun replaceAllFavourites(favorites: List<Favourite>) {
        viewModelScope.launch {
            favouriteRepository.replaceAllFavourites(context, favorites)
        }
    }

    // Xposed module status
    val isXposed = MutableLiveData<Boolean>()
    fun updateXposedState() {
        onMain {
            isXposed.value = XposedSelfHooks.isXposedModuleEnabled()
        }
    }

    // Settings update methods
    fun update(start: Boolean, la: Double, ln: Double) {
        prefManger.update(start, la, ln)
        isStarted.value = start
    }

    fun updateDarkTheme(theme: String) {
        val themeMode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        prefManger.darkTheme = themeMode
        darkTheme.value = theme
    }

    fun updateMapType(type: Int) {
        prefManger.mapType = type
    }

    fun updateAccuracy(acc: Int) {
        prefManger.accuracy = acc.toString()
        accuracy.value = acc
    }

    fun updateRandomPosition(enabled: Boolean) {
        if (!enabled) {
            prefManger.randomPositionRange = "0"
        } else if ((prefManger.randomPositionRange?.toDoubleOrNull() ?: 0.0) == 0.0) {
            prefManger.randomPositionRange = "2"
        }
        randomPosition.value = prefManger.isRandomPositionEnabled
    }

    fun updateAdvancedHook(enabled: Boolean) {
        prefManger.isHookSystem = enabled
        advancedHook.value = enabled
    }

    // Download handling
    // Remove all code below related to update/download logic

    // Download state sealed class
    sealed class State {
        object Idle : State()
        data class Downloading(val progress: Int) : State()
        data class Done(val fileUri: Uri) : State()
        object Failed : State()
    }

    // Firestore syncing
    fun migrateFavoritesFromFirestoreToRoom() {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser ?: return@launch
            val email = user.email ?: return@launch
            FirebaseFirestore.getInstance().collection("favorites").document(email).get()
                .addOnSuccessListener { doc ->
                    val favList = doc.get("list")
                    if (favList is List<*>) {
                        val gson = com.google.gson.Gson()
                        val json = gson.toJson(favList)
                        val type = object : com.google.gson.reflect.TypeToken<List<Favourite>>() {}.type
                        val favorites: List<Favourite> = gson.fromJson(json, type)
                        viewModelScope.launch { favouriteRepository.insertAllFavourites(context, favorites) }
                    }
                }
        }
    }

    val moveToLatLng = MutableLiveData<LatLng?>()
    fun moveToLocation(lat: Double, lng: Double) {
        moveToLatLng.value = LatLng(lat, lng)
    }

    var lastCameraLatLng: LatLng? = null
    var lastCameraZoom: Float? = null
    var _justMovedToFavorite: Boolean = false

    override fun onCleared() {
        super.onCleared()
        // Stop listening to favourites when ViewModel is cleared
        favouriteRepository.stopListener()
    }
}
