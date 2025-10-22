package ch.slf.whiterisk.kmpsam

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun CameraView(onImageCaptured: (ImageBitmap?) -> Unit, onDismiss: () -> Unit)