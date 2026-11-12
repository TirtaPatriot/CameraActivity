package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Live camera view that calls [onScanned] once with the text of the first QR code it sees. */
@Composable
expect fun QrScannerView(modifier: Modifier, onScanned: (String) -> Unit)
