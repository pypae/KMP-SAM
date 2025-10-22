package ch.slf.whiterisk.kmpsam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showCamera by remember { mutableStateOf(false) }
        var capturedImage by remember { mutableStateOf<ImageBitmap?>(null) }
        
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { showCamera = true }) {
                Text("Take Photo")
            }
            
            capturedImage?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                
                Button(onClick = { capturedImage = null }) {
                    Text("Clear Photo")
                }
            }
        }
        
        if (showCamera) {
            CameraView(
                onImageCaptured = { image ->
                    capturedImage = image
                    showCamera = false
                },
                onDismiss = {
                    showCamera = false
                }
            )
        }
    }
}