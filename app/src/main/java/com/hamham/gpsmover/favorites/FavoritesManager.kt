package com.hamham.gpsmover.favorites

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.WorkerThread

// --- Entity representing a Favourite item in the Room database ---
@Entity
data class Favourite(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,              // Unique ID (auto-generated)
    val address: String?,              // Human-readable address
    val lat: Double?,                  // Latitude coordinate
    val lng: Double?,                  // Longitude coordinate
    val order: Int = 0                 // Custom order index for sorting
)

// --- Data Access Object (DAO) for Favourite operations ---
@Dao
interface FavouriteDao {

    // Insert a favourite; replace if already exists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToRoomDatabase(favourite: Favourite): Long

    // Update an existing favourite
    @Update
    suspend fun updateUserDetails(favourite: Favourite)

    // Delete a specific favourite
    @Delete
    suspend fun deleteSingleFavourite(favourite: Favourite)

    // Get all favourites ordered by `order` and then by id
    @Transaction
    @Query("SELECT * FROM favourite ORDER BY `order` ASC, id DESC")
    fun getAllFavourites(): Flow<List<Favourite>>

    // Get a single favourite by ID
    @Transaction
    @Query("SELECT * FROM favourite WHERE id = :id ORDER BY id DESC")
    fun getSingleFavourite(id: Long): Favourite

    // Update the order of a specific favourite
    @Query("UPDATE favourite SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    // Update multiple favourites
    @Update
    suspend fun updateFavourites(favourites: List<Favourite>)

    // Delete all favourites
    @Query("DELETE FROM favourite")
    suspend fun deleteAllFavourites()

    // Insert a list of favourites
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<Favourite>)

    // Replace all favourites (delete old, insert new)
    @Transaction
    suspend fun replaceAllFavourites(favorites: List<Favourite>) {
        deleteAllFavourites()
        insertAll(favorites)
    }
}

// --- Room Database containing the Favourite entity ---
@Database(entities = [Favourite::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favouriteDao(): FavouriteDao

    companion object {
        // Migration from version 1 to 2: add `order` column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favourite ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    // Optional database callback for pre-populating or setup
    class Callback @Inject constructor(
        private val applicationScope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {
                // Optional: Pre-populate database here
            }
        }
    }
}

// --- Repository for Favourite data operations ---
class FavouriteRepository @Inject constructor(
    private val favouriteDao: FavouriteDao
) {
    // Flow of all favourites to be observed (Live updates)
    val getAllFavourites: Flow<List<Favourite>> get() = favouriteDao.getAllFavourites()

    // Add a new favourite to the database
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun addNewFavourite(favourite: Favourite): Long {
        return favouriteDao.insertToRoomDatabase(favourite)
    }

    // Delete a single favourite
    suspend fun deleteFavourite(favourite: Favourite) {
        favouriteDao.deleteSingleFavourite(favourite)
    }

    // Get a single favourite by ID (non-suspending)
    fun getSingleFavourite(id: Long): Favourite {
        return favouriteDao.getSingleFavourite(id)
    }

    // Update the order of all favourites
    suspend fun updateFavouritesOrder(favourites: List<Favourite>) {
        favouriteDao.updateFavourites(favourites)
    }

    // Delete all favourites from the database
    suspend fun deleteAllFavourites() {
        favouriteDao.deleteAllFavourites()
    }

    // Insert a batch of favourites
    suspend fun insertAllFavourites(favorites: List<Favourite>) {
        favouriteDao.insertAll(favorites)
    }

    // Replace all favourites with a new list
    suspend fun replaceAllFavourites(favorites: List<Favourite>) {
        favouriteDao.replaceAllFavourites(favorites)
    }
}
