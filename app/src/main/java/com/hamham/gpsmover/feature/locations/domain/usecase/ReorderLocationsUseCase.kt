package com.hamham.gpsmover.feature.locations.domain.usecase

import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Use case for reordering locations
 */
class ReorderLocationsUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(locations: List<Location>): Result<Unit> {
        return try {
            // Update order indices
            val reorderedLocations = locations.mapIndexed { index, location ->
                location.withOrder(index)
            }
            repository.updateLocationsOrder(reorderedLocations)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
