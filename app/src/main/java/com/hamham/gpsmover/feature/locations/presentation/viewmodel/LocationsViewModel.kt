package com.hamham.gpsmover.feature.locations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.usecase.DeleteLocationUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.GetLocationsUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.ReorderLocationsUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.SaveLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for locations feature
 */
@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val getLocationsUseCase: GetLocationsUseCase,
    private val saveLocationUseCase: SaveLocationUseCase,
    private val deleteLocationUseCase: DeleteLocationUseCase,
    private val reorderLocationsUseCase: ReorderLocationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationsUiState())
    val uiState: StateFlow<LocationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LocationsEvent>()
    val events = _events.asSharedFlow()

    val locations = getLocationsUseCase().stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveLocation(location: Location) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            saveLocationUseCase(location)
                .onSuccess {
                    _events.emit(LocationsEvent.LocationSaved)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _events.emit(LocationsEvent.Error(error.message ?: "Failed to save location"))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun deleteLocation(location: Location) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            deleteLocationUseCase(location)
                .onSuccess {
                    _events.emit(LocationsEvent.LocationDeleted)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _events.emit(LocationsEvent.Error(error.message ?: "Failed to delete location"))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun reorderLocations(locations: List<Location>) {
        viewModelScope.launch {
            reorderLocationsUseCase(locations)
                .onSuccess {
                    _events.emit(LocationsEvent.LocationsReordered)
                }
                .onFailure { error ->
                    _events.emit(LocationsEvent.Error(error.message ?: "Failed to reorder locations"))
                }
        }
    }
}

/**
 * UI State for locations screen
 */
data class LocationsUiState(
    val isLoading: Boolean = false
)

/**
 * Events for locations screen
 */
sealed class LocationsEvent {
    object LocationSaved : LocationsEvent()
    object LocationDeleted : LocationsEvent()
    object LocationsReordered : LocationsEvent()
    data class Error(val message: String) : LocationsEvent()
}
