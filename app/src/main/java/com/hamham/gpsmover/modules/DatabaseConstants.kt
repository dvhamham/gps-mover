package com.hamham.gpsmover.modules

/**
 * Database Constants for Firebase Collections and Keys
 */
object Collections {
    const val APPLICATION = "application"
    const val DEVICES = "devices"
    
    // Deprecated - for backward compatibility
    @Deprecated("Use APPLICATION instead")
    const val GLOBAL = "global"
    @Deprecated("Favourites are now stored within devices collection")
    const val FAVOURITES = "favourites"
    @Deprecated("Favourites are now stored within devices collection")
    const val FAVORITES = "favorites"
}

object DeviceKeys {
    // Device Fields
    const val ACCOUNT = "account"
    const val ACCOUNTS = "accounts"
    const val APP_VERSION = "app_version"
    const val BANNED = "banned"
    const val FAVOURITES = "favourites"
    
    // Custom Message Fields
    const val CUSTOM_MESSAGE = "custom_message"
    const val ENABLED = "enabled"
    const val TEXT = "text"
    const val TITLE = "title"
    
    // About Group Fields (Device Information)
    const val ABOUT = "about"
    const val MANUFACTURER = "manufacturer"
    const val MODEL = "model"
    const val OS_VERSION = "os_version"
    
    // Location Group Fields
    const val LOCATION = "location"
    const val CITY = "city"
    const val COUNTRY = "country"
    
    // Favourites Fields (within device)
    object FavouritesKeys {
        const val LIST = "list"
        
        object FavouriteItem {
            const val ID = "id"
            const val NAME = "name"
            const val LAT = "lat"
            const val LNG = "lng"
            const val ORDER = "order"
            
            // Deprecated - for backward compatibility
            @Deprecated("Use NAME instead")
            const val ADDRESS = "address"
            @Deprecated("Use LAT instead")
            const val LATITUDE = "latitude"
            @Deprecated("Use LNG instead")
            const val LONGITUDE = "longitude"
            @Deprecated("Field not used in current structure")
            const val CREATED_AT = "created_at"
        }
    }
    
    // Timestamps
    const val CREATED_AT = "created_at"
    const val UPDATED_AT = "updated_at"
    
    // Deprecated - for backward compatibility
    @Deprecated("Use ABOUT group instead")
    const val DEVICE_INFO = "device_info"
    @Deprecated("Use ABOUT instead")
    const val DEVICE = "device"
    @Deprecated("Use ACCOUNT instead")
    const val CURRENT_ACCOUNT = "current_account"
    @Deprecated("Use MODEL instead")
    const val DEVICE_MODEL = "device_model"
    @Deprecated("Use MANUFACTURER instead")
    const val DEVICE_MANUFACTURER = "device_manufacturer"
    @Deprecated("Use OS_VERSION instead")
    const val ANDROID_VERSION = "android_version"
    const val ANDROID_ID = "android_id"
    const val MESSAGE = "message"
}

object RulesKeys {
    // Application Rules Groups
    const val KILL = "kill"
    const val UPDATE = "update"
    
    // Common Group Fields
    const val ENABLED = "enabled"
    const val TITLE = "title"
    const val MESSAGE = "message"
    
    // Kill Group Fields - uses common fields above
    
    // Update Group Fields
    const val LATEST_VERSION = "latest_version"
    const val MIN_VERSION = "min_version"
    const val REQUIRED = "required"
    const val SILENT_INSTALL = "silent_install"
    const val URL = "url"
    
    // Deprecated - for backward compatibility
    @Deprecated("Use KILL instead")
    const val KILLALL = "killall"
    @Deprecated("Use UPDATE group instead")
    const val APP_ENABLED = "app_enabled"
    @Deprecated("Use UPDATE group instead")
    const val MAINTENANCE_MODE = "maintenance_mode"
    @Deprecated("Use UPDATE group instead")
    const val MINIMUM_VERSION = "minimum_version"
    @Deprecated("Use UPDATE group instead")
    const val FORCE_UPDATE = "force_update"
    @Deprecated("Use UPDATE group instead")
    const val UPDATE_URL = "update_url"
    @Deprecated("Use UPDATE group instead")
    const val ANNOUNCEMENT = "announcement"
    @Deprecated("Use UPDATE group instead")
    const val MAX_DEVICES_PER_USER = "max_devices_per_user"
    const val VERSION = "version"
    const val LIMIT = "limit"
    const val LATEST_VERSION_ALT = "latest_version"
    const val MIN_REQUIRED_VERSION = "min_required_version"
}
