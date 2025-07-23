package com.hamham.gpsmover.feature.locations.domain.usecase

import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Use case for saving a location
 */
class SaveLocationUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(location: Location): Result<Unit> {
        return try {
            if (!location.isValidLocation()) {
                Result.failure(InvalidLocationException("Location coordinates or name are invalid"))
            } else {
                repository.saveLocation(location)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class InvalidLocationException(message: String) : Exception(message)
