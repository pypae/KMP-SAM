package ch.slf.whiterisk.kmpsam

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraView() {
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        when (status) {
            AVAuthorizationStatusAuthorized -> {
                permissionGranted = true
                permissionChecked = true
            }
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    permissionGranted = granted
                    permissionChecked = true
                }
            }
            else -> {
                permissionChecked = true
                permissionGranted = false
            }
        }
    }

    when {
        !permissionChecked -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Checking camera permission...")
            }
        }
        !permissionGranted -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is required")
            }
        }
        else -> {
            CameraPreview()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun CameraPreview() {
    val device = remember {
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    }

    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera not available")
        }
        return
    }

    val session = remember {
        AVCaptureSession().apply {
            beginConfiguration()
            sessionPreset = AVCaptureSessionPresetHigh
            
            val input = try {
                AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            } catch (e: Exception) {
                println("Error creating camera input: $e")
                null
            }
            
            if (input != null && canAddInput(input)) {
                addInput(input)
                println("Camera input added successfully")
            } else {
                println("Failed to add camera input")
            }
            
            commitConfiguration()
        }
    }

    val cameraPreviewLayer = remember { 
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    DisposableEffect(session) {
        kotlinx.cinterop.autoreleasepool {
            session.startRunning()
            println("Camera session started: ${session.running}")
        }
        
        onDispose {
            session.stopRunning()
            println("Camera session stopped")
        }
    }

    UIKitView(
        modifier = Modifier.fillMaxSize(),
        background = Color.Black,
        factory = {
            val container = object : UIView(frame = platform.CoreGraphics.CGRectZero.readValue()) {
                override fun layoutSubviews() {
                    super.layoutSubviews()
                    CATransaction.begin()
                    CATransaction.setValue(true, kCATransactionDisableActions)
                    cameraPreviewLayer.setFrame(this.bounds)
                    CATransaction.commit()
                }
            }
            container.layer.addSublayer(cameraPreviewLayer)
            container
        }
    )
}