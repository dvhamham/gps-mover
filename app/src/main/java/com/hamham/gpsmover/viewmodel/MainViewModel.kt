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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.R
import com.hamham.gpsmover.favorites.Favourite
import com.hamham.gpsmover.favorites.FavouriteRepository
import com.hamham.gpsmover.update.UpdateChecker
import com.hamham.gpsmover.utils.PrefManager
import com.hamham.gpsmover.utils.ext.onMain
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
    private val updateChecker: UpdateChecker,
    private val downloadManager: DownloadManager,
    private val favouriteRepository: FavouriteRepository,
    @ApplicationContext context: Context
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

    // Favorites state
    val allFavList = favouriteRepository.getAllFavourites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun insertFavourite(favourite: Favourite) {
        viewModelScope.launch {
            favouriteRepository.addNewFavourite(favourite)
            syncFavoritesToFirestore()
        }
    }

    fun deleteFavourite(favourite: Favourite) {
        viewModelScope.launch {
            favouriteRepository.deleteFavourite(favourite)
            syncFavoritesToFirestore()
        }
    }

    fun updateFavouritesOrder(favourites: List<Favourite>) {
        viewModelScope.launch {
            favouriteRepository.updateFavouritesOrder(favourites)
            syncFavoritesToFirestore()
        }
    }

    fun replaceAllFavourites(favorites: List<Favourite>) {
        viewModelScope.launch {
            favouriteRepository.replaceAllFavourites(favorites)
            syncFavoritesToFirestore()
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
    fun update(start: Boolean, la: Double, ln: Double) = prefManger.update(start, la, ln)

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

    // Update checking and downloading
    private val _response = MutableLiveData<Long>()
    val response: LiveData<Long> = _response

    private val _update = MutableStateFlow<UpdateChecker.Update?>(null).apply {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                updateChecker.clearCachedDownloads(context)
            }
            updateChecker.getLatestRelease().collect {
                emit(it)
            }
        }
    }
    val update = _update.asStateFlow()

    fun getAvailableUpdate(): UpdateChecker.Update? = _update.value
    fun clearUpdate() {
        viewModelScope.launch {
            _update.emit(null)
        }
    }

    // Download handling
    private var requestId: Long? = null
    private var _downloadState = MutableStateFlow<State>(State.Idle)
    private var downloadFile: File? = null
    val downloadState = _downloadState.asStateFlow()

    fun startDownload(context: Context, update: UpdateChecker.Update) {
        if (_downloadState.value is State.Idle) {
            downloadUpdate(context, update.assetUrl, update.assetName)
        }
    }

    private val downloadStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            viewModelScope.launch {
                var success = false
                val query = DownloadManager.Query().apply {
                    setFilterById(requestId ?: return@apply)
                }
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (cursor.getInt(columnIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true
                    }
                }
                if (success && downloadFile != null) {
                    val outputUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", downloadFile!!)
                    _downloadState.emit(State.Done(outputUri))
                } else {
                    _downloadState.emit(State.Failed)
                }
            }
        }
    }

    private val downloadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            viewModelScope.launch {
                val query = DownloadManager.Query()
                query.setFilterById(requestId ?: return@launch)
                val c: Cursor = downloadManager.query(query)
                var progress = 0.0
                if (c.moveToFirst()) {
                    val size = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val downloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (size != -1) progress = downloaded * 100.0 / size
                }
                _downloadState.emit(State.Downloading(progress.roundToInt()))
            }
        }
    }

    private fun downloadUpdate(context: Context, url: String, fileName: String) = viewModelScope.launch {
        val downloadFolder = File(context.externalCacheDir, "updates").apply { mkdirs() }
        downloadFile = File(downloadFolder, fileName)
        ContextCompat.registerReceiver(
            context,
            downloadStateReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        context.contentResolver.registerContentObserver(Uri.parse("content://downloads/my_downloads"), true, downloadObserver)
        requestId = DownloadManager.Request(Uri.parse(url)).apply {
            setDescription(context.getString(R.string.download_manager_description))
            setTitle(context.getString(R.string.app_name))
            setDestinationUri(Uri.fromFile(downloadFile!!))
        }.run {
            downloadManager.enqueue(this)
        }
    }

    fun openPackageInstaller(context: Context, uri: Uri) {
        runCatching {
            Intent(Intent.ACTION_VIEW, uri).apply {
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.also { context.startActivity(it) }
        }.onFailure { it.printStackTrace() }
    }

    fun cancelDownload(context: Context) {
        viewModelScope.launch {
            requestId?.let { downloadManager.remove(it) }
            context.unregisterReceiver(downloadStateReceiver)
            context.contentResolver.unregisterContentObserver(downloadObserver)
            _downloadState.emit(State.Idle)
        }
    }

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
                        viewModelScope.launch { favouriteRepository.insertAllFavourites(favorites) }
                    }
                }
        }
    }

    private fun syncFavoritesToFirestore() {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser ?: return@launch
            val email = user.email ?: return@launch
            val favorites = favouriteRepository.getAllFavourites.first()
            FirebaseFirestore.getInstance().collection("favorites").document(email)
                .set(mapOf("list" to favorites))
        }
    }

    fun syncFavoritesFromFirestore() {
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
                        viewModelScope.launch { favouriteRepository.replaceAllFavourites(favorites) }
                    }
                }
        }
    }

    private var firestoreListener: ListenerRegistration? = null
    private fun startRealtimeFavoritesSync() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val email = user.email ?: return
        firestoreListener?.remove()
        firestoreListener = FirebaseFirestore.getInstance()
            .collection("favorites")
            .document(email)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                val favList = doc.get("list")
                if (favList is List<*>) {
                    val gson = com.google.gson.Gson()
                    val json = gson.toJson(favList)
                    val type = object : com.google.gson.reflect.TypeToken<List<Favourite>>() {}.type
                    val favorites: List<Favourite> = gson.fromJson(json, type)
                    viewModelScope.launch { favouriteRepository.replaceAllFavourites(favorites) }
                }
            }
    }

    init {
        startRealtimeFavoritesSync()
    }
}
