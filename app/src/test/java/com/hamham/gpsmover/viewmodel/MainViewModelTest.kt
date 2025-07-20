package com.hamham.gpsmover.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.hamham.gpsmover.favorites.Favourite
import com.hamham.gpsmover.favorites.FavouriteRepository
import com.hamham.gpsmover.xposed.XposedSelfHooks
import com.hamham.gpsmover.xposed.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class MainViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var favouriteRepository: FavouriteRepository
    private lateinit var prefManager: PrefManager
    private lateinit var downloadManager: android.app.DownloadManager
    private lateinit var context: android.content.Context
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        favouriteRepository = mock()
        prefManager = mock()
        downloadManager = mock()
        context = mock()
        
        // Mock PrefManager properties
        whenever(prefManager.getLat).thenReturn(0.0)
        whenever(prefManager.getLng).thenReturn(0.0)
        whenever(prefManager.isStarted).thenReturn(false)
        whenever(prefManager.mapType).thenReturn(1)
        whenever(prefManager.darkTheme).thenReturn(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        whenever(prefManager.accuracy).thenReturn("5")
        whenever(prefManager.isRandomPosition).thenReturn(false)
        whenever(prefManager.isHookSystem).thenReturn(false)
        
        viewModel = MainViewModel(
            favouriteRepository,
            prefManager,
            downloadManager,
            context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateLocation() = runTest {
        viewModel.update(true, 1.0, 2.0)
        verify(prefManager).update(true, 1.0, 2.0)
    }

    @Test
    fun testUpdateDarkTheme() {
        viewModel.updateDarkTheme("dark")
        verify(prefManager).darkTheme = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        assertEquals("dark", viewModel.darkTheme.value)
    }

    @Test
    fun testUpdateMapType() {
        viewModel.updateMapType(2)
        verify(prefManager).mapType = 2
    }

    @Test
    fun testUpdateAccuracy() {
        viewModel.updateAccuracy(10)
        verify(prefManager).accuracy = "10"
        assertEquals(10, viewModel.accuracy.value)
    }

    @Test
    fun testUpdateRandomPosition() {
        viewModel.updateRandomPosition(true)
        verify(prefManager).isRandomPosition = true
        assertEquals(true, viewModel.randomPosition.value)
    }

    @Test
    fun testUpdateAdvancedHook() {
        viewModel.updateAdvancedHook(true)
        verify(prefManager).isHookSystem = true
        assertEquals(true, viewModel.advancedHook.value)
    }

    @Test
    fun testDeleteFavourite() = runTest {
        val fav = Favourite(1, "Test", 1.0, 2.0, 0)
        whenever(favouriteRepository.deleteFavourite(fav)).thenReturn(Unit)
        viewModel.deleteFavourite(fav)
        verify(favouriteRepository).deleteFavourite(fav)
    }

    @Test
    fun testDeleteAllFavourites() = runTest {
        whenever(favouriteRepository.deleteAllFavourites()).thenReturn(Unit)
        viewModel.deleteAllFavourites()
        verify(favouriteRepository).deleteAllFavourites()
    }

    @Test
    fun testUpdateFavouritesOrder() = runTest {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteRepository.updateFavouritesOrder(favList)).thenReturn(Unit)
        viewModel.updateFavouritesOrder(favList)
        verify(favouriteRepository).updateFavouritesOrder(favList)
    }

    @Test
    fun testInsertFavourite() = runTest {
        val fav = Favourite(1, "Test", 1.0, 2.0, 0)
        whenever(favouriteRepository.addNewFavourite(fav)).thenReturn(1L)
        viewModel.insertFavourite(fav)
        verify(favouriteRepository).addNewFavourite(fav)
    }

    @Test
    fun testInsertAllFavourites() = runTest {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteRepository.insertAllFavourites(favList)).thenReturn(Unit)
        viewModel.insertAllFavourites(favList)
        verify(favouriteRepository).insertAllFavourites(favList)
    }

    @Test
    fun testReplaceAllFavourites() = runTest {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteRepository.replaceAllFavourites(favList)).thenReturn(Unit)
        viewModel.replaceAllFavourites(favList)
        verify(favouriteRepository).replaceAllFavourites(favList)
    }
} 