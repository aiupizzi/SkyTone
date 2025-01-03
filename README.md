![SkyTone Logo](img/banner.jpg)

# SkyTone

SkyTone is an Android app that shows sunset and twilight times based on your location using Jetpack Compose and API integration.

## Architecture

Built using Jetpack Compose with single-activity architecture. Implements location services for user position and RESTful API for sunset calculations.

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationState = mutableStateOf<LocationState>()
    private var sunsetState = mutableStateOf<SunsetState>()
}
```

## Technical Stack

### Core Components
- Jetpack Compose UI (1.6.0)
- Material Design 3
- FusedLocationProviderClient
- Retrofit2
- Coroutines for async operations

### Build Specs
- Target SDK: 34
- Min SDK: 24
- Kotlin: 1.9.0
- JDK: 17

## Key Features

### Location Services
Implements Google Play Services location API for precise positioning. Handles runtime permissions with ActivityResultContracts.

### Time Processing
Converts UTC timestamps to local time zones for sunset calculations:
```kotlin
data class SunsetData(
    val twilightBegin: String,  // Civil twilight start
    val sunset: String,         // Actual sunset
    val twilightEnd: String     // Civil twilight end
)
```

### Network Layer
RESTful implementation using Retrofit for sunset time fetching. Processes astronomical data through the Sunrise-Sunset API.

## State Management

Implements Compose state management for:
- Location updates
- Sunset calculations
- UI rendering
- Permission handling

## Testing

Unit tests for time conversions and API responses. UI tests implemented with Compose testing framework.

## Permissions
- Fine location
- Coarse location
- Internet access

## Build Instructions
1. Clone repository:
```bash
git clone https://github.com/aiupizzi/skytone.git
```
2. Open in Android Studio.
3. Build and run.

## Contributing

Fork, branch, commit, and submit a pull request.

## Future Development

- Push notifications system
- Weather integration
- Custom location support
- Historical data tracking

## License

[MIT License](https://github.com/aiupizzi/SkyTone/blob/master/LICENSE.md)
