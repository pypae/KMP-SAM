package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Optimized SAM 2.1 segmentation service with two-stage architecture.
 * 
 * SAM uses a two-model approach:
 * 1. Image encoder (preprocessing) - Run once per image to get embeddings
 * 2. Prompt decoder - Run multiple times with different prompts (fast)
 * 
 * This allows interactive segmentation where users can click multiple points
 * on the same image without re-encoding the image each time.
 */
class SAMSegmentationService {
    
    private val imageEncoder = ModelInference()
    private val promptDecoder = ModelInference()
    
    private var isEncoderLoaded = false
    private var isDecoderLoaded = false
    
    // Cache all encoder outputs for the current image
    private var cachedImageEmbeddings: FloatArray? = null
    private var cachedHighResFeatures1: FloatArray? = null
    private var cachedHighResFeatures2: FloatArray? = null
    private var cachedOriginalWidth: Int = 1024
    private var cachedOriginalHeight: Int = 1024
    
    /**
     * Initializes both SAM models (encoder and decoder).
     * 
     * @param preprocessModelPath Path to sam2.1_tiny_preprocess.onnx
     * @param decoderModelPath Path to sam2.1_tiny.onnx
     * @throws Exception if model loading fails
     */
    fun initialize(preprocessModelPath: String, decoderModelPath: String) {
        try {
            imageEncoder.loadModel(preprocessModelPath)
            isEncoderLoaded = true
            
            promptDecoder.loadModel(decoderModelPath)
            isDecoderLoaded = true
        } catch (e: Exception) {
            throw Exception("Failed to initialize SAM models: ${e.message}", e)
        }
    }
    
    /**
     * Encodes an image to embeddings (preprocessing step).
     * This should be called once per new image.
     * 
     * The embeddings are cached so you can run multiple segmentations
     * on the same image without re-encoding.
     * 
     * @param imageData Preprocessed image (1024x1024, RGB, normalized with ImageNet mean/std)
     * @param imageWidth Width of the preprocessed image (typically 1024)
     * @param imageHeight Height of the preprocessed image (typically 1024)
     * @param originalWidth Width of the original image before preprocessing
     * @param originalHeight Height of the original image before preprocessing
     * @throws Exception if encoding fails or encoder is not loaded
     */
    fun encodeImage(
        imageData: FloatArray, 
        imageWidth: Int, 
        imageHeight: Int,
        originalWidth: Int,
        originalHeight: Int
    ) {
        if (!isEncoderLoaded) {
            throw Exception("Image encoder not loaded. Call initialize() first.")
        }
        
        println("SAMSegmentationService: Encoding image ${imageWidth}x${imageHeight}")
        println("SAMSegmentationService: Original image dimensions: ${originalWidth}x${originalHeight}")
        println("SAMSegmentationService: Image data size: ${imageData.size}")
        
        val inputShape = intArrayOf(1, 3, imageHeight, imageWidth)
        println("SAMSegmentationService: Input shape: ${inputShape.contentToString()}")
        
        // Store ORIGINAL dimensions for decoder (critical for correct mask alignment!)
        cachedOriginalWidth = originalWidth
        cachedOriginalHeight = originalHeight
        
        try {
            println("SAMSegmentationService: Running image encoder...")
            // Run image encoder to get ALL outputs (embeddings + high-res features)
            val outputs = imageEncoder.runInference(imageData, inputShape)
            
            println("SAMSegmentationService: ✓ Encoding complete, got ${outputs.size} output(s)")
            outputs.forEach { (name, data) ->
                println("SAMSegmentationService:   Output '$name': size=${data.size}")
            }
            
            // Extract and cache all three encoder outputs
            // SAM 2.1 encoder outputs:
            // - image_embeddings: [1, 256, 64, 64] = 1048576 floats
            // - high_res_features1: [1, 32, 256, 256] = 2097152 floats
            // - high_res_features2: [1, 64, 128, 128] = 1048576 floats
            cachedImageEmbeddings = outputs["image_embeddings"]
                ?: throw Exception("Missing 'image_embeddings' from encoder output")
            
            cachedHighResFeatures1 = outputs["high_res_features1"]
                ?: throw Exception("Missing 'high_res_features1' from encoder output")
            
            cachedHighResFeatures2 = outputs["high_res_features2"]
                ?: throw Exception("Missing 'high_res_features2' from encoder output")
            
            println("SAMSegmentationService: ✓ Cached all encoder outputs:")
            println("SAMSegmentationService:   - image_embeddings: ${cachedImageEmbeddings?.size} floats")
            println("SAMSegmentationService:   - high_res_features1: ${cachedHighResFeatures1?.size} floats")
            println("SAMSegmentationService:   - high_res_features2: ${cachedHighResFeatures2?.size} floats")
            
        } catch (e: Exception) {
            println("SAMSegmentationService ERROR: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to encode image: ${e.message}", e)
        }
    }
    
