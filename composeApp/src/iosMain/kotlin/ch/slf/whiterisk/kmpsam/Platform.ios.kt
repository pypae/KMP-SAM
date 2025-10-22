package ch.slf.whiterisk.kmpsam

import platform.UIKit.UIDevice
import cocoapods.onnxruntime_objc.ORTVersion
import kotlinx.cinterop.ExperimentalForeignApi

class IOSPlatform: Platform {
    @OptIn(ExperimentalForeignApi::class)
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion + " ONNX Runtime:" + ORTVersion()
}

actual fun getPlatform(): Platform = IOSPlatform()