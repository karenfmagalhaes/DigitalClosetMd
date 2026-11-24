package com.griffith.digitalclosetmd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material.icons.filled.Place
import android.hardware.SensorManager
import android.hardware.Sensor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DigitalClosetTheme { HomeScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }   // 0 = Today, 1 = Closet, 2 = Outfits

    // Explicit intent: open another activity of this app
    fun open(target: Class<*>) = context.startActivity(Intent(context, target))

    Scaffold(
        topBar = { TopAppBar(title = { Text("Digital Closet App") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Place, contentDescription = null) },
                    label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Closet") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Outfits") }
                )
            }
        },
        floatingActionButton = {
            // The Today screen has its own quick buttons, so the FAB is only for Closet/Outfits
            if (tab != 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        open(if (tab == 1) AddItemActivity::class.java else OutfitBuilderActivity::class.java)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (tab == 1) "Add item" else "New outfit") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> TodayTab(
                onAddItem = { open(AddItemActivity::class.java) },
                onCreateOutfit = { open(OutfitBuilderActivity::class.java) },
                modifier = Modifier.padding(padding)
            )
            1 -> ClosetTab(Modifier.padding(padding))
            else -> OutfitsTab(Modifier.padding(padding))
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}

@Composable
fun ClosetTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    val visible = ClosetRepository.items.filter { filter == null || it.category == filter }

    Column(modifier) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ChipSelector(
                options = listOf<Category?>(null) + Category.entries,
                selected = filter,
                label = { it?.label ?: "All" },
                onSelect = { filter = it }
            )
        }
        if (visible.isEmpty()) {
            EmptyState("Nothing here yet.\nTap \"Add item\" to photograph your first piece.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.id }) { item ->
                    ItemCard(item) {
                        // Explicit intent + extra: pass the item id to the detail activity
                        context.startActivity(
                            Intent(context, ItemDetailActivity::class.java)
                                .putExtra(EXTRA_ITEM_ID, item.id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OutfitsTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val hasAccelerometer = rememberSensorAvailable(Sensor.TYPE_ACCELEROMETER)
    var suggestion by remember { mutableStateOf<Outfit?>(null) }

    // Builds a season-aware random outfit; called by a shake or by the Suggest button
    fun suggest() {
        val next = suggestOutfit(avoid = suggestion?.itemIds ?: emptyList())
        if (next == null) {
            Toast.makeText(
                context,
                "Add at least 2 items for ${currentSeason()} (or All year) first",
                Toast.LENGTH_LONG
            ).show()
        } else {
            suggestion = next
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)   // feedback on shake
        }
    }

    // Accelerometer (Milestone 2): only listens while the Outfits tab is on screen
    val shakeDetector = remember { ShakeDetector(onShake = { suggest() }) }
    SensorEffect(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME) {
        shakeDetector.onValues(it)
    }

    Column(modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasAccelerometer) "Shake your phone for an outfit idea"
                    else "No accelerometer found. Use the button instead.",
                    Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { suggest() }) { Text("Suggest") }
            }
        }

        if (ClosetRepository.outfits.isEmpty()) {
            EmptyState("No outfits saved.\nTap \"New outfit\" to combine your clothes, or shake for an idea.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ClosetRepository.outfits, key = { it.id }) { outfit ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(outfit.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    outfit.itemIds.mapNotNull { ClosetRepository.getItem(it) }.forEach {
                                        ItemPhoto(it, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                                    }
                                }
                            }
                            IconButton(onClick = { shareText(context, outfitSummary(outfit)) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share outfit")
                            }
                        }
                    }
                }
            }
        }
    }

    suggestion?.let { idea ->
        OutfitSuggestionDialog(
            outfit = idea,
            season = currentSeason(),
            onSave = {
                ClosetRepository.addOutfit("Idea #${ClosetRepository.outfits.size + 1}", idea.itemIds)
                Toast.makeText(context, "Outfit saved", Toast.LENGTH_SHORT).show()
                suggestion = null
            },
            onShare = { shareText(context, outfitSummary(idea)) },
            onDismiss = { suggestion = null }
        )
    }
}
