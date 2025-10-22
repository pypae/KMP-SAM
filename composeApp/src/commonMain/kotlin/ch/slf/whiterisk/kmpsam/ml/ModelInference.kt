package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * Platform-specific ML model inference interface.
 * 
 * This expect/actual class provides a common interface for running ML inference
 * across different platforms (Android with ONNX Runtime, iOS with CoreML, etc.)
 */
expect class ModelInference() {
    
    /**
     * Loads the ML model from the specified path.
     * 
     * @param modelPath Path to the model file
     * @throws Exception if the model cannot be loaded
     */
    fun loadModel(modelPath: String)
    
    /**
     * Runs inference on the provided input data (single input).
     * 
     * @param input Input tensor as a FloatArray
     * @param inputShape Shape of the input tensor (e.g., [1, 3, 224, 224] for batch, channels, height, width)
     * @return Map of output names to output data
     * @throws Exception if inference fails
     */
    fun runInference(input: FloatArray, inputShape: IntArray): Map<String, FloatArray>
    
    /**
     * Runs inference with multiple inputs (e.g., for SAM which needs image + points + labels).
     * 
     * @param inputs Map of input names to (data, shape) pairs
     * @return Map of output names to output data
     * @throws Exception if inference fails
     */
    fun runInferenceMultiInput(inputs: Map<String, Pair<FloatArray, IntArray>>): Map<String, FloatArray>
    
    /**
     * Closes the model and releases resources.
     */
    fun close()
}

