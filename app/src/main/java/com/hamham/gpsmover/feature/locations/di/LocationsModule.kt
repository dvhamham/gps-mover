package com.hamham.gpsmover.feature.locations.di

import com.hamham.gpsmover.feature.locations.data.repository.LocationRepositoryImpl
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for locations feature dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationsModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository
}