    /**
     * Segments the previously encoded image with point prompts.
     * 
     * This is fast because it uses cached embeddings.
     * Call encodeImage() first before using this method.
     * 
     * IMPORTANT: This now returns the mask at 1024×1024 (SAM space) and handles
     * de-padding and scaling to original dimensions internally.
     * 
     * @param points List of point prompts (coordinates in SAM space, not original image space!)
     * @param originalImageWidth Original image width (mask will be scaled to this)
     * @param originalImageHeight Original image height (mask will be scaled to this)
     * @return Segmentation result containing mask scaled to original image dimensions
     * @throws Exception if segmentation fails or image not encoded
     */
    fun segmentWithPrompts(
        points: List<SegmentationPoint>,
        originalImageWidth: Int,
        originalImageHeight: Int
    ): SegmentationResult {
        if (!isDecoderLoaded) {
            throw Exception("Prompt decoder not loaded. Call initialize() first.")
        }
        
        val embeddings = cachedImageEmbeddings
            ?: throw Exception("No image encoded. Call encodeImage() first.")
        
        val highResFeatures1 = cachedHighResFeatures1
            ?: throw Exception("No high_res_features1 cached. Call encodeImage() first.")
        
        val highResFeatures2 = cachedHighResFeatures2
            ?: throw Exception("No high_res_features2 cached. Call encodeImage() first.")
        
        if (points.isEmpty()) {
            throw Exception("At least one point prompt is required for SAM segmentation")
        }
        
        println("SAMSegmentationService: Segmenting with ${points.size} point(s)")
        points.forEachIndexed { i, point ->
            println("SAMSegmentationService:   Point $i: (${point.x}, ${point.y}), label=${point.label}")
        }
        
        try {
            // Prepare inputs for the decoder
            println("SAMSegmentationService: Preparing decoder inputs...")
            // Pass 1024×1024 to get mask in SAM space (we'll handle scaling ourselves)
            val inputs = prepareDecoderInputs(
                embeddings, 
                highResFeatures1, 
                highResFeatures2, 
                points,
                1024,  // Request mask in SAM space (1024×1024)
                1024
            )
            println("SAMSegmentationService: Prepared ${inputs.size} inputs")
            inputs.forEach { (name, data) ->
                println("SAMSegmentationService:   Input '$name': shape=${data.second.contentToString()}, size=${data.first.size}")
            }
            
            // Run prompt decoder (fast!)
            println("SAMSegmentationService: Running prompt decoder...")
            val outputs = promptDecoder.runInferenceMultiInput(inputs)
            println("SAMSegmentationService: ✓ Decoder complete, got ${outputs.size} output(s)")
            outputs.forEach { (name, data) ->
                println("SAMSegmentationService:   Output '$name': size=${data.size}")
            }
            
            // Parse output - Get mask at 1024×1024 first
            println("SAMSegmentationService: Parsing SAM output (at 1024×1024)...")
            val samSpaceMask = parseSAMOutput(outputs, 1024, 1024)
            
            // Now scale from SAM space (1024×1024) to original image dimensions
            // accounting for the padding that was added during preprocessing
            println("SAMSegmentationService: Scaling mask from SAM space to original dimensions...")
            val result = scaleMaskToOriginalSize(
                samSpaceMask.mask,
                cachedOriginalWidth,
                cachedOriginalHeight,
                samSpaceMask.scores?.get(0) ?: 1.0f
            )
            println("SAMSegmentationService: ✓ Segmentation complete")
            
            return result
            
        } catch (e: Exception) {
            println("SAMSegmentationService ERROR: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to segment with prompts: ${e.message}", e)
        }
    }
    
