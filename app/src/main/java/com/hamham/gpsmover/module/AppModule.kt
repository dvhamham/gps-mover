package com.hamham.gpsmover.module

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import androidx.room.Room
import com.hamham.gpsmover.xposed.PrefManager
import com.hamham.gpsmover.favorites.Favourite
import com.hamham.gpsmover.favorites.FavouriteDao
import com.hamham.gpsmover.favorites.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule{



    @Singleton
    @Provides
    fun provideDownloadManger(application: Application) =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager



    @Provides
    @Singleton
    fun provideDatabase(application: Application, callback: AppDatabase.Callback)
            = Room.databaseBuilder(application, AppDatabase::class.java, "user_database")
        .allowMainThreadQueries()
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .addCallback(callback)
        .fallbackToDestructiveMigration()
        .build()


    @Singleton
    @Provides
    fun providesUserDao(favouriteDatabase: AppDatabase) : FavouriteDao =
        favouriteDatabase.favouriteDao()

    @Singleton
    @Provides
    fun provideSettingRepo() : PrefManager =
        PrefManager

    @Provides
    @Singleton
    fun providesApplicationScope() = CoroutineScope(SupervisorJob())

    @Singleton
    @Provides
    fun provideFavouriteRepository(favouriteDao: FavouriteDao): com.hamham.gpsmover.favorites.FavouriteRepository =
        com.hamham.gpsmover.favorites.FavouriteRepository(favouriteDao)

}



