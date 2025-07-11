package com.hamham.gpsmover.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Database(entities = [Favourite::class], version = 1,exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favouriteDao(): FavouriteDao
    class Callback @Inject constructor(private val applicationScope: CoroutineScope) : RoomDatabase.Callback(){
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {

            }
        }
    }
}