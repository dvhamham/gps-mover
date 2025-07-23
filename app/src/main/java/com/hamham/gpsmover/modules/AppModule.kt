package com.hamham.gpsmover.modules

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.hamham.gpsmover.xposed.PrefManager
import com.hamham.gpsmover.feature.locations.compat.FavouriteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideDownloadManger(application: Application) =
        application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    @Singleton
    @Provides
    fun provideSettingRepo(): PrefManager =
        PrefManager

    @Provides
    @Singleton
    fun providesApplicationScope() = CoroutineScope(SupervisorJob())

    @Singleton
    @Provides
    fun provideFavouriteRepository(locationRepository: com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository): FavouriteRepository =
        FavouriteRepository(locationRepository)

    @Singleton
    @Provides
    fun provideFirebaseFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance()
}



