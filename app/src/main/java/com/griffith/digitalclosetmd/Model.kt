package com.griffith.digitalclosetmd

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf

const val EXTRA_ITEM_ID = "item_id"
val SEASONS = listOf("Spring/Summer", "Autumn/Winter", "All year")

enum class Category(val label: String) {
    TOP("Top"), BOTTOM("Bottom"), SHOES("Shoes"), ACCESSORY("Accessory")
}

data class ClothingItem(
    val id: Int,
    val name: String,
    val category: Category,
    val color: String,
    val season: String,
    val photoUri: String?
)

data class Outfit(val id: Int, val name: String, val itemIds: List<Int>)

/** In-memory store shared by all activities (same process). Replaced by Room in Milestone 3. */
object ClosetRepository {
    val items = mutableStateListOf<ClothingItem>()
    val outfits = mutableStateListOf<Outfit>()
    private var nextItemId = 1
    private var nextOutfitId = 1

    fun addItem(name: String, category: Category, color: String, season: String, photoUri: String?) {
        items.add(ClothingItem(nextItemId++, name, category, color, season, photoUri))
    }

    fun addOutfit(name: String, itemIds: List<Int>) {
        outfits.add(Outfit(nextOutfitId++, name, itemIds))
    }

    fun getItem(id: Int): ClothingItem? = items.firstOrNull { it.id == id }
}

fun outfitSummary(outfit: Outfit): String {
    val lines = outfit.itemIds.mapNotNull { ClosetRepository.getItem(it) }
        .joinToString("\n") { "- ${it.name} (${it.category.label}, ${it.color})" }
    return "My outfit \"${outfit.name}\":\n$lines\nBuilt with Digital Closet App"
}

/** Implicit intent: lets the user pick any app that can receive text. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}
