package ch.slf.whiterisk.multiplatform.domain.conditions.ml

import cocoapods.onnxruntime_objc.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.NSObject

/**
 * iOS implementation of ModelInference using ONNX Runtime.
 */
@OptIn(ExperimentalForeignApi::class)
actual class ModelInference {

    private var env: ORTEnv? = null
    private var session: ORTSession? = null
    private var sessionOptions: ORTSessionOptions? = null

    // Store input and output metadata
    private val inputNames = mutableListOf<String>()
    private val outputNames = mutableListOf<String>()

    companion object {
        private const val TAG = "ModelInference.iOS"

        private fun log(message: String) {
            println("[$TAG] $message")
        }

        private fun logError(message: String, error: Throwable? = null) {
            println("[$TAG] ERROR: $message")
            error?.let { println("  ${it.message}") }
        }
    }

    /**
     * Loads the ONNX model from the specified path.
     */
    actual fun loadModel(modelPath: String) {
        try {
            log("Loading model from: $modelPath")

            // Check if file exists
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(modelPath)) {
                throw IllegalStateException("Model file not found: $modelPath")
            }

            // Create ONNX Runtime environment
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                env = ORTEnv(ORTLoggingLevel.ORTLoggingLevelWarning, errorPtr.ptr)

                errorPtr.value?.let { error ->
                    throw Exception("Failed to create ONNX Runtime environment: ${error.localizedDescription}")
                }
            }

            // Create session options
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                sessionOptions = ORTSessionOptions(errorPtr.ptr)

                errorPtr.value?.let { error ->
                    throw Exception("Failed to create session options: ${error.localizedDescription}")
                }
            }

            // Create session
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val currentEnv = env ?: throw Exception("Environment not initialized")
                val currentOptions = sessionOptions ?: throw Exception("Session options not initialized")

                session = ORTSession(
                    env = currentEnv,
                    modelPath = modelPath,
                    sessionOptions = currentOptions,
                    error = errorPtr.ptr
                )

                errorPtr.value?.let { error ->
                    throw Exception("Failed to create session: ${error.localizedDescription}")
                }
            }

            val currentSession = session ?: throw Exception("Session not created")

            // Get input names
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val inputs = currentSession.inputNamesWithError(errorPtr.ptr) as? List<*>

                errorPtr.value?.let { error ->
                    throw Exception("Failed to get input names: ${error.localizedDescription}")
                }

                inputs?.forEach { name ->
                    (name as? String)?.let { inputNames.add(it) }
                }
            }

            // Get output names
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val outputs = currentSession.outputNamesWithError(errorPtr.ptr) as? List<*>

                errorPtr.value?.let { error ->
                    throw Exception("Failed to get output names: ${error.localizedDescription}")
                }

                outputs?.forEach { name ->
                    (name as? String)?.let { outputNames.add(it) }
                }
            }

            log("✓ Model loaded successfully")
            log("Model has ${inputNames.size} input(s):")
            inputNames.forEach { log("  • Input: '$it'") }
            log("Model has ${outputNames.size} output(s):")
            outputNames.forEach { log("  • Output: '$it'") }

        } catch (e: Exception) {
            logError("Failed to load model from $modelPath", e)
            close()
            throw Exception("Failed to load ONNX model from $modelPath: ${e.message}", e)
        }
    }

    actual fun runInference(input: FloatArray, inputShape: IntArray): Map<String, FloatArray> {
        val currentSession = session
            ?: throw Exception("Model not loaded. Call loadModel() first.")

        try {
            log("Running single-input inference")
            log("  Input data size: ${input.size}")
            log("  Input shape: ${inputShape.contentToString()}")

            val inputName = inputNames.firstOrNull()
                ?: throw Exception("Model has no inputs defined")

            log("  Input name: '$inputName'")

            val inputTensor = createFloatTensor(input, inputShape)
            val inputsMap = mapOf(inputName to inputTensor)

            log("  Running model...")
            val outputs = runSessionInference(currentSession, inputsMap, outputNames.toSet())
            log("  ✓ Inference complete, got ${outputs.size} output(s)")

            return outputs

        } catch (e: Exception) {
            logError("Inference failed", e)
            throw Exception("Failed to run inference: ${e.message}", e)
        }
    }

    actual fun runInferenceMultiInput(inputs: Map<String, Pair<FloatArray, IntArray>>): Map<String, FloatArray> {
        val currentSession = session
            ?: throw Exception("Model not loaded. Call loadModel() first.")

        try {
            log("Running multi-input inference")
            log("  Number of inputs: ${inputs.size}")

            val inputTensors = mutableMapOf<String, ORTValue>()

            for ((name, dataAndShape) in inputs) {
                val (data, shape) = dataAndShape
                log("  Creating input '$name': shape=${shape.contentToString()}, data size=${data.size}")
                
                // Only orig_im_size should be INT64, everything else is FLOAT
                val isInt64 = name == "orig_im_size"
                
                val tensor = if (isInt64) {
                    log("    Creating INT64 tensor for '$name'")
                    createInt64Tensor(data, shape)
                } else {
                    createFloatTensor(data, shape)
                }
                
                inputTensors[name] = tensor
            }

            log("  Running model...")
            val outputs = runSessionInference(currentSession, inputTensors, outputNames.toSet())
            log("  ✓ Inference complete, got ${outputs.size} output(s)")

            return outputs

        } catch (e: Exception) {
            logError("Multi-input inference failed", e)
            throw Exception("Failed to run multi-input inference: ${e.message}", e)
        }
    }

    private fun createFloatTensor(data: FloatArray, shape: IntArray): ORTValue {
        return memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            // Convert shape to List<NSNumber>
            val shapeNSArray: List<NSNumber> = shape.map { NSNumber(long = it.toLong()) }

            // Create NSMutableData from FloatArray
            val nsData = data.usePinned { pinned ->
                NSMutableData.dataWithBytes(
                    bytes = pinned.addressOf(0),
                    length = (data.size * 4).toULong()
                )
            }

            // Create tensor
            val tensor = ORTValue(
                tensorData = nsData as NSMutableData,
                elementType = ORTTensorElementDataType.ORTTensorElementDataTypeFloat,
                shape = shapeNSArray,
                error = errorPtr.ptr
            )

            errorPtr.value?.let { error ->
                throw Exception("Failed to create float tensor: ${error.localizedDescription}")
            }

            tensor
        }
    }

    private fun createInt64Tensor(data: FloatArray, shape: IntArray): ORTValue {
        return memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            val longData = data.map { it.toLong() }.toLongArray()
            val shapeNSArray: List<NSNumber> = shape.map { NSNumber(long = it.toLong()) }

            val nsData = longData.usePinned { pinned ->
                NSMutableData.dataWithBytes(
                    bytes = pinned.addressOf(0),
                    length = (longData.size * 8).toULong()
                )
            }

            val tensor = ORTValue(
                tensorData = nsData as NSMutableData,
                elementType = ORTTensorElementDataType.ORTTensorElementDataTypeInt64,
                shape = shapeNSArray,
                error = errorPtr.ptr
            )

            errorPtr.value?.let { error ->
                throw Exception("Failed to create INT64 tensor: ${error.localizedDescription}")
            }

            tensor
        }
    }

    private fun runSessionInference(
        session: ORTSession,
        inputs: Map<String, ORTValue>,
        requestedOutputNames: Set<String>
    ): Map<String, FloatArray> {
        return memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            // Convert to proper types for Objective-C
            val inputsMap: Map<Any?, Any?> = inputs.mapKeys { it.key as Any? }.mapValues { it.value as Any? }
            val outputNamesSet: Set<Any?> = requestedOutputNames.map { it as Any? }.toSet()

            // Run inference
            val outputsDict: Map<Any?, *>? = session.runWithInputs(
                inputs = inputsMap,
                outputNames = outputNamesSet,
                runOptions = null,
                error = errorPtr.ptr
            )

            errorPtr.value?.let { error ->
                throw Exception("Inference failed: ${error.localizedDescription}")
            }

            outputsDict ?: throw Exception("Inference returned no outputs")

            // Extract float arrays from outputs
            val outputs = mutableMapOf<String, FloatArray>()

            for (outputName in requestedOutputNames) {
                val outputValue = outputsDict[outputName] as? ORTValue ?: continue

                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val tensorTypeInfo = outputValue.tensorTypeAndShapeInfoWithError(errorPtr.ptr)

                    errorPtr.value?.let { return@memScoped }

                    if (tensorTypeInfo?.elementType == ORTTensorElementDataType.ORTTensorElementDataTypeFloat) {
                        val errorPtr2 = alloc<ObjCObjectVar<NSError?>>()
                        val tensorData = outputValue.tensorDataWithError(errorPtr2.ptr)

                        errorPtr2.value?.let { return@memScoped }

                        if (tensorData != null && tensorTypeInfo != null) {
                            // Calculate element count from shape
                            val shape = tensorTypeInfo.shape
                            var elementCount = 1
                            for (dim in shape) {
                                elementCount *= (dim as NSNumber).intValue
                            }

                            val floatArray = FloatArray(elementCount)

                            // Copy data
                            tensorData.bytes?.let { bytesPtr ->
                                floatArray.usePinned { pinned ->
                                    platform.posix.memcpy(
                                        pinned.addressOf(0),
                                        bytesPtr,
                                        (elementCount * 4).toULong()
                                    )
                                }
                            }

                            log("  ✓ Extracted ${floatArray.size} floats from '$outputName'")
                            outputs[outputName] = floatArray
                        }
                    }
                }
            }

            if (outputs.isEmpty()) {
                throw Exception("No float tensor outputs extracted")
            }

            outputs
        }
    }

    actual fun close() {
        log("Closing model session")
        session = null
        sessionOptions = null
        env = null
        inputNames.clear()
        outputNames.clear()
    }
}

