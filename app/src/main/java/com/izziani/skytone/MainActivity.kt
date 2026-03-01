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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
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
                WelcomeScreen(
                    location = locationState.value,
                    sunsetInfo = sunsetState.value,
                    onRefreshClick = { fetchLocation() }
                )
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

    private fun mapStatusToMessage(status: String): String = mapSunsetStatusToMessage(status)

    private fun convertUtcToLocal(utcTime: String?): String = convertUtcToLocalOrUnavailable(utcTime)
}

internal fun convertUtcToLocalOrUnavailable(
    utcTime: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val time = utcTime?.trim()
    if (time.isNullOrEmpty()) {
        return "Unavailable"
    }

    return formatUtcToLocalTime(time, zoneId, locale)
}

internal fun mapSunsetStatusToMessage(status: String): String {
    return when (status.uppercase(Locale.US)) {
        "INVALID_REQUEST" -> "Invalid location request. Please refresh and try again."
        "INVALID_DATE" -> "The requested date is invalid. Please try again later."
        SERVICE_UNAVAILABLE_STATUS ->
            "Sunset service is temporarily unavailable. Please try again later."

        else -> "Unable to retrieve sunset data at the moment. Please try again later."
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
    val sectionHorizontalPadding = 16.dp
    val sectionSpacing = 14.dp

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BottomNavigationBar() }
    ) { innerPadding ->
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
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sectionHorizontalPadding,
                    end = sectionHorizontalPadding,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                item {
                    TopHeaderSection(
                        currentTime = "Now",
                        location = location,
                        date = "Today"
                    )
                }
                item { SunsetHeroCard(sunsetInfo = sunsetInfo, onRefreshClick = onRefreshClick) }
                item {
                    TwilightSummaryRow(
                        civil = "6:31 PM",
                        nautical = "7:04 PM",
                        astronomical = "7:36 PM"
                    )
                }
                item {
                    TodayDetailsRow(
                        humidity = "62%",
                        wind = "11 km/h",
                        cloudCover = "18%"
                    )
                }
                item {
                    WeekForecastSection(
                        forecastItems = listOf(
                            ForecastItem("Mon", "Clear", "22° / 13°"),
                            ForecastItem("Tue", "Sunny", "24° / 14°"),
                            ForecastItem("Wed", "Partly Cloudy", "21° / 12°"),
                            ForecastItem("Thu", "Cloudy", "20° / 11°"),
                            ForecastItem("Fri", "Light Rain", "18° / 10°")
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TopHeaderSection(currentTime: String, location: String, date: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = currentTime,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = location,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun SunsetHeroCard(sunsetInfo: String, onRefreshClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sunset & Twilight",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = sunsetInfo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onRefreshClick, modifier = Modifier.align(Alignment.End)) {
                Text("Refresh Data")
            }
        }
    }
}

@Composable
fun TwilightSummaryRow(civil: String, nautical: String, astronomical: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(label = "Civil", value = civil)
            SummaryItem(label = "Nautical", value = nautical)
            SummaryItem(label = "Astronomical", value = astronomical)
        }
    }
}

@Composable
fun TodayDetailsRow(humidity: String, wind: String, cloudCover: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(label = "Humidity", value = humidity)
            SummaryItem(label = "Wind", value = wind)
            SummaryItem(label = "Clouds", value = cloudCover)
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

data class ForecastItem(val day: String, val condition: String, val temperatureRange: String)

@Composable
fun WeekForecastSection(forecastItems: List<ForecastItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Week Forecast", style = MaterialTheme.typography.titleMedium)
            forecastItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.day, style = MaterialTheme.typography.bodyLarge)
                    Text(text = item.condition, style = MaterialTheme.typography.bodyMedium)
                    Text(text = item.temperatureRange, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar() {
    val tabs = listOf(
        BottomTab("Home", Icons.Outlined.Home),
        BottomTab("Locations", Icons.Outlined.LocationOn),
        BottomTab("Alerts", Icons.Outlined.Notifications),
        BottomTab("Settings", Icons.Outlined.Settings)
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    NavigationBar {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}

data class BottomTab(val label: String, val icon: ImageVector)

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
