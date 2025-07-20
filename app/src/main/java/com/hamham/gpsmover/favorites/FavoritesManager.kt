package com.hamham.gpsmover.favorites

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.WorkerThread

@Entity
data class Favourite(
    @PrimaryKey(autoGenerate = false)
    val id: Long? = null,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val order: Int = 0
)

@Dao
interface FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertToRoomDatabase(favourite: Favourite) : Long

    @Update
    suspend fun updateUserDetails(favourite: Favourite)

    @Delete
    suspend fun deleteSingleFavourite(favourite: Favourite)

    @Transaction
    @Query("SELECT * FROM favourite ORDER BY `order` ASC, id DESC")
    fun getAllFavourites() : Flow<List<Favourite>>

    @Transaction
    @Query("SELECT * FROM favourite WHERE id = :id ORDER BY id DESC")
    fun getSingleFavourite(id: Long) : Favourite

    @Query("UPDATE favourite SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Update
    suspend fun updateFavourites(favourites: List<Favourite>)

    @Query("DELETE FROM favourite")
    suspend fun deleteAllFavourites()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<Favourite>)

    @Transaction
    suspend fun replaceAllFavourites(favorites: List<Favourite>) {
        deleteAllFavourites()
        insertAll(favorites)
    }
}

@Database(entities = [Favourite::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favouriteDao(): FavouriteDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favourite ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
    class Callback @Inject constructor(private val applicationScope: CoroutineScope) : RoomDatabase.Callback(){
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {
                // Optional: prepopulate
            }
        }
    }
}

class FavouriteRepository @Inject constructor(private val favouriteDao: FavouriteDao) {
    val getAllFavourites: Flow<List<Favourite>> get() =  favouriteDao.getAllFavourites()

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun addNewFavourite(favourite: Favourite) : Long {
        return favouriteDao.insertToRoomDatabase(favourite)
    }

    suspend fun deleteFavourite(favourite: Favourite) {
        favouriteDao.deleteSingleFavourite(favourite)
    }

    fun getSingleFavourite(id: Long) : Favourite {
        return favouriteDao.getSingleFavourite(id)
    }

    suspend fun updateFavouritesOrder(favourites: List<Favourite>) {
        favouriteDao.updateFavourites(favourites)
    }

    suspend fun deleteAllFavourites() {
        favouriteDao.deleteAllFavourites()
    }

    suspend fun insertAllFavourites(favorites: List<Favourite>) {
        favouriteDao.insertAll(favorites)
    }

    suspend fun replaceAllFavourites(favorites: List<Favourite>) {
        favouriteDao.replaceAllFavourites(favorites)
    }
} 