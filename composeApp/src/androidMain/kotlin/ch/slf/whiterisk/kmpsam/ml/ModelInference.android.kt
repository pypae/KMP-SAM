package ch.slf.whiterisk.multiplatform.domain.conditions.ml

import ai.onnxruntime.OnnxMap
import ai.onnxruntime.OnnxSequence
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.SequenceInfo
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Android implementation of ModelInference using ONNX Runtime.
 */
actual class ModelInference {
    
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    
    companion object {
        private const val TAG = "ModelInference"
    }
    
    /**
     * Loads the ONNX model from the specified path.
     * 
     * @param modelPath Path to the .onnx model file
     * @throws Exception if the model cannot be loaded
     */
    actual fun loadModel(modelPath: String) {
        try {
            Log.d(TAG, "Loading model from: $modelPath")
            session = env.createSession(modelPath)
            
            val currentSession = session!!
            
            // Log input information
            val inputInfo = currentSession.inputInfo
            Log.d(TAG, "✓ Model loaded successfully")
            Log.d(TAG, "Model has ${inputInfo.size} input(s):")
            inputInfo.forEach { (name, nodeInfo) ->
                when (val info = nodeInfo.info) {
                    is TensorInfo -> {
                        val shape = info.shape
                        Log.d(TAG, "  • Input '$name': Tensor, shape=${shape.contentToString()}, type=${info.type}")
                    }
                    else -> {
                        Log.d(TAG, "  • Input '$name': ${info.javaClass.simpleName}")
                    }
                }
            }
            
            // Log output information
            val outputInfo = currentSession.outputInfo
            Log.d(TAG, "Model has ${outputInfo.size} output(s):")
            outputInfo.forEach { (name, nodeInfo) ->
                when (val info = nodeInfo.info) {
                    is TensorInfo -> {
                        val shape = info.shape
                        Log.d(TAG, "  • Output '$name': Tensor, shape=${shape.contentToString()}, type=${info.type}")
                    }
                    is SequenceInfo -> {
                        Log.d(TAG, "  • Output '$name': Sequence")
                    }
                    is ai.onnxruntime.MapInfo -> {
                        Log.d(TAG, "  • Output '$name': Map")
                    }
                    else -> {
                        Log.d(TAG, "  • Output '$name': ${info.javaClass.simpleName}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model from $modelPath", e)
            throw Exception("Failed to load ONNX model from $modelPath: ${e.message}", e)
        }
    }
    
    /**
     * Runs inference on the provided input data using ONNX Runtime.
     * 
     * @param input Input tensor as a FloatArray (flattened)
     * @param inputShape Shape of the input tensor (e.g., [1, 3, 224, 224])
     * @return Map of output names to output data
     * @throws Exception if inference fails or session is not initialized
     */
    actual fun runInference(input: FloatArray, inputShape: IntArray): Map<String, FloatArray> {
        val currentSession = session 
            ?: throw Exception("Model not loaded. Call loadModel() first.")
        
        try {
            // Convert IntArray to LongArray for ONNX Runtime
            val shape = inputShape.map { it.toLong() }.toLongArray()
            
            Log.d(TAG, "Running single-input inference")
            Log.d(TAG, "  Input data size: ${input.size}")
            Log.d(TAG, "  Input shape: ${inputShape.contentToString()}")
            
            // Create input tensor
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(input),
                shape
            )
            
            // Get the input name (usually the model has one input)
            val inputName = currentSession.inputNames.firstOrNull() 
                ?: throw Exception("Model has no input names defined")
            
            Log.d(TAG, "  Input name: '$inputName'")
            
            // Run inference
            Log.d(TAG, "  Running model...")
            val results = currentSession.run(mapOf(inputName to inputTensor))
            Log.d(TAG, "  ✓ Inference complete, got ${results.size()} output(s)")
            
            // Extract all outputs
            val outputs = mutableMapOf<String, FloatArray>()
            
            for (outputName in currentSession.outputNames) {
                var outputValue = results.get(outputName).orElse(null) ?: continue
                
                Log.d(TAG, "  Output '$outputName' type: ${outputValue.javaClass.simpleName}")
                
                // Unwrap Optional if present
                if (outputValue is java.util.Optional<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val unwrapped = (outputValue as java.util.Optional<ai.onnxruntime.OnnxValue>).orElse(null)
                    if (unwrapped == null) continue
                    outputValue = unwrapped
                }
                
                // Handle different output types
                when (outputValue) {
                    is OnnxTensor -> {
                        val outputBuffer = outputValue.floatBuffer
                        val outputArray = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(outputArray)
                        Log.d(TAG, "  ✓ Extracted ${outputArray.size} floats from '$outputName'")
                        outputs[outputName] = outputArray
                    }
                    else -> {
                        Log.w(TAG, "  ⚠ Skipping non-tensor output '$outputName'")
                    }
                }
            }
            
            // Clean up
            inputTensor.close()
            results.close()
            
            if (outputs.isEmpty()) {
                throw Exception("No tensor outputs found.")
            }
            
            return outputs
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            throw Exception("Failed to run inference: ${e.message}", e)
        }
    }
    
    /**
     * Runs inference with multiple inputs (for models like SAM that need multiple tensors).
     * 
     * @param inputs Map of input names to (data, shape) pairs
     * @return Map of output names to output data
     * @throws Exception if inference fails
     */
    actual fun runInferenceMultiInput(inputs: Map<String, Pair<FloatArray, IntArray>>): Map<String, FloatArray> {
        val currentSession = session 
            ?: throw Exception("Model not loaded. Call loadModel() first.")
        
        try {
            Log.d(TAG, "Running multi-input inference")
            Log.d(TAG, "  Number of inputs: ${inputs.size}")
            
            // Get model input info to determine expected types
            val modelInputInfo = currentSession.inputInfo
            
            // Create input tensors
            val inputTensors = mutableMapOf<String, OnnxTensor>()
            
            for ((name, dataAndShape) in inputs) {
                val (data, shape) = dataAndShape
                val longShape = shape.map { it.toLong() }.toLongArray()
                
                Log.d(TAG, "  Creating input '$name': shape=${shape.contentToString()}, data size=${data.size}")
                
                // Check if this input expects INT64
                val inputInfo = modelInputInfo[name]
                val isInt64 = inputInfo?.info is TensorInfo && 
                              (inputInfo.info as TensorInfo).type.toString().contains("INT64")
                
                val tensor = if (isInt64) {
                    // Convert float data to long for INT64 inputs
                    val longData = data.map { it.toLong() }.toLongArray()
                    Log.d(TAG, "    Creating INT64 tensor for '$name'")
                    OnnxTensor.createTensor(env, LongBuffer.wrap(longData), longShape)
                } else {
                    // Regular float tensor
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longShape)
                }
                
                inputTensors[name] = tensor
            }
            
            // Run inference
            Log.d(TAG, "  Running model...")
            val results = currentSession.run(inputTensors)
            Log.d(TAG, "  ✓ Inference complete, got ${results.size()} output(s)")
            
            // Extract all outputs
            val outputs = mutableMapOf<String, FloatArray>()
            
            for (outputName in currentSession.outputNames) {
                var outputValue = results.get(outputName).orElse(null) ?: continue
                
                // Log output details
                Log.d(TAG, "  Output: name='$outputName'")
                Log.d(TAG, "    Type: ${outputValue.javaClass.simpleName}")
                
                // Unwrap Optional if present (ONNX Runtime sometimes wraps outputs)
                if (outputValue is java.util.Optional<*>) {
                    Log.d(TAG, "    Unwrapping Optional...")
                    @Suppress("UNCHECKED_CAST")
                    val unwrapped = (outputValue as java.util.Optional<ai.onnxruntime.OnnxValue>).orElse(null)
                    if (unwrapped == null) {
                        Log.w(TAG, "    ⚠ Optional was empty, skipping")
                        continue
                    }
                    outputValue = unwrapped
                    Log.d(TAG, "    Unwrapped type: ${outputValue.javaClass.simpleName}")
                }
                
                // Handle different output types
                when (outputValue) {
                    is OnnxTensor -> {
                        Log.d(TAG, "    ✓ Processing as OnnxTensor")
                        val outputBuffer = outputValue.floatBuffer
                        val outputArray = FloatArray(outputBuffer.remaining())
                        outputBuffer.get(outputArray)
                        Log.d(TAG, "    ✓ Extracted ${outputArray.size} floats")
                        outputs[outputName] = outputArray
                    }
                    is OnnxSequence -> {
                        Log.w(TAG, "    ⚠ Output is OnnxSequence (skipping)")
                        Log.w(TAG, "      Sequence outputs are not currently supported")
                        // Skip sequence outputs for now
                    }
                    is OnnxMap -> {
                        Log.w(TAG, "    ⚠ Output is OnnxMap (skipping)")
                        // Skip map outputs for now
                    }
                    else -> {
                        Log.w(TAG, "    ⚠ Unknown output type: ${outputValue.javaClass.name} (skipping)")
                    }
                }
            }
            
            // Clean up
            inputTensors.values.forEach { it.close() }
            results.close()
            
            Log.d(TAG, "  ✓ Successfully extracted ${outputs.size} tensor output(s)")
            
            if (outputs.isEmpty()) {
                throw Exception("No tensor outputs found. Model may output sequence or map types which are not supported.")
            }
            
            return outputs
            
        } catch (e: Exception) {
            Log.e(TAG, "Multi-input inference failed", e)
            throw Exception("Failed to run multi-input inference: ${e.message}", e)
        }
    }
    
    /**
     * Closes the ONNX Runtime session and releases resources.
     */
    actual fun close() {
        session?.close()
        session = null
    }
}

