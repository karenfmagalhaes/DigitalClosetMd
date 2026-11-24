package com.griffith.digitalclosetmd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar


// Milestone 2: outfit suggestions triggered by the accelerometer

/**
 * Season used to filter suggestions, based on today's date.
 * April-September counts as Spring/Summer, otherwise Autumn/Winter.
 */
fun currentSeason(): String {
    val month = Calendar.getInstance().get(Calendar.MONTH)   // 0 = January
    return if (month in Calendar.APRIL..Calendar.SEPTEMBER) SEASONS[0] else SEASONS[1]
}

/**
 * Builds a random outfit from the closet: one random item per category, using only items
 * that suit the season. Returns null if the closet is too small.
 */
fun suggestOutfit(avoid: List<Int> = emptyList(), season: String = currentSeason()): Outfit? {
    val usable = ClosetRepository.items.filter { it.season == season || it.season == SEASONS[2] }

    fun pickRandom(): List<ClothingItem> = Category.entries.mapNotNull { category ->
        usable.filter { it.category == category }.randomOrNull()
    }

    var picks = pickRandom()
    if (picks.size < 2) return null

    repeat(5) {
        if (picks.map { it.id }.toSet() != avoid.toSet()) {
            return Outfit(0, "Idea", picks.map { it.id })
        }
        picks = pickRandom()
    }
    return Outfit(0, "Idea", picks.map { it.id })
}

/** Dialog that shows the suggested outfit with save / share / close actions. */
@Composable
fun OutfitSuggestionDialog(
    outfit: Outfit,
    season: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val items = outfit.itemIds.mapNotNull { ClosetRepository.getItem(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Outfit idea for $season") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                Text("Shake again for another idea.", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save outfit") } },
        dismissButton = {
            Row {
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