    /**
     * Convenience method that does both encoding and segmentation.
     * Use this if you only need to segment once, otherwise use
     * encodeImage() + segmentWithPrompts() separately for better performance.
     * 
     * @param imageData Preprocessed image data (1024x1024)
     * @param imageWidth Width of preprocessed image (1024)
     * @param imageHeight Height of preprocessed image (1024)
     * @param originalWidth Width of original image
     * @param originalHeight Height of original image
     * @param points Point prompts for segmentation
     */
    fun segmentImage(
        imageData: FloatArray,
        imageWidth: Int,
        imageHeight: Int,
        originalWidth: Int,
        originalHeight: Int,
        points: List<SegmentationPoint>
    ): SegmentationResult {
        encodeImage(imageData, imageWidth, imageHeight, originalWidth, originalHeight)
        return segmentWithPrompts(points, originalWidth, originalHeight)
    }
    
    /**
     * Prepares inputs for the SAM decoder model.
     * 
     * SAM 2.1 decoder requires 8 inputs:
     * 1. image_embeddings: [1, 256, 64, 64] - from encoder
     * 2. high_res_features1: [1, 32, 256, 256] - from encoder
     * 3. high_res_features2: [1, 64, 128, 128] - from encoder
     * 4. point_coords: [1, N, 2] - user click coordinates (in SAM space)
     * 5. point_labels: [1, N] - 1=foreground, 0=background
     * 6. mask_input: [1, 1, 256, 256] - optional mask for refinement (zeros if none)
     * 7. has_mask_input: [1] - 0.0 if no mask, 1.0 if mask provided
     * 8. orig_im_size: [2] - Output mask dimensions (INT64)
     * 
     * NOTE: We pass 1024×1024 to get the mask in SAM space, then manually handle
     * de-padding and scaling to the original image dimensions.
     * 
     * @param outputWidth Width of the output mask (1024 for SAM space)
     * @param outputHeight Height of the output mask (1024 for SAM space)
     */
    private fun prepareDecoderInputs(
        embeddings: FloatArray,
        highResFeatures1: FloatArray,
        highResFeatures2: FloatArray,
        points: List<SegmentationPoint>,
        outputWidth: Int,
        outputHeight: Int
    ): Map<String, Pair<FloatArray, IntArray>> {
        val inputs = mutableMapOf<String, Pair<FloatArray, IntArray>>()
        
        // 1. Image embeddings from encoder [1, 256, 64, 64]
        inputs["image_embeddings"] = Pair(embeddings, intArrayOf(1, 256, 64, 64))
        
        // 2. High resolution features 1 [1, 32, 256, 256]
        inputs["high_res_features1"] = Pair(highResFeatures1, intArrayOf(1, 32, 256, 256))
        
        // 3. High resolution features 2 [1, 64, 128, 128]
        inputs["high_res_features2"] = Pair(highResFeatures2, intArrayOf(1, 64, 128, 128))
        
        // 4. Point coordinates: [1, N, 2]
        val numPoints = points.size
        val pointCoords = FloatArray(numPoints * 2)
        for (i in points.indices) {
            pointCoords[i * 2] = points[i].x
            pointCoords[i * 2 + 1] = points[i].y
        }
        inputs["point_coords"] = Pair(pointCoords, intArrayOf(1, numPoints, 2))
        
        // 5. Point labels: [1, N]
        val pointLabels = FloatArray(numPoints) { i -> points[i].label.toFloat() }
        inputs["point_labels"] = Pair(pointLabels, intArrayOf(1, numPoints))
        
        // 6. Mask input: [1, 1, 256, 256] - zeros for no previous mask
        val maskInput = FloatArray(1 * 1 * 256 * 256) { 0f }
        inputs["mask_input"] = Pair(maskInput, intArrayOf(1, 1, 256, 256))
        
        // 7. Has mask input: [1] - 0.0 indicates no mask provided
        val hasMaskInput = FloatArray(1) { 0f }
        inputs["has_mask_input"] = Pair(hasMaskInput, intArrayOf(1))
        
        // 8. Original image size: [2] - INT64 (height, width)
        // This controls the output mask dimensions
        // We pass 1024×1024 to get mask in SAM space, then manually scale it
        // Note: This will be converted to INT64 in the Android implementation
        val origImSize = FloatArray(2)
        origImSize[0] = outputHeight.toFloat()  // Output mask height (1024)
        origImSize[1] = outputWidth.toFloat()   // Output mask width (1024)
        inputs["orig_im_size"] = Pair(origImSize, intArrayOf(2))
        
        println("SAMSegmentationService: orig_im_size = [${outputHeight}, ${outputWidth}] (SAM space)")
        
        return inputs
    }
    
