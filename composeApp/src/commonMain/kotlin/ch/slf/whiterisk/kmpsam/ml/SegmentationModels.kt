package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Represents a point prompt for segmentation.
 * 
 * Used by SAM and other interactive segmentation models to specify
 * where the user wants to segment (foreground) or exclude (background).
 */
data class SegmentationPoint(
    val x: Float,
    val y: Float,
    val label: Int = 1  // 1 = foreground point, 0 = background point
)

/**
 * Result of a segmentation operation.
 * 
 * Contains the segmentation mask and optional confidence scores.
 */
data class SegmentationResult(
    val mask: FloatArray,
    val width: Int,
    val height: Int,
    val scores: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as SegmentationResult
        
        if (!mask.contentEquals(other.mask)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (scores != null) {
            if (other.scores == null) return false
            if (!scores.contentEquals(other.scores)) return false
        } else if (other.scores != null) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = mask.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + (scores?.contentHashCode() ?: 0)
        return result
    }
}

