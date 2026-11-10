package com.griffith.digitalclosetmd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class OutfitBuilderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preselected = intent.getIntExtra(EXTRA_ITEM_ID, -1)   // optional extra
        setContent { DigitalClosetTheme { OutfitBuilderScreen(preselected, onClose = { finish() }) } }
    }
}

@Composable
fun OutfitBuilderScreen(preselectedId: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val selected = remember {
        mutableStateMapOf<Category, Int>().apply {
            ClosetRepository.getItem(preselectedId)?.let { put(it.category, it.id) }
        }
    }
    var name by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { BackTopBar("Create outfit", onClose) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live preview of the combination
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { cat ->
                    val item = selected[cat]?.let { ClosetRepository.getItem(it) }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item != null) ItemPhoto(item, Modifier.fillMaxSize())
                        else Text(cat.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // One row of choices per category; tap again to deselect
            Category.entries.forEach { cat ->
                val options = ClosetRepository.items.filter { it.category == cat }
                Text(cat.label, style = MaterialTheme.typography.titleSmall)
                if (options.isEmpty()) {
                    Text("No ${cat.label.lowercase()} items yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options, key = { it.id }) { item ->
                            ItemCard(item, Modifier.width(120.dp), selected = selected[cat] == item.id) {
                                if (selected[cat] == item.id) selected.remove(cat) else selected[cat] = item.id
                            }
                        }
                    }
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("Outfit name") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (selected.size < 2 || name.isBlank()) {
                            Toast.makeText(context, "Pick at least 2 items and name the outfit", Toast.LENGTH_SHORT).show()
                        } else {
                            ClosetRepository.addOutfit(name.trim(), selected.values.toList())
                            Toast.makeText(context, "Outfit saved", Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save outfit") }
                OutlinedButton(
                    onClick = {
                        shareText(context, outfitSummary(Outfit(0, name.ifBlank { "My outfit" }, selected.values.toList())))
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Share") }
            }
        }
    }
}

