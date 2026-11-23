package com.griffith.digitalclosetmd

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import kotlin.math.sqrt


// Milestone 2. sensor support: accelerometer + ambient light


/** Walks up the context chain to find the hosting activity (needed for its lifecycle). */
private fun Context.findActivity(): ComponentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/** True when the device has a default sensor of the given type */
@Composable
fun rememberSensorAvailable(sensorType: Int): Boolean {
    val context = LocalContext.current
    return remember(sensorType) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.getDefaultSensor(sensorType) != null
    }
}

@Composable
fun SensorEffect(
    sensorType: Int,
    delay: Int = SensorManager.SENSOR_DELAY_UI,
    onValues: (FloatArray) -> Unit
) {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onValues)

    DisposableEffect(context, sensorType, delay) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(sensorType)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestCallback(event.values)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (sensor != null) manager.registerListener(listener, sensor, delay)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                manager.unregisterListener(listener)
            }
        }

        val lifecycle = context.findActivity()?.lifecycle
        if (lifecycle != null) {
            lifecycle.addObserver(observer)   // replays ON_RESUME if already resumed
        } else if (sensor != null) {
            manager.registerListener(listener, sensor, delay)
        }

        onDispose {
            lifecycle?.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
}

// Accelerometer
// Detects a shake from raw accelerometer values.

class ShakeDetector(
    private val thresholdG: Float = 2.5f,
    private val cooldownMs: Long = 1000L,
    private val onShake: () -> Unit
) {
    private var lastShakeTime = 0L

    fun onValues(values: FloatArray) {
        val gX = values[0] / SensorManager.GRAVITY_EARTH
        val gY = values[1] / SensorManager.GRAVITY_EARTH
        val gZ = values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > thresholdG) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastShakeTime > cooldownMs) {
                lastShakeTime = now
                onShake()
            }
        }
    }
}

//  Ambient light
enum class LightQuality(val title: String, val advice: String) {
    TOO_DARK("Too dark", "Move near a window or switch on a light, otherwise the photo will look dull."),
    DIM("A bit dim", "Brighter light will give truer colours in the photo."),
    GOOD("Good light", "Great conditions for a clear photo of your item."),
    TOO_BRIGHT("Very bright", "Avoid direct sun: harsh glare and shadows hide the real colour.")
}

/** Maps a lux reading to photo-quality advice. */
fun lightQuality(lux: Float): LightQuality = when {
    lux < 50f -> LightQuality.TOO_DARK
    lux < 200f -> LightQuality.DIM
    lux < 20000f -> LightQuality.GOOD
    else -> LightQuality.TOO_BRIGHT
}

/** Live light meter shown on the Add item screen before the user takes a photo. */
@Composable
fun LightMeterCard(available: Boolean, lux: Float?, modifier: Modifier = Modifier) {
    val reading = lux?.let { it to lightQuality(it) }
    val container = when (reading?.second) {
        LightQuality.GOOD -> MaterialTheme.colorScheme.primaryContainer
        LightQuality.DIM -> MaterialTheme.colorScheme.tertiaryContainer
        LightQuality.TOO_DARK, LightQuality.TOO_BRIGHT -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Light check for your photo", style = MaterialTheme.typography.titleSmall)
            if (!available) {
                Text("No light sensor on this device.", style = MaterialTheme.typography.bodySmall)
            } else if (reading == null) {
                Text("Reading light level...", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "${reading.first.roundToInt()} lux - ${reading.second.title}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(reading.second.advice, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
