# SkyTone

SkyTone is an Android application that helps photography enthusiasts and sky-watchers catch the most beautiful moments of the day. By tracking sunset times, civil twilight periods, and providing notifications, SkyTone ensures you never miss those perfect golden hour shots.

## Features

- **Real-time Location Tracking**: Automatically detects your current location for accurate timing calculations
- **Precise Sunset Tracking**: Monitors three critical phases of sunset:
  - Civil Twilight Begin (Golden Hour Start)
  - Actual Sunset
  - Civil Twilight End (Blue Hour End)
- **Local Time Conversion**: All times are automatically converted to your local timezone
- **Material Design 3**: Modern, clean interface built with Jetpack Compose
- **Refresh on Demand**: Manual refresh option for updated timing information

## Technical Stack

- **Language**: Kotlin
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture Components**:
  - Jetpack Compose for UI
  - Location Services (FusedLocationProviderClient)
  - Retrofit for network operations
  - Coroutines for asynchronous programming
  - Material 3 design components

## Required Permissions

- `ACCESS_FINE_LOCATION`: For precise location tracking
- `ACCESS_COARSE_LOCATION`: For approximate location tracking
- `INTERNET`: For fetching sunset data

## Building the Project

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and run the application

## Dependencies

```kotlin
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
implementation("androidx.activity:activity-compose:1.8.0")
implementation("androidx.compose.ui:ui:1.6.0")
implementation("androidx.compose.material3:material3:1.2.0")
implementation("com.google.android.gms:play-services-location:21.0.1")
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
```

## Usage

1. Launch the application
2. Grant location permissions when prompted
3. The app will automatically fetch your current location
4. View the sunset timing information displayed on the cards
5. Use the refresh button to update the information as needed

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Sunset/sunrise calculations powered by Sunrise-Sunset API
- Icons and design elements from Material Design 3
- Location services provided by Google Play Services

## Future Enhancements

- Push notifications for sunset alerts
- Custom notification timing settings
- Historical data tracking
- Weather integration for better sky condition predictions
- Photo sharing capabilities
- Custom location bookmarking

## Contact

For any questions or suggestions, please open an issue in the repository.
