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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.izziani.skytone.ui.theme.GlassBorder
import com.izziani.skytone.ui.theme.GlassSurface
import com.izziani.skytone.ui.theme.SkyToneTheme
import com.izziani.skytone.ui.theme.SunsetCoral
import com.izziani.skytone.ui.theme.TwilightIndigo
import com.izziani.skytone.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SERVICE_UNAVAILABLE_STATUS = "UNKNOWN_ERROR"

data class SunsetUiModel(
    val sunsetTime: String,
    val countdownLabel: String,
    val twilightItems: List<TwilightItem>,
    val todayDetailItems: List<TodayDetailItem>,
    val weekItems: List<WeekItem>
)

data class TwilightItem(val label: String, val value: String)

data class TodayDetailItem(val label: String, val value: String)

data class WeekItem(val day: String, val condition: String, val temperatureRange: String)

sealed interface SunsetUiState {
    data object Loading : SunsetUiState

    data class Success(val model: SunsetUiModel) : SunsetUiState

    data class Error(val message: String) : SunsetUiState
}

private sealed interface SunsetFetchResult {
    data class Success(val model: SunsetUiModel) : SunsetFetchResult

    data class DomainError(val message: String) : SunsetFetchResult

    data class NetworkError(val message: String) : SunsetFetchResult
}

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationState = mutableStateOf("Fetching location...")
    private var sunsetState = mutableStateOf<SunsetUiState>(SunsetUiState.Loading)

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
                    sunsetState = sunsetState.value,
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
            sunsetState.value = SunsetUiState.Loading

            when (val result = fetchAndMapSunsetData(latitude, longitude)) {
                is SunsetFetchResult.Success -> {
                    sunsetState.value = SunsetUiState.Success(result.model)
                }

                is SunsetFetchResult.DomainError -> {
                    sunsetState.value = SunsetUiState.Error(result.message)
                }

                is SunsetFetchResult.NetworkError -> {
                    sunsetState.value = SunsetUiState.Error(result.message)
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

            val twilightItems = listOf(
                TwilightItem(label = "Civil start", value = sunsetStartLocal),
                TwilightItem(label = "Sunset", value = sunsetEndLocal),
                TwilightItem(label = "Civil end", value = twilightEndLocal)
            )

            val model = SunsetUiModel(
                sunsetTime = sunsetEndLocal,
                countdownLabel = calculateCountdownLabel(sunsetEndLocal),
                twilightItems = twilightItems,
                todayDetailItems = buildTodayDetailItems(sunsetStartLocal, sunsetEndLocal, twilightEndLocal),
                weekItems = buildWeekItems()
            )

            SunsetFetchResult.Success(
                model = model
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

        val formatter = DateTimeFormatter.ofPattern("h:mm a", locale)
        localDateTime.format(formatter)
    } catch (e: Exception) {
        "Error converting time"
    }
}

@Composable
fun WelcomeScreen(
    location: String,
    sunsetState: SunsetUiState,
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
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
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
                item { SunsetHeroCard(sunsetState = sunsetState, onRefreshClick = onRefreshClick) }

                when (sunsetState) {
                    SunsetUiState.Loading -> {
                        item { SunsetLoadingBlock() }
                    }

                    is SunsetUiState.Error -> {
                        item { SunsetErrorBlock(message = sunsetState.message) }
                    }

                    is SunsetUiState.Success -> {
                        item { TwilightSummaryRow(items = sunsetState.model.twilightItems) }
                        item { TodayDetailsRow(items = sunsetState.model.todayDetailItems) }
                        item { WeekForecastSection(weekItems = sunsetState.model.weekItems) }
                    }
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
fun SunsetHeroCard(sunsetState: SunsetUiState, onRefreshClick: () -> Unit) {
    val sunsetTime = (sunsetState as? SunsetUiState.Success)?.model?.sunsetTime ?: "--"
    val countdownLabel = when (sunsetState) {
        SunsetUiState.Loading -> "Getting latest sunset window..."
        is SunsetUiState.Error -> "Unable to compute countdown"
        is SunsetUiState.Success -> sunsetState.model.countdownLabel
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.sunset_background),
                contentDescription = "Sunset background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                TwilightIndigo.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sunset Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                )
                Text(
                    text = sunsetTime,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                onClick = onRefreshClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SunsetCoral,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
            hoveredElevation = 10.dp,
            focusedElevation = 8.dp,
            pressedElevation = 4.dp
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        content()
    }
}

private fun calculateCountdownLabel(sunsetTime: String): String {
    if (sunsetTime == "Unavailable") return "Happening time unavailable"

    return try {
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val targetTime = LocalTime.parse(sunsetTime, formatter)
        val now = LocalTime.now()
        val minutes = Duration.between(now, targetTime).toMinutes()

        when {
            minutes > 60 -> "Happening in ${minutes / 60} hr ${minutes % 60} mins"
            minutes > 0 -> "Happening in $minutes mins"
            minutes == 0L -> "Happening now"
            else -> "Happened ${kotlin.math.abs(minutes)} mins ago"
        }
    } catch (e: Exception) {
        "Happening time unavailable"
    }
}

@Composable
fun TwilightSummaryRow(items: List<TwilightItem>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                TwilightStatCard(item = item)
            }
        }
    }
}

@Composable
fun TodayDetailsRow(items: List<TodayDetailItem>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                DetailPill(item = item)
            }
        }
    }
}

@Composable
fun TwilightStatCard(item: TwilightItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = item.label, style = MaterialTheme.typography.labelMedium)
        Text(text = item.value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun DetailPill(item: TodayDetailItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = item.label, style = MaterialTheme.typography.labelMedium)
        Text(text = item.value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun WeekForecastSection(weekItems: List<WeekItem>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Week Forecast", style = MaterialTheme.typography.titleMedium)
            weekItems.forEach { item ->
                WeekDayCard(item = item)
            }
        }
    }
}

@Composable
fun WeekDayCard(item: WeekItem) {
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

@Composable
fun SunsetLoadingBlock() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Loading sunset details...",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun SunsetErrorBlock(message: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun buildTodayDetailItems(
    sunsetStartLocal: String,
    sunsetEndLocal: String,
    twilightEndLocal: String
): List<TodayDetailItem> {
    return listOf(
        TodayDetailItem(label = "Start", value = sunsetStartLocal),
        TodayDetailItem(label = "Peak", value = sunsetEndLocal),
        TodayDetailItem(label = "End", value = twilightEndLocal)
    )
}

private fun buildWeekItems(): List<WeekItem> {
    return listOf(
        WeekItem("Mon", "Clear", "22° / 13°"),
        WeekItem("Tue", "Sunny", "24° / 14°"),
        WeekItem("Wed", "Partly Cloudy", "21° / 12°"),
        WeekItem("Thu", "Cloudy", "20° / 11°"),
        WeekItem("Fri", "Light Rain", "18° / 10°")
    )
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
            sunsetState = SunsetUiState.Success(
                SunsetUiModel(
                    sunsetTime = "5:48 PM",
                    countdownLabel = "Happening in 54 mins",
                    twilightItems = listOf(
                        TwilightItem("Civil start", "5:18 PM"),
                        TwilightItem("Sunset", "5:48 PM"),
                        TwilightItem("Civil end", "6:25 PM")
                    ),
                    todayDetailItems = listOf(
                        TodayDetailItem("Start", "5:18 PM"),
                        TodayDetailItem("Peak", "5:48 PM"),
                        TodayDetailItem("End", "6:25 PM")
                    ),
                    weekItems = buildWeekItems()
                )
            ),
            onRefreshClick = {}
        )
    }
}
