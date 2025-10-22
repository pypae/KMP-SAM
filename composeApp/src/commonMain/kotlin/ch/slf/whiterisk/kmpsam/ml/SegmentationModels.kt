package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * A point prompt for SAM segmentation.
 * 
 * @param x X coordinate in SAM space (0-1024)
 * @param y Y coordinate in SAM space (0-1024)
 * @param label 1 for foreground point, 0 for background point
 */
data class SegmentationPoint(
    val x: Float,
    val y: Float,
    val label: Int
)

/**
 * Result of SAM segmentation.
 * 
 * @param mask Binary mask (values 0.0-1.0, typically thresholded at 0.5)
 * @param width Width of the mask
 * @param height Height of the mask  
 * @param scores IoU confidence scores for each mask (SAM generates multiple masks)
 */
data class SegmentationResult(
    val mask: FloatArray,
    val width: Int,
    val height: Int,
    val scores: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SegmentationResult

        if (!mask.contentEquals(other.mask)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!scores.contentEquals(other.scores)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mask.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + scores.contentHashCode()
        return result
    }
}
