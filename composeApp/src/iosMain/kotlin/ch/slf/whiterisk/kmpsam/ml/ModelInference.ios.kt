package ch.slf.whiterisk.multiplatform.domain.conditions.ml

/**
 * iOS implementation of ModelInference.
 * 
 * TODO: Implement using CoreML or ONNX Runtime for iOS when needed.
 */
actual class ModelInference {
    
    /**
     * Loads the ML model from the specified path.
     * 
     * @param modelPath Path to the model file
     * @throws NotImplementedError iOS implementation not yet available
     */
    actual fun loadModel(modelPath: String) {
        throw NotImplementedError("iOS ModelInference implementation is not yet available. Use CoreML or ONNX Runtime for iOS.")
    }
    
    /**
     * Runs inference on the provided input data.
     * 
     * @param input Input tensor as a FloatArray
     * @param inputShape Shape of the input tensor
     * @return Map of output names to output data
     * @throws NotImplementedError iOS implementation not yet available
     */
    actual fun runInference(input: FloatArray, inputShape: IntArray): Map<String, FloatArray> {
        throw NotImplementedError("iOS ModelInference implementation is not yet available. Use CoreML or ONNX Runtime for iOS.")
    }
    
    /**
     * Runs inference with multiple inputs.
     * 
     * @param inputs Map of input names to (data, shape) pairs
     * @return Map of output names to output data
     * @throws NotImplementedError iOS implementation not yet available
     */
    actual fun runInferenceMultiInput(inputs: Map<String, Pair<FloatArray, IntArray>>): Map<String, FloatArray> {
        throw NotImplementedError("iOS ModelInference implementation is not yet available. Use CoreML or ONNX Runtime for iOS.")
    }
    
    /**
     * Closes the model and releases resources.
     */
    actual fun close() {
        // No-op for now
    }
}

