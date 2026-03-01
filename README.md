![SkyTone Logo](img/banner.jpg)

# SkyTone

SkyTone is an Android application that shows sunset and twilight information for your current location.  
It is built with **Jetpack Compose**, uses **Google Play Services Location** to get coordinates, and calls the **Sunrise-Sunset API** to retrieve astronomical times.

---

## Features

- Current location detection (fine/coarse location permissions)
- Sunset and civil twilight times based on your coordinates
- UTC-to-local time conversion for user-friendly display
- Simple, modern UI built with Material 3 and Jetpack Compose
- Manual refresh action to request updated location and times

---

## Screens and Flow

1. App starts and checks location permissions.
2. If permission is granted, the app requests the device's last known location.
3. Latitude/longitude are sent to the Sunrise-Sunset API.
4. Returned UTC values are converted to local time.
5. Location and sunset/twilight values are rendered in the Compose UI.

---

## Tech Stack

### Platform
- Android (minSdk 24, targetSdk 34)
- Kotlin
- Jetpack Compose (Material 3)

### Libraries
- `com.google.android.gms:play-services-location` for location retrieval
- `com.squareup.retrofit2:retrofit` for HTTP client
- `com.squareup.retrofit2:converter-gson` for JSON parsing
- Kotlin coroutines for asynchronous work

---

## Project Structure

```text
app/
  src/main/java/com/izziani/skytone/
    MainActivity.kt                 # Activity + Compose screen + UI state
    network/
      RetrofitInstance.kt           # Retrofit configuration
      SunriseSunsetApi.kt           # API interface and response models
    ui/theme/
      Color.kt                      # Compose color definitions
      Theme.kt                      # Material theme setup
      Type.kt                       # Typography setup
```

---

## Permissions

Declared in `AndroidManifest.xml`:

- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.INTERNET`

---

## API

SkyTone integrates with:  
**Sunrise-Sunset API** → `https://api.sunrise-sunset.org/json`

Request parameters used:

- `lat`: latitude
- `lng`: longitude
- `formatted=0` to receive ISO-like UTC timestamps

Example response fields consumed:

- `results.sunset`
- `results.civil_twilight_begin`
- `results.civil_twilight_end`

---

## Build and Run

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK 34
- JDK 17

### Steps

1. Clone the repository:

   ```bash
   git clone https://github.com/aiupizzi/skytone.git
   cd skytone
   ```

2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Run the app on an emulator or physical device.
5. Grant location permission when prompted.

---

## Testing

Current repository includes starter test files:

- Unit test source: `app/src/test/...`
- Instrumented test source: `app/src/androidTest/...`

To run tests:

```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## Troubleshooting

- **Location is unavailable**: ensure location services are enabled on device and permission is granted.
- **No network data**: verify internet access and API reachability.
- **Times seem incorrect**: check device timezone settings.

---

## Roadmap Ideas

- Better error and loading states
- Weather context around sunset period
- Saved favorite locations
- Notifications before sunset/twilight transitions
- Improved dashboard-style visual design

---

## Contributing

Contributions are welcome.

1. Fork the repo
2. Create a feature branch
3. Commit your changes
4. Open a Pull Request

---

## License

This project is licensed under the MIT License. See [LICENSE.md](LICENSE.md).
