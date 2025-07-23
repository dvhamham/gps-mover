package com.hamham.gpsmover.feature.locations.domain.usecase

import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Use case for deleting a location
 */
class DeleteLocationUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(location: Location): Result<Unit> {
        return try {
            repository.deleteLocation(location)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
