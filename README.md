# GPS Mover

---

## Introduction

**GPS Mover** (GPS Mover) is an advanced Android application for GPS spoofing, designed for rooted devices with Xposed (LSPosed/EdXposed) support. It allows you to set your device location for any app, manage favorites, export/import locations, and control advanced accuracy and randomization settings—all through a modern, user-friendly interface.

> ⚠️ **Disclaimer:** Use this application at your own risk. The developer is not responsible for any damage or data loss.

---

## Requirements
- Android 8.1 or higher
- Root access
- Xposed Framework (LSPosed or EdXposed)
- Module activation via LSPosed/EdXposed app

---

## Key Features
- **GPS Spoofing:** Set any location for your device without enabling "Mock Location" in developer options.
- **Favorites Management:** Add, reorder (drag & drop), rename, or delete your favorite locations.
- **Search:** Search for any coordinates directly from the app.
- **Custom Accuracy:** Control the accuracy of the reported location (e.g., 10m, 50m, etc.).
- **Randomization Mode:** Send a randomized location around your set point (useful for bypassing some protections).
- **Modern Material Design UI:** Includes dark mode support.
- **Notifications:** Get notified when spoofing is active.

---

## Usage Guide

### 1. Installation & Activation
- Install the APK on your device.
- Activate the module in LSPosed or EdXposed.
- Reboot your device.

### 2. Spoofing Location
- Open the app.
- Select a location on the map or search for an address/coordinates.
- Tap the "Start" (▶️) button to enable spoofing.
- Tap the "Stop" (⏹️) button to disable spoofing.

### 3. Managing Favorites
- Add your current location to favorites using the star (⭐) button.

### 4. Advanced Settings
- **Accuracy:** Choose your preferred accuracy from Settings.
- **Randomization:** Enable to send a slightly different location each time.
- **Map type and dark mode:** Change from Settings.

---

## Xposed Settings
- Enable "Hook system location" in Settings to ensure system-wide spoofing.
- You can temporarily disable spoofing from the same option.

---

## Required Permissions
- Location
- Internet (for search and updates)
- Storage (for exporting/importing favorites)

---

## Project Structure & Technologies
- **Language:** Kotlin
- **Architecture:** MVVM
- **State Management:** ViewModel + LiveData/StateFlow
- **Database:** Room
- **Dependency Injection:** Hilt
- **Preferences:** SharedPreferences
- **Networking:** Retrofit

---

## License

GPLv3 - See LICENSE file for details.

---

## Developer

Developed by: **Mohammed Hamham**  
Email: dv.hamham@gmail.com

---

## Notes
- The app will not work without root and Xposed.
- Some apps may detect Xposed or root; use at your own discretion.
- Updates are released regularly to fix bugs and support newer Android versions.

