package ch.slf.whiterisk.multiplatform.domain.conditions.ml

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap

/**
 * Common interface for preprocessing Compose ImageBitmap for SAM inference.
 * This bridges Compose Multiplatform's ImageBitmap with platform-specific implementations.
 */
object ImageBitmapPreprocessor {
    
    /**
     * Preprocesses a Compose ImageBitmap for SAM inference.
     * 
     * This is a platform-agnostic wrapper that converts ImageBitmap to
     * the appropriate platform type and calls SAMImagePreprocessor.
     * 
     * @param imageBitmap Compose ImageBitmap to preprocess
     * @return Preprocessed float array ready for SAM, along with original dimensions
     */
    fun preprocessImageBitmap(imageBitmap: ImageBitmap): Pair<FloatArray, Pair<Int, Int>> {
        val width = imageBitmap.width
        val height = imageBitmap.height
        
        // Convert ImageBitmap to ARGB pixel array (platform-agnostic)
        val pixelMap = imageBitmap.toPixelMap()
        val pixels = IntArray(width * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixelMap[x, y]
                // Convert Color to ARGB integer format (same as Android Bitmap.getPixels())
                val a = (color.alpha * 255).toInt()
                val r = (color.red * 255).toInt()
                val g = (color.green * 255).toInt()
                val b = (color.blue * 255).toInt()
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        
        // Now use the common preprocessing pipeline
        val params = SAMPreprocessingUtils.calculatePreprocessingParams(
            width, height, SAMPreprocessingConstants.TARGET_SIZE
        )
        
        // Resize and pad the pixel array
        val resizedPixels = resizeAndPadPixels(pixels, width, height, params)
        
        // Normalize and convert to CHW format
        val preprocessed = SAMPreprocessingConstants.normalizeAndConvertToCHW(
            resizedPixels,
            SAMPreprocessingConstants.TARGET_SIZE,
            SAMPreprocessingConstants.TARGET_SIZE
        )
        
        return preprocessed to (width to height)
    }
    
    /**
     * Resizes and pads pixel array to 1024×1024.
     */
    private fun resizeAndPadPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: SAMPreprocessingUtils.PreprocessingParams
    ): IntArray {
        val targetSize = SAMPreprocessingConstants.TARGET_SIZE
        
        // First, resize to scaled dimensions
        val scaledPixels = IntArray(params.scaledWidth * params.scaledHeight)
        for (y in 0 until params.scaledHeight) {
            for (x in 0 until params.scaledWidth) {
                // Nearest neighbor sampling
                val srcX = (x / params.scale).toInt().coerceIn(0, width - 1)
                val srcY = (y / params.scale).toInt().coerceIn(0, height - 1)
                scaledPixels[y * params.scaledWidth + x] = pixels[srcY * width + srcX]
            }
        }
        
        // Then, add padding to make it 1024×1024
        val paddedPixels = IntArray(targetSize * targetSize) { 0xFF000000.toInt() } // Black with full alpha
        
        for (y in 0 until params.scaledHeight) {
            for (x in 0 until params.scaledWidth) {
                val targetX = x + params.padLeft
                val targetY = y + params.padTop
                paddedPixels[targetY * targetSize + targetX] = scaledPixels[y * params.scaledWidth + x]
            }
        }
        
        return paddedPixels
    }
}

