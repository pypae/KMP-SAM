package ch.slf.whiterisk.kmpsam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.slf.whiterisk.kmpsam.ml.SegmentationViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel = remember { SegmentationViewModel() }
        val scope = rememberCoroutineScope()
        var showCamera by remember { mutableStateOf(false) }
        
        // Initialize SAM models on startup
        LaunchedEffect(Unit) {
            viewModel.initialize(scope)
        }
        
        // Cleanup on dispose
        DisposableEffect(Unit) {
            onDispose {
                viewModel.cleanup()
            }
        }
        
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "SAM Segmentation Demo",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            // Show global errors at the top
            viewModel.uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Show loading state
            if (viewModel.uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            // Capture button
            if (viewModel.uiState.currentImage == null) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { showCamera = true },
                    enabled = !viewModel.uiState.isLoading
                ) {
                    Text("Take Photo")
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Show segmentation view
                SegmentationView(
                    viewModel = viewModel,
                    scope = scope,
                    modifier = Modifier.weight(1f)
                )
                
                // Take another photo button
                Button(
                    onClick = { 
                        viewModel.clearImage()
                        showCamera = true
                    },
                    enabled = !viewModel.uiState.isLoading,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Take Another Photo")
                }
            }
        }
        
        // Camera view
        if (showCamera) {
            CameraView(
                onImageCaptured = { image ->
                    showCamera = false
                    // Pass image to viewModel for encoding
                    image?.let { bitmap ->
                        scope.launch {
                            viewModel.setImage(bitmap, scope)
                        }
                    }
                },
                onDismiss = {
                    showCamera = false
                }
            )
        }
    }
}