    /**
     * Scales mask from SAM space (1024×1024) to original image dimensions,
     * properly handling the padding that was added during preprocessing.
     * 
     * @param samMask Mask in SAM space (1024×1024)
     * @param originalWidth Original image width
     * @param originalHeight Original image height
     * @param score IoU confidence score from SAM
     * @return Segmentation result with mask at original dimensions
     */
    private fun scaleMaskToOriginalSize(
        samMask: FloatArray,
        originalWidth: Int,
        originalHeight: Int,
        score: Float
    ): SegmentationResult {
        val samSize = 1024
        
        println("SAMSegmentationService: === MASK SCALING ===")
        println("SAMSegmentationService: Original image: ${originalWidth}×${originalHeight}")
        
        // Use common preprocessing calculation to ensure consistency
        val params = SAMPreprocessingUtils.calculatePreprocessingParams(
            originalWidth,
            originalHeight,
            samSize
        )
        
        println("SAMSegmentationService: Scale factor: ${params.scale}")
        println("SAMSegmentationService: Scaled dimensions (before padding): ${params.scaledWidth}×${params.scaledHeight}")
        println("SAMSegmentationService: Padding: left=${params.padLeft}, top=${params.padTop}")
        println("SAMSegmentationService: Image region in SAM space: [${params.padLeft}, ${params.padLeft + params.scaledWidth}] × [${params.padTop}, ${params.padTop + params.scaledHeight}]")
        
        // Extract the region that contains the actual image (remove padding)
        val unpaddedMask = FloatArray(params.scaledWidth * params.scaledHeight)
        for (y in 0 until params.scaledHeight) {
            for (x in 0 until params.scaledWidth) {
                val samX = x + params.padLeft
                val samY = y + params.padTop
                val samIndex = samY * samSize + samX
                val unpaddedIndex = y * params.scaledWidth + x
                unpaddedMask[unpaddedIndex] = samMask[samIndex]
            }
        }
        
        println("SAMSegmentationService: Extracted unpadded mask: ${params.scaledWidth}×${params.scaledHeight}")
        
        // Now scale from (scaledWidth × scaledHeight) to (originalWidth × originalHeight)
        val finalMask = FloatArray(originalWidth * originalHeight)
        
        for (y in 0 until originalHeight) {
            for (x in 0 until originalWidth) {
                // Map to unpadded mask coordinates
                val srcX = (x * params.scaledWidth.toFloat() / originalWidth).toInt().coerceIn(0, params.scaledWidth - 1)
                val srcY = (y * params.scaledHeight.toFloat() / originalHeight).toInt().coerceIn(0, params.scaledHeight - 1)
                
                val srcIndex = srcY * params.scaledWidth + srcX
                val dstIndex = y * originalWidth + x
                finalMask[dstIndex] = unpaddedMask[srcIndex]
            }
        }
        
        val nonZeroCount = finalMask.count { it > 0.5f }
        val percentCovered = (nonZeroCount.toFloat() / finalMask.size) * 100f
        println("SAMSegmentationService: Final mask: ${originalWidth}×${originalHeight}")
        println("SAMSegmentationService: Coverage: $nonZeroCount pixels (${percentCovered}%)")
        println("SAMSegmentationService: IoU score: $score")
        println("SAMSegmentationService: === END MASK SCALING ===")
        
        return SegmentationResult(
            mask = finalMask,
            width = originalWidth,
            height = originalHeight,
            scores = floatArrayOf(score)
        )
    }
    
