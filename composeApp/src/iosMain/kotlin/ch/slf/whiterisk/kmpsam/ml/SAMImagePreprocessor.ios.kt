package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * iOS implementation of SAM image preprocessing.
 * 
 * TODO: Implement using UIImage processing when needed.
 */
actual class SAMImagePreprocessor {
    
    actual companion object {
        
        /**
         * Preprocesses a UIImage for SAM inference.
         * 
         * TODO: Implement UIImage preprocessing:
         * 1. Resize to 1024x1024 (with padding to maintain aspect ratio)
         * 2. Extract pixel data as RGB
         * 3. Normalize with ImageNet mean/std
         * 4. Convert to CHW format
         * 
         * @param image Input image (should be UIImage on iOS)
         * @return Preprocessed float array ready for SAM
         * @throws NotImplementedError iOS implementation not yet available
         */
        actual fun preprocessImage(image: Any): FloatArray {
            throw NotImplementedError(
                "iOS SAMImagePreprocessor.preprocessImage is not yet implemented. " +
                "Implement UIImage preprocessing similar to Android's Bitmap preprocessing."
            )
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
            // This works on iOS! It's shared common code
            return SAMPreprocessingUtils.transformPointToSAMSpace(
                x, y, originalWidth, originalHeight
            )
        }
        
        /**
         * Copies a model file from the iOS app bundle to a usable location.
         * 
         * TODO: Implement iOS-specific asset copying:
         * - Use Bundle.main.path(forResource:ofType:) to locate the model
         * - Copy to appropriate cache directory if needed
         * 
         * @param context Platform-specific context (iOS doesn't need context, can use path string)
         * @param assetPath Path to the model in app bundle
         * @return Absolute path to the model file
         * @throws NotImplementedError iOS implementation not yet available
         */
        actual fun copyModelFromAssets(context: Any, assetPath: String): String {
            throw NotImplementedError(
                "iOS SAMImagePreprocessor.copyModelFromAssets is not yet implemented. " +
                "Use Bundle.main to locate model files in the iOS app bundle."
            )
        }
    }
}

