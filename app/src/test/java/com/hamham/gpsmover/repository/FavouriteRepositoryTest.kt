package com.hamham.gpsmover.repository

import com.hamham.gpsmover.favorites.Favourite
import com.hamham.gpsmover.favorites.FavouriteDao
import com.hamham.gpsmover.favorites.FavouriteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class FavouriteRepositoryTest {
    private lateinit var favouriteDao: FavouriteDao
    private lateinit var repository: FavouriteRepository

    @Before
    fun setUp() {
        favouriteDao = mock()
        repository = FavouriteRepository(favouriteDao)
    }

    @Test
    fun testGetAllFavourites() {
        val favList = listOf(Favourite(1, "Test", 1.0, 2.0, 0))
        whenever(favouriteDao.getAllFavourites()).thenReturn(flowOf(favList))
        val result = repository.getAllFavourites
        assertNotNull(result)
    }

    @Test
    fun testAddNewFavourite() = runBlocking {
        val fav = Favourite(1, "Test", 1.0, 2.0, 0)
        whenever(favouriteDao.insertToRoomDatabase(fav)).thenReturn(1L)
        val id = repository.addNewFavourite(fav)
        assertEquals(1L, id)
    }

    @Test
    fun testDeleteFavourite() = runBlocking {
        val fav = Favourite(1, "Test", 1.0, 2.0, 0)
        whenever(favouriteDao.deleteSingleFavourite(fav)).thenReturn(Unit)
        repository.deleteFavourite(fav)
        verify(favouriteDao).deleteSingleFavourite(fav)
    }

    @Test
    fun testGetSingleFavourite() {
        val fav = Favourite(1, "Test", 1.0, 2.0, 0)
        whenever(favouriteDao.getSingleFavourite(1L)).thenReturn(fav)
        val result = repository.getSingleFavourite(1L)
        assertEquals(fav, result)
    }

    @Test
    fun testUpdateFavouritesOrder() = runBlocking {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteDao.updateFavourites(favList)).thenReturn(Unit)
        repository.updateFavouritesOrder(favList)
        verify(favouriteDao).updateFavourites(favList)
    }

    @Test
    fun testDeleteAllFavourites() = runBlocking {
        whenever(favouriteDao.deleteAllFavourites()).thenReturn(Unit)
        repository.deleteAllFavourites()
        verify(favouriteDao).deleteAllFavourites()
    }

    @Test
    fun testInsertAllFavourites() = runBlocking {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteDao.insertAll(favList)).thenReturn(Unit)
        repository.insertAllFavourites(favList)
        verify(favouriteDao).insertAll(favList)
    }

    @Test
    fun testReplaceAllFavourites() = runBlocking {
        val favList = listOf(Favourite(1, "Test1", 1.0, 2.0, 0), Favourite(2, "Test2", 3.0, 4.0, 1))
        whenever(favouriteDao.replaceAllFavourites(favList)).thenReturn(Unit)
        repository.replaceAllFavourites(favList)
        verify(favouriteDao).replaceAllFavourites(favList)
    }
} 