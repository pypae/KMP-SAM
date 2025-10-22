package ch.slf.whiterisk.multiplatform.domain.conditions.ml

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.posix.memcpy

/**
 * iOS implementation of SAM image preprocessing.
 */
actual class SAMImagePreprocessor {
    
    actual companion object {
        
        /**
         * Preprocesses a UIImage for SAM inference.
         * 
         * Steps:
         * 1. Resize to 1024x1024 (with padding to maintain aspect ratio)
         * 2. Extract pixel data as RGB
         * 3. Normalize with ImageNet mean/std
         * 4. Convert to CHW format
         * 
         * @param image Input image (must be a UIImage on iOS)
         * @return Preprocessed float array ready for SAM
         * @throws IllegalArgumentException if image is not a UIImage
         */
        actual fun preprocessImage(image: Any): FloatArray {
            val uiImage = image as? UIImage
                ?: throw IllegalArgumentException("Expected UIImage, got ${image::class.simpleName}")
            
            val resizedImage = resizeAndPadImage(uiImage)
            val pixels = extractPixels(resizedImage)
            return SAMPreprocessingConstants.normalizeAndConvertToCHW(
                pixels,
                SAMPreprocessingConstants.TARGET_SIZE,
                SAMPreprocessingConstants.TARGET_SIZE
            )
        }
        
        /**
         * Resizes UIImage to 1024x1024 with padding to maintain aspect ratio.
         * 
         * @param image Input UIImage
         * @return Resized UIImage with padding
         */
        @OptIn(ExperimentalForeignApi::class)
        private fun resizeAndPadImage(image: UIImage): UIImage {
            val targetSize = SAMPreprocessingConstants.TARGET_SIZE
            val originalWidth = image.size.useContents { width.toInt() }
            val originalHeight = image.size.useContents { height.toInt() }
            
            // Calculate preprocessing parameters
            val params = SAMPreprocessingUtils.calculatePreprocessingParams(
                originalWidth,
                originalHeight,
                targetSize
            )
            
            // Create a new image context with the target size
            UIGraphicsBeginImageContextWithOptions(
                CGSizeMake(targetSize.toDouble(), targetSize.toDouble()),
                false,
                1.0
            )
            
            // Fill with black background (for padding)
            val context = UIGraphicsGetCurrentContext()
            context?.let {
                CGContextSetRGBFillColor(it, 0.0, 0.0, 0.0, 1.0)
                CGContextFillRect(it, CGRectMake(0.0, 0.0, targetSize.toDouble(), targetSize.toDouble()))
            }
            
            // Draw the scaled image centered with padding
            val rect = CGRectMake(
                params.padLeft.toDouble(),
                params.padTop.toDouble(),
                params.scaledWidth.toDouble(),
                params.scaledHeight.toDouble()
            )
            image.drawInRect(rect)
            
            // Get the resulting image
            val resultImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            
            return resultImage ?: image
        }
        
        /**
         * Extracts pixels from UIImage as ARGB integer array.
         * 
         * @param image UIImage to extract pixels from
         * @return IntArray of ARGB pixels (compatible with Android format)
         */
        @OptIn(ExperimentalForeignApi::class)
        private fun extractPixels(image: UIImage): IntArray {
            val width = image.size.useContents { width.toInt() }
            val height = image.size.useContents { height.toInt() }
            
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val bytesPerPixel = 4
            val bytesPerRow = bytesPerPixel * width
            val bitsPerComponent = 8
            
            // Allocate memory for pixel data
            val rawData = nativeHeap.allocArray<UByteVar>(height * bytesPerRow)
            
            try {
                // Create bitmap context
                val context = CGBitmapContextCreate(
                    data = rawData,
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = bitsPerComponent.toULong(),
                    bytesPerRow = bytesPerRow.toULong(),
                    space = colorSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
                )
                
                if (context != null) {
                    // Draw image into context
                    CGContextDrawImage(
                        context,
                        CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                        image.CGImage
                    )
                    
                    // Convert to IntArray (ARGB format)
                    val pixels = IntArray(width * height)
                    for (i in 0 until height) {
                        for (j in 0 until width) {
                            val offset = (i * width + j) * bytesPerPixel
                            val r = rawData[offset].toInt() and 0xFF
                            val g = rawData[offset + 1].toInt() and 0xFF
                            val b = rawData[offset + 2].toInt() and 0xFF
                            val a = rawData[offset + 3].toInt() and 0xFF
                            
                            // Convert to ARGB format (compatible with Android)
                            pixels[i * width + j] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    
                    return pixels
                } else {
                    throw RuntimeException("Failed to create bitmap context")
                }
            } finally {
                nativeHeap.free(rawData)
            }
        }
        
        /**
         * Converts point coordinates from original image space to SAM input space.
         * 
         * This delegates to the shared SAMPreprocessingUtils which works on all platforms.
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
            return SAMPreprocessingUtils.transformPointToSAMSpace(
                x, y, originalWidth, originalHeight
            )
        }
        
        /**
         * Copies a model file from the iOS app bundle to a usable location.
         * 
         * On iOS, models in the bundle can be accessed directly, but we copy them
         * to the cache directory for consistency with Android.
         * 
         * @param context Not used on iOS (kept for API consistency)
         * @param assetPath Path to the model in app bundle (e.g., "models/sam2.1_tiny.onnx")
         * @return Absolute path to the model file
         * @throws IllegalStateException if the model file cannot be found or copied
         */
        @OptIn(ExperimentalForeignApi::class)
        actual fun copyModelFromAssets(context: Any, assetPath: String): String {
            // Parse the asset path (e.g., "models/sam2.1_tiny.onnx")
            val pathComponents = assetPath.split("/")
            val fileName = pathComponents.lastOrNull() ?: assetPath
            val fileNameComponents = fileName.split(".")
            val name = fileNameComponents.dropLast(1).joinToString(".")
            val extension = fileNameComponents.lastOrNull() ?: ""
            
            // Try to find the file in bundles
            // First try main bundle (where Compose resources are placed)
            var bundlePath = NSBundle.mainBundle.pathForResource(name, extension)
            
            // If not found, try all loaded bundles (including framework bundles)
            if (bundlePath == null) {
                val allBundles = NSBundle.allBundles() as? List<*>
                allBundles?.forEach { bundle ->
                    if (bundlePath == null && bundle is NSBundle) {
                        bundlePath = bundle.pathForResource(name, extension)
                    }
                }
            }
            
            bundlePath ?: throw IllegalStateException(
                "Model file not found in any bundle: $assetPath (looking for $name.$extension)"
            )
            
            // For iOS, we can return the bundle path directly as ONNX Runtime can read from it
            // But for consistency with Android (and in case we need write access), copy to cache
            val cacheDir = NSFileManager.defaultManager.URLsForDirectory(
                NSCachesDirectory,
                NSUserDomainMask
            ).firstOrNull() as? NSURL
                ?: throw IllegalStateException("Could not access cache directory")
            
            val cachePath = cacheDir.path + "/" + assetPath
            val cacheFile = NSURL.fileURLWithPath(cachePath)
            
            // Create parent directory if needed
            val parentDir = (cacheDir.path + "/" + pathComponents.dropLast(1).joinToString("/"))
            val parentURL = NSURL.fileURLWithPath(parentDir)
            NSFileManager.defaultManager.createDirectoryAtURL(
                parentURL,
                true,
                null,
                null
            )
            
            // Copy file if it doesn't exist
            if (!NSFileManager.defaultManager.fileExistsAtPath(cachePath)) {
                val success = NSFileManager.defaultManager.copyItemAtPath(
                    bundlePath,
                    cachePath,
                    null
                )
                
                if (!success) {
                    // If copy fails, just return the bundle path
                    return bundlePath
                }
            }
            
            return cachePath
        }
    }
}

