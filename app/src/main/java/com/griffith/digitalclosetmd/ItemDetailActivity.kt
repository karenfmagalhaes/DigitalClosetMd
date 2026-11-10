package com.griffith.digitalclosetmd
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class ItemDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Data received from the previous activity through the intent extra
        val item = ClosetRepository.getItem(intent.getIntExtra(EXTRA_ITEM_ID, -1))
        if (item == null) { finish(); return }
        setContent { DigitalClosetTheme { ItemDetailScreen(item, onClose = { finish() }) } }
    }
}

@Composable
fun ItemDetailScreen(item: ClothingItem, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { BackTopBar(item.name, onClose) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ItemPhoto(item, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            Text("Category: ${item.category.label}")
            Text("Colour: ${item.color}")
            Text("Season: ${item.season}")
            Button(
                onClick = {
                    context.startActivity(
                        Intent(context, OutfitBuilderActivity::class.java).putExtra(EXTRA_ITEM_ID, item.id)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use in an outfit") }
            OutlinedButton(
                onClick = { shareText(context, "Look at my ${item.color} ${item.name}! (Digital Closet)") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share item") }
            TextButton(
                onClick = { ClosetRepository.items.remove(item); onClose() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
        }
    }
}
