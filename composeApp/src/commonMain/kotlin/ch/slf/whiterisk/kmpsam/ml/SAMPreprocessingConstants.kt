package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Constants and utilities for SAM image preprocessing.
 */
object SAMPreprocessingConstants {
    
    /**
     * Target size for SAM input images (1024×1024).
     */
    const val TARGET_SIZE = 1024
    
    /**
     * ImageNet normalization constants (RGB channels).
     * SAM models are typically trained with ImageNet normalization.
     */
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)  // RGB
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)   // RGB
    
    /**
     * Normalizes pixel values and converts from HWC (Height-Width-Channel) format
     * to CHW (Channel-Height-Width) format required by neural networks.
     * 
     * Input pixels are expected to be in ARGB integer format (Android Bitmap format).
     * 
     * Process:
     * 1. Extract RGB channels from ARGB integers
     * 2. Normalize to [0, 1] by dividing by 255
     * 3. Apply ImageNet mean/std normalization
     * 4. Rearrange from HWC to CHW format
     * 
     * @param pixels ARGB pixel array (from Bitmap.getPixels())
     * @param width Image width
     * @param height Image height
     * @return Normalized float array in CHW format [C, H, W]
     */
    fun normalizeAndConvertToCHW(pixels: IntArray, width: Int, height: Int): FloatArray {
        val chw = FloatArray(3 * height * width)
        
        for (h in 0 until height) {
            for (w in 0 until width) {
                val pixel = pixels[h * width + w]
                
                // Extract RGB channels from ARGB
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                
                // Normalize with ImageNet mean/std and store in CHW format
                chw[0 * height * width + h * width + w] = (r - MEAN[0]) / STD[0]  // Red channel
                chw[1 * height * width + h * width + w] = (g - MEAN[1]) / STD[1]  // Green channel
                chw[2 * height * width + h * width + w] = (b - MEAN[2]) / STD[2]  // Blue channel
            }
        }
        
        return chw
    }
}

