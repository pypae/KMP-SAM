package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Platform-specific SAM image preprocessing utilities.
 * 
 * This expect/actual class handles platform-specific image operations
 * (bitmap/UIImage manipulation, pixel extraction) while delegating
 * coordinate transformations to the shared SAMPreprocessingUtils.
 */
expect class SAMImagePreprocessor {
    companion object {
        /**
         * Preprocesses a platform-specific image for SAM inference.
         * 
         * Steps:
         * 1. Resize to 1024x1024 (with padding to maintain aspect ratio)
         * 2. Convert to RGB pixel array
         * 3. Normalize with ImageNet mean/std
         * 4. Convert to CHW format
         * 
         * @param image Platform-specific image type (Bitmap on Android, UIImage on iOS)
         * @return Preprocessed float array ready for SAM
         */
        fun preprocessImage(image: Any): FloatArray
        
        /**
         * Converts point coordinates from original image space to SAM input space.
         * 
         * This is a convenience method that delegates to SAMPreprocessingUtils.
         * 
         * @param x X coordinate in original image
         * @param y Y coordinate in original image
         * @param originalWidth Original image width
         * @param originalHeight Original image height
         * @return Pair of (x, y) in SAM input space
         */
        fun transformPointToSAMSpace(
            x: Float,
            y: Float,
            originalWidth: Int,
            originalHeight: Int
        ): Pair<Float, Float>
        
        /**
         * Copies a model file from platform-specific assets to a usable location.
         * 
         * @param context Platform-specific context (Android Context, or path on iOS)
         * @param assetPath Path to the model in assets
         * @return Absolute path to the cached model file
         */
        fun copyModelFromAssets(context: Any, assetPath: String): String
    }
}

