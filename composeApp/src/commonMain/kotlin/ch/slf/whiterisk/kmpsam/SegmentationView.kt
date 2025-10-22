package ch.slf.whiterisk.kmpsam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ch.slf.whiterisk.kmpsam.ml.SegmentationViewModel
import ch.slf.whiterisk.multiplatform.domain.conditions.ml.SegmentationResult
import kotlinx.coroutines.CoroutineScope

/**
 * Interactive segmentation view that displays an image with mask overlay
 * and allows users to add point prompts by tapping.
 */
@Composable
fun SegmentationView(
    viewModel: SegmentationViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Loading indicator
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Image with mask overlay
        uiState.currentImage?.let { image ->
            InteractiveImageWithMask(
                image = image,
                mask = uiState.mask,
                points = uiState.points.map { 
                    Offset(it.x, it.y) to (it.label == 1)
                },
                onTap = { offset, displaySize ->
                    // Convert from display coordinates to original image coordinates
                    val imageX = (offset.x / displaySize.width) * uiState.imageWidth
                    val imageY = (offset.y / displaySize.height) * uiState.imageHeight
                    
                    // Add foreground point (could add UI to toggle foreground/background)
                    viewModel.addPoint(imageX, imageY, isForeground = true, scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            )
            
            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = { viewModel.clearPoints() },
                    enabled = uiState.points.isNotEmpty() && !uiState.isLoading
                ) {
                    Text("Clear Points")
                }
                
                Button(
                    onClick = { viewModel.clearImage() },
                    enabled = !uiState.isLoading
                ) {
                    Text("Clear Image")
                }
            }
            
            // Info
            Text(
                text = "Tap on the image to add segmentation points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Interactive image with mask overlay and point markers.
 */
@Composable
private fun InteractiveImageWithMask(
    image: ImageBitmap,
    mask: SegmentationResult?,
    points: List<Pair<Offset, Boolean>>, // Offset in SAM space, Boolean = isForeground
    onTap: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    
    Box(
        modifier = modifier
            .onSizeChanged { displaySize = it }
    ) {
        // Original image
        Image(
            bitmap = image,
            contentDescription = "Image to segment",
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onTap(offset, displaySize)
                    }
                },
            contentScale = ContentScale.Fit
        )
        
        // Mask overlay and points
        if (mask != null && displaySize != IntSize.Zero) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Calculate how the image is actually displayed with ContentScale.Fit
                val imageAspect = image.width.toFloat() / image.height.toFloat()
                val displayAspect = displaySize.width.toFloat() / displaySize.height.toFloat()
                
                val (imageDisplayWidth, imageDisplayHeight, offsetX, offsetY) = if (imageAspect > displayAspect) {
                    // Image is wider - fits to width
                    val w = displaySize.width.toFloat()
                    val h = w / imageAspect
                    val y = (displaySize.height - h) / 2f
                    listOf(w, h, 0f, y)
                } else {
                    // Image is taller - fits to height
                    val h = displaySize.height.toFloat()
                    val w = h * imageAspect
                    val x = (displaySize.width - w) / 2f
                    listOf(w, h, x, 0f)
                }
                
                // Draw mask overlay
                val scaleX = imageDisplayWidth / mask.width
                val scaleY = imageDisplayHeight / mask.height
                
                for (y in 0 until mask.height) {
                    for (x in 0 until mask.width) {
                        val maskValue = mask.mask[y * mask.width + x]
                        if (maskValue > 0.5f) {
                            val left = offsetX + x * scaleX
                            val top = offsetY + y * scaleY
                            val right = left + scaleX
                            val bottom = top + scaleY
                            
                            drawRect(
                                color = Color(0xFF00FF00).copy(alpha = 0.5f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(scaleX, scaleY)
                            )
                        }
                    }
                }
                
                // Draw point markers
                points.forEach { (point, isForeground) ->
                    // Transform point from SAM space (1024x1024) to original image space
                    val imageX = (point.x / 1024f) * image.width
                    val imageY = (point.y / 1024f) * image.height
                    
                    // Then to display space
                    val displayX = offsetX + (imageX / image.width) * imageDisplayWidth
                    val displayY = offsetY + (imageY / image.height) * imageDisplayHeight
                    
                    // Draw point marker
                    drawCircle(
                        color = if (isForeground) Color.Green else Color.Red,
                        radius = 12f,
                        center = Offset(displayX, displayY)
                    )
                    
                    // Draw white border
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = Offset(displayX, displayY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
        }
    }
}


