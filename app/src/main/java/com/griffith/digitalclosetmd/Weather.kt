package com.griffith.digitalclosetmd

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

// Milestone 2

data class WeatherInfo(val place: String, val tempC: Double, val code: Int, val fetchedAt: Long)

/** Holds the latest weather so it survives switching tabs */
object WeatherRepository {
    var weather by mutableStateOf<WeatherInfo?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

private const val CACHE_MS = 15 * 60 * 1000L

// GPS

/**
 * Gets the user's current location from the GPS provider.
 * Uses a recent last-known fix if there is one, otherwise waits up to 15 s for a new fix.
 * Returns null if location is switched off or no fix could be obtained.
 */
@SuppressLint("MissingPermission")   // permission is checked by the caller before this runs
suspend fun getCurrentLocation(context: Context): Location? = suspendCancellableCoroutine { cont ->
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { manager.isProviderEnabled(it) }
    if (providers.isEmpty()) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val lastKnown = providers.mapNotNull {
        try { manager.getLastKnownLocation(it) } catch (e: SecurityException) { null }
    }.maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 10 * 60 * 1000L) {
        cont.resume(lastKnown)
        return@suspendCancellableCoroutine
    }

    val handler = Handler(Looper.getMainLooper())
    var finished = false
    lateinit var listener: LocationListener

    fun finish(result: Location?) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        manager.removeUpdates(listener)
        if (cont.isActive) cont.resume(result)
    }

    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = finish(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Required on older Android versions")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
    handler.postDelayed({ finish(lastKnown) }, 15_000L)   // give up after 15 s, use old fix if any
    cont.invokeOnCancellation {
        handler.removeCallbacksAndMessages(null)
        manager.removeUpdates(listener)
    }
}

//Weather

/** City name from coordinates. Falls back to the coordinates if no geocoder is available. */
@Suppress("DEPRECATION")
private fun placeName(context: Context, location: Location): String {
    val coords = String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude)
    return try {
        if (!Geocoder.isPresent()) return coords
        Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.countryName }
            ?: coords
    } catch (e: Exception) {
        coords
    }
}

/** Downloads the current temperature and weather code from Open-Meteo. */
suspend fun fetchWeather(context: Context, location: Location): WeatherInfo = withContext(Dispatchers.IO) {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                "&longitude=${location.longitude}&current=temperature_2m,weather_code&timezone=auto"
    )
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val current = JSONObject(body).getJSONObject("current")
        WeatherInfo(
            place = placeName(context, location),
            tempC = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            fetchedAt = System.currentTimeMillis()
        )
    } finally {
        connection.disconnect()
    }
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Mostly clear"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Rain showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "Unknown"
}

private fun isWet(code: Int) = code in 51..67 || code in 80..82 || code in 95..99
private fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86

/** Which season's clothes suit this temperature. */
fun seasonForTemperature(tempC: Double): String = when {
    tempC >= 18.0 -> SEASONS[0]     // Spring/Summer
    tempC <= 12.0 -> SEASONS[1]     // Autumn/Winter
    else -> currentSeason()
}

/** Short practical advice shown under the temperature. */
fun weatherTip(weather: WeatherInfo): String = when {
    isWet(weather.code) -> "Rain expected: take a waterproof jacket or an umbrella."
    isSnow(weather.code) -> "Snow: wear warm, waterproof shoes."
    weather.tempC <= 5.0 -> "Very cold: layer up with a warm coat."
    weather.tempC <= 12.0 -> "Chilly: a jacket or warm layer will help."
    weather.tempC >= 24.0 -> "Hot: choose light, breathable clothes."
    weather.tempC >= 18.0 -> "Warm: light layers are enough."
    else -> "Mild: a light jacket is optional."
}

//Today screen

@Composable
fun TodayTab(onAddItem: () -> Unit, onCreateOutfit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestion by remember { mutableStateOf<Outfit?>(null) }
    val weather = WeatherRepository.weather

    // 1) GPS fix  2) download weather  3) show it
    fun refresh() {
        scope.launch {
            WeatherRepository.loading = true
            WeatherRepository.error = null
            try {
                val location = getCurrentLocation(context)
                if (location == null) {
                    WeatherRepository.error = "Could not get your location. Switch on GPS and try again."
                } else {
                    WeatherRepository.weather = fetchWeather(context, location)
                }
            } catch (e: Exception) {
                WeatherRepository.error = "Could not load the weather. Check your internet connection."
            } finally {
                WeatherRepository.loading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) refresh()
        else WeatherRepository.error = "Location permission is needed to show your local weather."
    }

    fun startRefresh() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) refresh()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // Fetch on first visit, or when the stored weather is older than 15 minutes
    LaunchedEffect(Unit) {
        val stored = WeatherRepository.weather
        if (stored == null || System.currentTimeMillis() - stored.fetchedAt > CACHE_MS) startRefresh()
    }

    // New weather -> new outfit suggestion chosen for that temperature
    LaunchedEffect(weather) {
        suggestion = weather?.let { suggestOutfit(season = seasonForTemperature(it.tempC)) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Weather card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        weather?.place ?: "Your location",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { startRefresh() }, enabled = !WeatherRepository.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh weather")
                    }
                }
                when {
                    WeatherRepository.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Finding your location and weather...")
                        }
                    }
                    weather != null -> {
                        Text("${Math.round(weather.tempC)}\u00B0C", style = MaterialTheme.typography.displayMedium)
                        Text(weatherDescription(weather.code), style = MaterialTheme.typography.titleMedium)
                        Text(weatherTip(weather), style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Text("No weather yet. Tap refresh to use your location.")
                }
                WeatherRepository.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Suggested outfit for today's weather
        if (weather != null) {
            val idea = suggestion
            val items = idea?.itemIds?.mapNotNull { ClosetRepository.getItem(it) } ?: emptyList()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Suggested for today", style = MaterialTheme.typography.titleMedium)
                    if (idea == null) {
                        Text(
                            "Add at least 2 items for ${seasonForTemperature(weather.tempC)} " +
                                    "(or All year) to get a suggestion."
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEach { item ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    ItemPhoto(
                                        item,
                                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                    )
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                suggestion = suggestOutfit(
                                    avoid = idea?.itemIds ?: emptyList(),
                                    season = seasonForTemperature(weather.tempC)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Another idea") }
                        Button(
                            onClick = {
                                idea?.let {
                                    ClosetRepository.addOutfit(
                                        "Today #${ClosetRepository.outfits.size + 1}",
                                        it.itemIds
                                    )
                                    Toast.makeText(context, "Outfit saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = idea != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Save outfit") }
                    }
                }
            }
        }

        // Quick buttons from the proposal's Home screen
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAddItem, modifier = Modifier.weight(1f)) { Text("Add item") }
            OutlinedButton(onClick = onCreateOutfit, modifier = Modifier.weight(1f)) { Text("Create outfit") }
        }
    }
}
