package com.izziani.skytone

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.izziani.skytone.ui.theme.SkyToneTheme
import com.izziani.skytone.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SERVICE_UNAVAILABLE_STATUS = "UNKNOWN_ERROR"

private sealed interface SunsetFetchResult {
    data class Success(
        val sunsetStartLocal: String,
        val sunsetEndLocal: String,
        val twilightEndLocal: String
    ) : SunsetFetchResult

    data class DomainError(val message: String) : SunsetFetchResult

    data class NetworkError(val message: String) : SunsetFetchResult
}

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationState = mutableStateOf("Fetching location...")
    private var sunsetState = mutableStateOf("Fetching sunset data...")

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                fetchLocation()
            } else {
                locationState.value = "Location permissions denied."
                Toast.makeText(
                    this,
                    "Location permissions are required for this app to work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkAndRequestPermissions()

        setContent {
            SkyToneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WelcomeScreen(
                        location = locationState.value,
                        sunsetInfo = sunsetState.value,
                        onRefreshClick = { fetchLocation() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED &&
            coarseLocationPermission != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fetchLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    locationState.value = "Lat: $latitude, Lng: $longitude"
                    fetchSunsetData(latitude, longitude)
                } else {
                    locationState.value = "Unable to fetch location."
                }
            }
            .addOnFailureListener { e ->
                locationState.value = "Failed to fetch location: ${e.message}"
            }
    }

    private fun fetchSunsetData(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            sunsetState.value = "Fetching sunset data..."

            when (val result = fetchAndMapSunsetData(latitude, longitude)) {
                is SunsetFetchResult.Success -> {
                    sunsetState.value = """
                        Sunset Starts: ${result.sunsetStartLocal}
                        Sunset Ends: ${result.sunsetEndLocal}
                        Twilight Ends: ${result.twilightEndLocal}
                    """.trimIndent()
                }

                is SunsetFetchResult.DomainError -> {
                    sunsetState.value = result.message
                }

                is SunsetFetchResult.NetworkError -> {
                    sunsetState.value = result.message
                }
            }
        }
    }

    private suspend fun fetchAndMapSunsetData(
        latitude: Double,
        longitude: Double
    ): SunsetFetchResult {
        return try {
            val response = withContext(Dispatchers.IO) {
                RetrofitInstance.api.getSunriseSunsetTimes(latitude, longitude)
            }

            if (response.status != "OK") {
                return SunsetFetchResult.DomainError(mapStatusToMessage(response.status))
            }

            val results = response.results
                ?: return SunsetFetchResult.DomainError(
                    "Sunset information is temporarily unavailable. Please try again shortly."
                )

            val sunsetStartLocal = convertUtcToLocal(results.civil_twilight_begin)
            val sunsetEndLocal = convertUtcToLocal(results.sunset)
            val twilightEndLocal = convertUtcToLocal(results.civil_twilight_end)

            SunsetFetchResult.Success(
                sunsetStartLocal = sunsetStartLocal,
                sunsetEndLocal = sunsetEndLocal,
                twilightEndLocal = twilightEndLocal
            )
        } catch (e: Exception) {
            SunsetFetchResult.NetworkError(
                "Failed to fetch sunset data. Check your connection and try again."
            )
        }
    }

    private fun mapStatusToMessage(status: String): String {
        return when (status.uppercase(Locale.US)) {
            "INVALID_REQUEST" -> "Invalid location request. Please refresh and try again."
            "INVALID_DATE" -> "The requested date is invalid. Please try again later."
            SERVICE_UNAVAILABLE_STATUS ->
                "Sunset service is temporarily unavailable. Please try again later."

            else -> "Unable to retrieve sunset data at the moment. Please try again later."
        }
    }

    private fun convertUtcToLocal(utcTime: String?): String {
        val time = utcTime?.trim()
        if (time.isNullOrEmpty()) {
            return "Unavailable"
        }

        return formatUtcToLocalTime(time)
    }
}

internal fun formatUtcToLocalTime(
    utcTime: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    return try {
        val localDateTime = OffsetDateTime.parse(utcTime)
            .atZoneSameInstant(zoneId)

        val formatter = DateTimeFormatter.ofPattern("hh:mm a", locale)
        localDateTime.format(formatter)
    } catch (e: Exception) {
        "Error converting time"
    }
}

@Composable
fun WelcomeScreen(
    location: String,
    sunsetInfo: String,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SkyTone",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Location",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sunset & Twilight",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = sunsetInfo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRefreshClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Refresh Data")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    SkyToneTheme {
        WelcomeScreen(
            location = "Lat: 50.8092356, Lng: 4.9370557",
            sunsetInfo = "Sunset Starts: 5:18 PM\nSunset Ends: 5:48 PM\nTwilight Ends: 6:25 PM",
            onRefreshClick = {}
        )
    }
}
