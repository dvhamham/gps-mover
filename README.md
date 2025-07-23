# GPS Mover

**GPS Mover** is an Android application that allows you to mock your device's GPS location. It provides a simple and intuitive interface to set a fake location, which can be useful for developers, testers, or anyone who wants to spoof their location for privacy or other purposes.

This application is built with Kotlin and utilizes the Xposed Framework to achieve reliable location spoofing. It also integrates with Firebase for remote configuration and user management.

## Key Features

*   **Advanced GPS Mocking:**
    *   Set a fake GPS location on your device using a foreground service for persistent mocking.
    *   The app's stability is enhanced by an integrated Xposed module, which helps to bypass common issues with GPS spoofing.
    *   The app checks if the Xposed module is active and notifies the user if it's not.

*   **Interactive Map:**
    *   Select a location by clicking on the map or by searching for an address or coordinates.
    *   The map is powered by the Google Maps SDK and supports different map types (e.g., normal, satellite).
    *   A "My Location" button allows you to quickly center the map on your real location.

*   **Favorites Management:**
    *   Save your favorite locations with a custom name for quick access.
    *   The favorites are stored locally in a Room database and can be reordered.
    *   You can easily import and export your favorites to a JSON file, allowing you to back them up or share them with others.
    *   There is also an option to import favorites from a cloud backup associated with your Google account.

*   **Firebase Integration:**
    *   **User Authentication:** All users must log in with a Google account, which helps to prevent misuse.
    *   **Remote Configuration:** The app's behavior can be controlled remotely via Firebase Firestore. This includes:
        *   A "kill switch" to disable the app.
        *   Forced updates to ensure users are on the latest version.
        *   The ability to send custom messages to all users.
    *   **Device Management:** The app tracks device information (e.g., `ANDROID_ID`, device model, OS version) to manage access and prevent abuse. This includes:
        *   The ability to ban specific devices.
        *   The ability to send custom messages to a specific device.

*   **User Interface:**
    *   A clean and modern UI built with Material Components.
    *   A bottom navigation bar for easy access to the map, favorites, and settings screens.
    *   The app uses haptic feedback to enhance the user experience.

## Technologies Used

*   **Programming Language:** [Kotlin](https://kotlinlang.org/)
*   **Architecture:** Model-View-ViewModel (MVVM)
*   **UI:**
    *   [Android Jetpack](https://developer.android.com/jetpack)
    *   [Material Components for Android](https://material.io/develop/android)
    *   [View Binding](https://developer.android.com/topic/libraries/view-binding)
*   **Dependency Injection:** [Hilt](https://dagger.dev/hilt/)
*   **Asynchronous Programming:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room)
*   **Networking:** [Retrofit](https://square.github.io/retrofit/)
*   **Maps:** [Google Maps SDK](https://developers.google.com/maps/documentation/android-sdk/intro)
*   **System Modifications:** [Xposed Framework](https://repo.xposed.info/)
*   **Backend Services:** [Firebase](https://firebase.google.com/)
    *   [Firebase Authentication](https://firebase.google.com/docs/auth)
    *   [Firebase Firestore](https://firebase.google.com/docs/firestore)

## Installation and Setup

To build and install the app, you will need:

*   Android Studio
*   An Android device or emulator with root access
*   The Xposed Framework installed on your device

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/gps-mover.git
    ```
2.  **Open the project in Android Studio.**
3.  **Create a `google-services.json` file:** This file is required for Firebase integration. You will need to create a new Firebase project and add an Android app to it. Then, download the `google-services.json` file and place it in the `app/` directory.
4.  **Set up the Maps API Key:** You will need to create a `secrets.properties` file in the root of the project with your Google Maps API key:
    ```
    MAPS_API_KEY="YOUR_API_KEY"
    ```
5.  **Build the project.**
6.  **Run the app on your device or emulator.**
7.  **Activate the Xposed module:**
    *   Open the Xposed Installer app.
    *   Go to the "Modules" section.
    *   Enable the "GPS Mover" module.
    *   Reboot your device.

## Usage

### Logging In

The first time you open the app, you will be prompted to log in with your Google account. This is a mandatory step to use the app.

### Setting a Location

There are two ways to set a location:

*   **By clicking on the map:** Simply pan and zoom to your desired location and tap on the map to place a marker.
*   **By searching:** Use the search bar at the top of the screen to search for a location by address or by coordinates (e.g., "40.7128, -74.0060").

### Starting and Stopping the Mock Location

*   **To start:** Once you have placed a marker on the map, click the "Start" button (the floating action button with the play icon). A notification will appear in the status bar to indicate that the location is being mocked.
*   **To stop:** Click the "Stop" button (the floating action button with the stop icon). The notification will be dismissed, and your device's GPS will return to its real location.

### Managing Your Favorites

*   **Adding a favorite:** Place a marker on the map and click the "Add to Favorites" button (the floating action button with the star icon). You will be prompted to enter a name for the favorite.
*   **Viewing and using favorites:** Navigate to the "Favorites" screen using the bottom navigation bar. Here you will see a list of all your saved favorites. Clicking on a favorite will immediately set your location to it and take you back to the map screen.
*   **Deleting and reordering favorites:** You can delete a favorite by swiping it to the left or right. You can also reorder your favorites by long-pressing on a favorite and then dragging it to a new position.

### Importing and Exporting Favorites

From the "Favorites" screen, you can access the import/export menu from the top-right corner. This allows you to:

*   **Export to device:** Save all your favorites to a `gps_mover_favorites.json` file on your device.
*   **Import from device:** Restore your favorites from a previously exported JSON file.
*   **Import from cloud:** Restore your favorites from a cloud backup associated with your Google account.

## Firebase Integration

This application uses Firebase for several key features:

*   **Firebase Authentication:** All users are required to authenticate with their Google account before using the app. This helps to prevent anonymous misuse.
*   **Firebase Firestore:** Firestore is used for the following:
    *   **Remote Configuration:** The app's behavior can be controlled remotely through a "Rules" document in Firestore. This includes:
        *   `latest_version`: The latest version of the app.
        *   `min_required_version`: The minimum required version of the app.
        *   `update_required`: A boolean to force users to update.
        *   `kill_switch`: A boolean to disable the app remotely.
    *   **Device Management:** The app collects the `ANDROID_ID` of each device and stores it in a "devices" collection. This allows for:
        *   **Banning Devices:** A device can be banned by setting the `banned` field to `true` in its document.
        *   **Sending Custom Messages:** A custom message can be sent to a specific device by setting the `message_show` and `message` fields in its document.
    *   **User Information:** The app stores user information, such as the device model, OS version, and last login time.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
