package ch.slf.whiterisk.kmpsam.ml

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import ch.slf.whiterisk.multiplatform.domain.conditions.ml.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing SAM segmentation state and operations.
 */
class SegmentationViewModel {
    
    private val samService = SAMSegmentationService()
    private var isInitialized = false
    
    var uiState by mutableStateOf(SegmentationUiState())
        private set
    
    /**
     * Initializes the SAM models (should be called once at startup).
     */
    fun initialize(scope: CoroutineScope) {
        scope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                
                withContext(Dispatchers.Default) {
                    // Models are in commonMain/resources/models/
                    // copyModelFromAssets will find them in the bundle/assets
                    val context = ch.slf.whiterisk.kmpsam.PlatformContext.get()
                    
                    val preprocessPath = SAMImagePreprocessor.copyModelFromAssets(
                        context,
                        "models/sam2.1_tiny_preprocess.onnx"
                    )
                    
                    val decoderPath = SAMImagePreprocessor.copyModelFromAssets(
                        context,
                        "models/sam2.1_tiny.onnx"
                    )
                    
                    println("SegmentationViewModel: Initializing SAM models...")
                    println("  Preprocess model: $preprocessPath")
                    println("  Decoder model: $decoderPath")
                    
                    samService.initialize(preprocessPath, decoderPath)
                    isInitialized = true
                }
                
                uiState = uiState.copy(isLoading = false)
                println("SegmentationViewModel: ✓ SAM models initialized successfully")
                
            } catch (e: Exception) {
                println("SegmentationViewModel ERROR: ${e.message}")
                e.printStackTrace()
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to initialize: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Sets the current image and encodes it with SAM.
     */
    fun setImage(imageBitmap: ImageBitmap, scope: CoroutineScope) {
        if (!isInitialized) {
            uiState = uiState.copy(error = "Models not initialized")
            return
        }
        
        scope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                
                val (preprocessed, dimensions) = withContext(Dispatchers.Default) {
                    ImageBitmapPreprocessor.preprocessImageBitmap(imageBitmap)
                }
                
                val (originalWidth, originalHeight) = dimensions
                
                println("SegmentationViewModel: Image preprocessed: ${originalWidth}x${originalHeight}")
                
                withContext(Dispatchers.Default) {
                    samService.encodeImage(
                        preprocessed,
                        SAMPreprocessingConstants.TARGET_SIZE,
                        SAMPreprocessingConstants.TARGET_SIZE,
                        originalWidth,
                        originalHeight
                    )
                }
                
                uiState = uiState.copy(
                    currentImage = imageBitmap,
                    imageWidth = originalWidth,
                    imageHeight = originalHeight,
                    isLoading = false,
                    mask = null,
                    points = emptyList()
                )
                
                println("SegmentationViewModel: ✓ Image encoded successfully")
                
            } catch (e: Exception) {
                println("SegmentationViewModel ERROR: ${e.message}")
                e.printStackTrace()
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to process image: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Adds a point prompt and runs segmentation.
     * 
     * @param x X coordinate in display space (will be transformed to SAM space)
     * @param y Y coordinate in display space
     * @param isForeground true for foreground point, false for background
     */
    fun addPoint(x: Float, y: Float, isForeground: Boolean, scope: CoroutineScope) {
        val image = uiState.currentImage
        val imageWidth = uiState.imageWidth
        val imageHeight = uiState.imageHeight
        
        if (image == null || imageWidth == 0 || imageHeight == 0) {
            uiState = uiState.copy(error = "No image loaded")
            return
        }
        
        // Transform point from display space to SAM space (1024×1024)
        val (samX, samY) = SAMPreprocessingUtils.transformPointToSAMSpace(
            x, y, imageWidth, imageHeight
        )
        
        val newPoint = SegmentationPoint(
            x = samX,
            y = samY,
            label = if (isForeground) 1 else 0
        )
        
        val newPoints = uiState.points + newPoint
        
        println("SegmentationViewModel: Added point at ($x, $y) → SAM space: ($samX, $samY), label=${newPoint.label}")
        
        uiState = uiState.copy(points = newPoints)
        
        // Run segmentation with all points
        runSegmentation(newPoints, scope)
    }
    
    /**
     * Clears all points and the mask.
     */
    fun clearPoints() {
        uiState = uiState.copy(points = emptyList(), mask = null)
        println("SegmentationViewModel: Cleared all points")
    }
    
    /**
     * Clears the current image and resets state.
     */
    fun clearImage() {
        samService.clearCache()
        uiState = uiState.copy(
            currentImage = null,
            mask = null,
            points = emptyList(),
            imageWidth = 0,
            imageHeight = 0
        )
        println("SegmentationViewModel: Cleared image")
    }
    
    /**
     * Runs segmentation with the given points.
     */
    private fun runSegmentation(points: List<SegmentationPoint>, scope: CoroutineScope) {
        if (points.isEmpty()) {
            uiState = uiState.copy(mask = null)
            return
        }
        
        val imageWidth = uiState.imageWidth
        val imageHeight = uiState.imageHeight
        
        scope.launch {
            try {
                uiState = uiState.copy(isLoading = true, error = null)
                
                val result = withContext(Dispatchers.Default) {
                    samService.segmentWithPrompts(points, imageWidth, imageHeight)
                }
                
                println("SegmentationViewModel: ✓ Segmentation complete")
                println("  Mask size: ${result.width}x${result.height}")
                println("  Mask coverage: ${result.mask.count { it > 0.5f }} pixels")
                
                uiState = uiState.copy(
                    mask = result,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                println("SegmentationViewModel ERROR: ${e.message}")
                e.printStackTrace()
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Segmentation failed: ${e.message}"
                )
            }
        }
    }
    
    fun cleanup() {
        samService.close()
    }
}

/**
 * UI state for segmentation.
 */
data class SegmentationUiState(
    val currentImage: ImageBitmap? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val mask: SegmentationResult? = null,
    val points: List<SegmentationPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