    /**
     * Parses SAM decoder output and selects the best mask.
     * 
     * SAM outputs masks in [1, num_masks, H, W] format where H=height, W=width
     */
    private fun parseSAMOutput(
        outputs: Map<String, FloatArray>,
        width: Int,
        height: Int
    ): SegmentationResult {
        // SAM outputs:
        // - "masks": [1, num_masks, H, W] - typically 4 masks
        // - "iou_predictions": [1, num_masks] - confidence scores
        
        val allMasks = outputs["masks"] ?: outputs.values.firstOrNull()
            ?: throw Exception("No mask output from model")
        
        val scores = outputs["iou_predictions"] ?: outputs["scores"]
            ?: throw Exception("No IoU scores from model")
        
        println("SAMSegmentationService: ========== MASK PARSING ==========")
        println("SAMSegmentationService: All masks size: ${allMasks.size}")
        println("SAMSegmentationService: Expected dimensions: ${width}x${height} (W×H)")
        println("SAMSegmentationService: Scores size: ${scores.size}")
        println("SAMSegmentationService: Scores: ${scores.contentToString()}")
        
        // Calculate mask size - each mask is H × W pixels
        val maskSize = width * height
        val numMasks = allMasks.size / maskSize
        
        println("SAMSegmentationService: Calculated mask size: $maskSize pixels")
        println("SAMSegmentationService: Number of masks: $numMasks, each ${width}x${height}")
        println("SAMSegmentationService: Verification: ${numMasks} × $maskSize = ${numMasks * maskSize} (should equal ${allMasks.size})")
        
        // Find the mask with the highest IoU score
        var bestMaskIndex = 0
        var bestScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestMaskIndex = i
            }
        }
        
        println("SAMSegmentationService: Best mask index: $bestMaskIndex with score: $bestScore")
        
        // Extract the best mask
        val bestMask = FloatArray(maskSize)
        val startIndex = bestMaskIndex * maskSize
        allMasks.copyInto(bestMask, 0, startIndex, startIndex + maskSize)
        
        // Debug: Check mask value statistics
        val nonZeroCount = bestMask.count { it > 0.5f }
        val percentCovered = (nonZeroCount.toFloat() / maskSize) * 100f
        println("SAMSegmentationService: Mask coverage: $nonZeroCount / $maskSize pixels (${percentCovered}%)")
        
        // Debug: Sample a few mask values to verify data
        println("SAMSegmentationService: Sample mask values (first 10): ${bestMask.take(10)}")
        println("SAMSegmentationService: Sample mask values (middle 10): ${bestMask.drop(maskSize/2).take(10)}")
        
        println("SAMSegmentationService: ========== END MASK PARSING ==========")
        
        return SegmentationResult(
            mask = bestMask,
            width = width,
            height = height,
            scores = floatArrayOf(bestScore)
        )
    }
    
    /**
     * Clears cached embeddings to free memory.
     * Call this when switching to a new image.
     */
    fun clearCache() {
        cachedImageEmbeddings = null
        cachedHighResFeatures1 = null
        cachedHighResFeatures2 = null
        cachedOriginalWidth = 1024
        cachedOriginalHeight = 1024
    }
    
    /**
     * Releases all model resources.
     */
    fun close() {
        imageEncoder.close()
        promptDecoder.close()
        isEncoderLoaded = false
        isDecoderLoaded = false
        clearCache()
    }
}

