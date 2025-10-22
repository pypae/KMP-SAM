package ch.slf.whiterisk.kmpsam

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image
import platform.AVFoundation.*
import platform.Foundation.getBytes
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraView(onImageCaptured: (ImageBitmap?) -> Unit, onDismiss: () -> Unit) {
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }
    var pickerPresented by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        when (status) {
            AVAuthorizationStatusAuthorized -> {
                permissionGranted = true
                permissionChecked = true
            }
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        permissionGranted = granted
                        permissionChecked = true
                    }
                }
            }
            else -> {
                permissionChecked = true
                permissionGranted = false
            }
        }
    }

    LaunchedEffect(permissionChecked, permissionGranted, pickerPresented) {
        if (permissionChecked && !pickerPresented) {
            if (permissionGranted) {
                pickerPresented = true
                presentImagePicker(
                    onImageCaptured = { image ->
                        dispatch_async(dispatch_get_main_queue()) {
                            onImageCaptured(image)
                        }
                    },
                    onDismiss = {
                        dispatch_async(dispatch_get_main_queue()) {
                            onDismiss()
                        }
                    }
                )
            } else {
                onDismiss()
            }
        }
    }
}

// Store delegate as a global to keep it alive
private var currentDelegate: Any? = null

@OptIn(ExperimentalForeignApi::class)
private fun presentImagePicker(
    onImageCaptured: (ImageBitmap?) -> Unit,
    onDismiss: () -> Unit
) {
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    picker.allowsEditing = false
    picker.mediaTypes = listOf(UTTypeImage.identifier)
    
    val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol,
        UINavigationControllerDelegateProtocol {
        
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            
            picker.dismissViewControllerAnimated(true) {
                dispatch_async(dispatch_get_main_queue()) {
                    if (image != null) {
                        val imageBitmap = image.toImageBitmap()
                        onImageCaptured(imageBitmap)
                    } else {
                        onImageCaptured(null)
                    }
                    currentDelegate = null
                }
            }
        }
        
        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true) {
                dispatch_async(dispatch_get_main_queue()) {
                    onDismiss()
                    currentDelegate = null
                }
            }
        }
    }
    
    // Keep delegate alive
    currentDelegate = delegate
    picker.delegate = delegate
    
    // Get the root view controller and present modally
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootViewController?.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toImageBitmap(): ImageBitmap? {
    return try {
        // Fix orientation by redrawing the image
        val fixedImage = this.fixOrientation()
        
        val nsData = UIImagePNGRepresentation(fixedImage) ?: return null
        val bytes = ByteArray(nsData.length.toInt())
        kotlinx.cinterop.memScoped {
            nsData.getBytes(bytes.refTo(0).getPointer(this), nsData.length)
        }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        println("Error converting UIImage to ImageBitmap: $e")
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.fixOrientation(): UIImage {
    // If image is already in correct orientation, return as is
    if (imageOrientation == UIImageOrientation.UIImageOrientationUp) {
        return this
    }
    
    // Create a graphics context and redraw the image in the correct orientation
    UIGraphicsBeginImageContextWithOptions(size, false, scale)
    drawInRect(platform.CoreGraphics.CGRectMake(0.0, 0.0, size.useContents { width }, size.useContents { height }))
    val normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    
    return normalizedImage ?: this
}
