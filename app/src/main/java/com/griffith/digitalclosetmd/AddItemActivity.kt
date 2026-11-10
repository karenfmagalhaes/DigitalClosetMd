package com.griffith.digitalclosetmd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

class AddItemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DigitalClosetTheme { AddItemScreen(onClose = { finish() }) } }
    }
}

@Composable
fun AddItemScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.TOP) }
    var season by rememberSaveable { mutableStateOf(SEASONS[0]) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Implicit intent (ACTION_IMAGE_CAPTURE) through the Activity Result API
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri = pendingUri
    }
    // Implicit intent: pick an image from any gallery app
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    fun launchCamera() {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "item_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri.toString()
        cameraLauncher.launch(uri)
    }

    Scaffold(topBar = { BackTopBar("Add item", onClose) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(photoUri, "Item photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("No photo yet")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) { Text("Take photo") }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Gallery") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Blue denim jacket)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Category", style = MaterialTheme.typography.labelLarge)
            ChipSelector(Category.entries, category, { it.label }) { category = it }
            OutlinedTextField(color, { color = it }, label = { Text("Colour") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Season", style = MaterialTheme.typography.labelLarge)
            ChipSelector(SEASONS, season, { it }) { season = it }
            Button(
                onClick = {
                    ClosetRepository.addItem(name.trim(), category, color.trim().ifBlank { "n/a" }, season, photoUri)
                    Toast.makeText(context, "Item saved", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save item") }
        }
    }
}
