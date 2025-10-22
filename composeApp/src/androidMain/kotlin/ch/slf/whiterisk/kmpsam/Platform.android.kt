package ch.slf.whiterisk.kmpsam

import android.os.Build
import ai.onnxruntime.OrtEnvironment

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT} ONNX Runtime: ${OrtEnvironment.getEnvironment().version}"
}

actual fun getPlatform(): Platform = AndroidPlatform()