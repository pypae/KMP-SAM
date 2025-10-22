package ch.slf.whiterisk.multiplatform.domain.conditions.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of SAM image preprocessing.
 */
actual class SAMImagePreprocessor {
    
    actual companion object {
        
        /**
         * Preprocesses an Android Bitmap for SAM inference.
         * 
         * Steps:
         * 1. Resize to 1024x1024 (with padding to maintain aspect ratio)
         * 2. Convert to RGB pixel array
         * 3. Normalize with ImageNet mean/std
         * 4. Convert to CHW format
         * 
         * @param image Input image (must be an Android Bitmap)
         * @return Preprocessed float array ready for SAM
         * @throws IllegalArgumentException if image is not a Bitmap
         */
        actual fun preprocessImage(image: Any): FloatArray {
            val bitmap = image as? Bitmap
                ?: throw IllegalArgumentException("Expected Android Bitmap, got ${image::class.simpleName}")
            
            val resizedBitmap = resizeAndPadBitmap(bitmap)
            val pixels = extractPixels(resizedBitmap)
            return SAMPreprocessingConstants.normalizeAndConvertToCHW(
                pixels,
                SAMPreprocessingConstants.TARGET_SIZE,
                SAMPreprocessingConstants.TARGET_SIZE
            )
        }
        
        /**
         * Resizes bitmap to 1024x1024 with padding to maintain aspect ratio.
         * 
         * @param bitmap Input bitmap
         * @return Resized bitmap with padding
         */
        private fun resizeAndPadBitmap(bitmap: Bitmap): Bitmap {
            val targetSize = SAMPreprocessingConstants.TARGET_SIZE
            
            // Use common preprocessing calculation
            val params = SAMPreprocessingUtils.calculatePreprocessingParams(
                bitmap.width,
                bitmap.height,
                targetSize
            )
            
            // Resize bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap, 
                params.scaledWidth, 
                params.scaledHeight, 
                true
            )
            
            // Create target bitmap with padding
            val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(paddedBitmap)
            
            // Fill with zeros (black background)
            canvas.drawColor(Color.BLACK)
            
            // Center the scaled image using calculated padding
            canvas.drawBitmap(scaledBitmap, params.padLeft.toFloat(), params.padTop.toFloat(), null)
            
            scaledBitmap.recycle()
            
            return paddedBitmap
        }
        
        /**
         * Extracts pixels from bitmap as ARGB integer array.
         */
        private fun extractPixels(bitmap: Bitmap): IntArray {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels
        }
        
        /**
         * Copies a model file from assets to app's cache directory.
         * This is necessary because ONNX Runtime needs a file path, not an InputStream.
         * 
         * @param context Platform-specific context (must be Android Context)
         * @param assetPath Path to the model in assets (e.g., "models/sam2.1_tiny.onnx")
         * @return Absolute path to the cached model file
         * @throws IllegalArgumentException if context is not an Android Context
         */
        actual fun copyModelFromAssets(context: Any, assetPath: String): String {
            val androidContext = context as? Context
                ?: throw IllegalArgumentException("Expected Android Context, got ${context::class.simpleName}")
            
            val cacheFile = File(androidContext.cacheDir, assetPath)
            
            // Only copy if file doesn't exist
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                
                try {
                    // Try to open from assets
                    println("SAMImagePreprocessor.Android: Trying to open asset: $assetPath")
                    
                    androidContext.assets.open(assetPath).use { input ->
                        println("SAMImagePreprocessor.Android: ✓ Asset found, copying to cache...")
                        FileOutputStream(cacheFile).use { output ->
                            val bytes = input.copyTo(output)
                            println("SAMImagePreprocessor.Android: ✓ Copied $bytes bytes to ${cacheFile.absolutePath}")
                        }
                    }
                } catch (e: Exception) {
                    // List available assets for debugging
                    println("SAMImagePreprocessor.Android: ✗ Failed to open asset: $assetPath")
                    println("SAMImagePreprocessor.Android: Error: ${e.message}")
                    println("SAMImagePreprocessor.Android: Listing available assets:")
                    try {
                        val assetsList = androidContext.assets.list("") ?: emptyArray()
                        assetsList.forEach { println("  - $it") }
                        
                        if (assetPath.contains("/")) {
                            val folder = assetPath.substringBeforeLast("/")
                            println("SAMImagePreprocessor.Android: Contents of '$folder':")
                            val folderContents = androidContext.assets.list(folder) ?: emptyArray()
                            folderContents.forEach { println("  - $folder/$it") }
                        }
                    } catch (e2: Exception) {
                        println("SAMImagePreprocessor.Android: Could not list assets: ${e2.message}")
                    }
                    throw IllegalStateException("Model file not found in assets: $assetPath", e)
                }
            } else {
                println("SAMImagePreprocessor.Android: ✓ Model already cached at ${cacheFile.absolutePath}")
            }
            
            return cacheFile.absolutePath
        }
        
        /**
         * Rotates bitmap if needed (useful for camera images).
         */
        fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            if (degrees == 0f) return bitmap
            
            val matrix = Matrix()
            matrix.postRotate(degrees)
            
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        
        /**
         * Converts point coordinates from original image space to SAM input space.
         * Use this to transform user click coordinates on the displayed image
         * to coordinates in the 1024x1024 SAM input space.
         * 
         * @param x X coordinate in original image
         * @param y Y coordinate in original image
         * @param originalWidth Original image width
         * @param originalHeight Original image height
         * @return Pair of (x, y) in SAM input space
         */
        actual fun transformPointToSAMSpace(
            x: Float,
            y: Float,
            originalWidth: Int,
            originalHeight: Int
        ): Pair<Float, Float> {
            // Delegate to common utilities
            return SAMPreprocessingUtils.transformPointToSAMSpace(
                x, y, originalWidth, originalHeight
            )
        }
    }
}

