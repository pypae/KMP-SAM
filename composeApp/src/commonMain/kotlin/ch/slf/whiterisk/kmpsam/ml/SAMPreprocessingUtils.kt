package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Common utilities for SAM preprocessing.
 * 
 * These utilities handle coordinate transformations and preprocessing calculations
 * that are the same across all platforms.
 */
object SAMPreprocessingUtils {
    
    /**
     * Parameters calculated during SAM preprocessing.
     * These are needed to correctly transform coordinates and reverse the preprocessing.
     */
    data class PreprocessingParams(
        val scale: Float,
        val scaledWidth: Int,
        val scaledHeight: Int,
        val padLeft: Int,
        val padTop: Int
    )
    
    /**
     * Calculates the preprocessing parameters for a given image size.
     * 
     * SAM requires images to be 1024×1024, so we scale the image to fit within
     * that size while maintaining aspect ratio, then pad with black pixels.
     * 
     * @param originalWidth Original image width
     * @param originalHeight Original image height
     * @param targetSize Target size for SAM (default 1024)
     * @return PreprocessingParams with scale factor and padding offsets
     */
    fun calculatePreprocessingParams(
        originalWidth: Int,
        originalHeight: Int,
        targetSize: Int = SAMPreprocessingConstants.TARGET_SIZE
    ): PreprocessingParams {
        // Calculate scale to fit image within target size
        val scale = minOf(
            targetSize.toFloat() / originalWidth,
            targetSize.toFloat() / originalHeight
        )
        
        // Calculate dimensions after scaling (before padding)
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        // Calculate padding offsets (center the image)
        val padLeft = (targetSize - scaledWidth) / 2
        val padTop = (targetSize - scaledHeight) / 2
        
        return PreprocessingParams(scale, scaledWidth, scaledHeight, padLeft, padTop)
    }
    
    /**
     * Transforms a point from original image space to SAM input space (1024×1024).
     * 
     * This applies the same scale and padding that was used during image preprocessing.
     * Use this to transform user click coordinates to SAM coordinates.
     * 
     * @param x X coordinate in original image
     * @param y Y coordinate in original image
     * @param originalWidth Original image width
     * @param originalHeight Original image height
     * @return Pair of (x, y) in SAM input space (1024×1024)
     */
    fun transformPointToSAMSpace(
        x: Float,
        y: Float,
        originalWidth: Int,
        originalHeight: Int
    ): Pair<Float, Float> {
        val params = calculatePreprocessingParams(originalWidth, originalHeight)
        return transformPointToSAMSpace(x, y, params)
    }
    
    /**
     * Transforms a point from original image space to SAM input space using
     * pre-calculated preprocessing parameters.
     * 
     * @param x X coordinate in original image
     * @param y Y coordinate in original image
     * @param params Pre-calculated preprocessing parameters
     * @return Pair of (x, y) in SAM input space
     */
    fun transformPointToSAMSpace(
        x: Float,
        y: Float,
        params: PreprocessingParams
    ): Pair<Float, Float> {
        val samX = x * params.scale + params.padLeft
        val samY = y * params.scale + params.padTop
        return Pair(samX, samY)
    }
    
    /**
     * Transforms a point from SAM input space (1024×1024) back to original image space.
     * 
     * This reverses the scale and padding transformations.
     * 
     * @param samX X coordinate in SAM space
     * @param samY Y coordinate in SAM space
     * @param originalWidth Original image width
     * @param originalHeight Original image height
     * @return Pair of (x, y) in original image space
     */
    fun transformPointFromSAMSpace(
        samX: Float,
        samY: Float,
        originalWidth: Int,
        originalHeight: Int
    ): Pair<Float, Float> {
        val params = calculatePreprocessingParams(originalWidth, originalHeight)
        return transformPointFromSAMSpace(samX, samY, params)
    }
    
    /**
     * Transforms a point from SAM input space back to original image space using
     * pre-calculated preprocessing parameters.
     * 
     * @param samX X coordinate in SAM space
     * @param samY Y coordinate in SAM space
     * @param params Pre-calculated preprocessing parameters
     * @return Pair of (x, y) in original image space
     */
    fun transformPointFromSAMSpace(
        samX: Float,
        samY: Float,
        params: PreprocessingParams
    ): Pair<Float, Float> {
        val x = (samX - params.padLeft) / params.scale
        val y = (samY - params.padTop) / params.scale
        return Pair(x, y)
    }
}

