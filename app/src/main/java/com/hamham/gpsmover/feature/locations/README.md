# Locations Feature - Clean Architecture Implementation

## Overview
The favorites module has been completely restructured using modern Android clean architecture principles. The new `locations` feature module provides better organization, maintainability, and follows SOLID principles.

## Architecture

### 📁 Directory Structure
```
feature/
└── locations/
    ├── data/           # Data layer - External data sources
    │   ├── model/      # Data entities for Firebase mapping
    │   ├── repository/ # Repository implementations
    │   └── mapper/     # Data mapping utilities
    ├── domain/         # Business logic layer - Framework independent
    │   ├── model/      # Domain entities (Location)
    │   ├── repository/ # Repository interfaces
    │   └── usecase/    # Business use cases
    ├── presentation/   # UI layer - Android specific
    │   ├── adapter/    # RecyclerView adapters
    │   ├── ui/         # Custom views
    │   └── viewmodel/  # ViewModels
    └── di/             # Dependency injection module
```

### 🔄 Migration from Old System

#### Old System (favorites/)
- `Favourite` data class with Firebase mapping
- `FavoritesManager` with repository pattern
- `FavListAdapter` with manual list management
- `FavoritesPage` custom view
- `ItemTouchHelperCallback` for drag & drop

#### New System (feature/locations/)
- `Location` domain model with validation
- `LocationRepository` interface with clean implementation
- `LocationsAdapter` using ListAdapter and DiffUtil
- `LocationsView` with modern architecture
- `LocationTouchHelperCallback` with improved functionality

## 🛠️ Key Components

### Domain Layer
- **Location**: Core business entity with validation methods
- **LocationRepository**: Interface defining data operations
- **Use Cases**: Individual business operations (Save, Delete, Reorder, Get)

### Data Layer
- **LocationEntity**: Firebase mapping entity
- **LocationRepositoryImpl**: Firebase Firestore implementation
- **LocationMapper**: Extension functions for backward compatibility

### Presentation Layer
- **LocationsViewModel**: Modern reactive ViewModel
- **LocationsAdapter**: Efficient RecyclerView adapter with DiffUtil
- **LocationsView**: Self-contained custom view with lifecycle awareness

## 🔧 Usage

### Modern Usage (Recommended)
```kotlin
// In Fragment/Activity
@AndroidEntryPoint
class LocationsFragment : Fragment() {
    private val locationsViewModel: LocationsViewModel by viewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val locationsView = view.findViewById<LocationsView>(R.id.locations_view)
        locationsView.setViewModel(locationsViewModel, lifecycleScope)
        
        locationsView.setOnLocationClick { location ->
            // Handle location selection
            navigateToMap(location.latitude, location.longitude)
        }
    }
}
```

### Backward Compatibility Usage
```kotlin
// For gradual migration from old system
@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private val compatibilityViewModel: LocationCompatibilityViewModel by viewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val locationsView = view as LocationsView
        locationsView.setCompatibilityViewModel(compatibilityViewModel, lifecycleScope)
    }
}
```

## 📋 API Reference

### Location Domain Model
```kotlin
data class Location(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int,
    val createdAt: Long
) {
    fun isValidLocation(): Boolean
    fun getFormattedCoordinates(): String
    fun withOrder(newOrder: Int): Location
}
```

### LocationRepository Interface
```kotlin
interface LocationRepository {
    fun getAllLocations(): Flow<List<Location>>
    suspend fun saveLocation(location: Location)
    suspend fun deleteLocation(location: Location)
    suspend fun updateLocationsOrder(locations: List<Location>)
    suspend fun getLocationById(id: String): Location?
    suspend fun importLocations(locations: List<Location>)
    suspend fun exportLocations(): List<Location>
}
```

### Use Cases
- **GetLocationsUseCase**: Reactive stream of all locations
- **SaveLocationUseCase**: Save with validation
- **DeleteLocationUseCase**: Safe deletion
- **ReorderLocationsUseCase**: Bulk order updates

## 🎯 Benefits

### 1. **Clean Architecture**
- Clear separation of concerns
- Framework-independent business logic
- Testable components

### 2. **Modern Android Patterns**
- Kotlin Coroutines & Flow
- Hilt Dependency Injection
- ListAdapter with DiffUtil
- StateFlow for UI state

### 3. **Maintainability**
- Single responsibility classes
- Consistent naming conventions
- Comprehensive documentation
- Type-safe operations

### 4. **Performance**
- Efficient RecyclerView updates
- Reduced Firebase calls
- Reactive data streams
- Memory leak prevention

### 5. **Backward Compatibility**
- Gradual migration support
- Extension functions for conversion
- Compatibility ViewModel layer

## 🔄 Migration Guide

### Phase 1: Infrastructure (✅ Complete)
- Set up new directory structure
- Create domain models and interfaces
- Implement data layer with Firebase
- Add dependency injection modules

### Phase 2: UI Migration (✅ Complete)
- Create modern adapter with DiffUtil
- Implement new custom view
- Update layout files
- Add compatibility layer

### Phase 3: Integration (✅ Complete)
- Update existing fragments
- Test backward compatibility
- Verify build system
- Document changes

### Phase 4: Future Cleanup (Pending)
- Remove old favorites/ directory
- Update all references
- Remove compatibility layer
- Update documentation

## 🧪 Testing
The new architecture enables comprehensive testing:
- **Unit Tests**: Domain models and use cases
- **Integration Tests**: Repository implementations
- **UI Tests**: ViewModel and adapter behavior

## 📈 Future Enhancements
- Search and filtering capabilities
- Location categories/tags
- Import/export functionality
- Offline synchronization
- Performance analytics

---

**Note**: This restructuring maintains 100% backward compatibility while providing a modern, scalable foundation for future development.
