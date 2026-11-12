package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.camera.QrScannerView
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.model.AppConfig
import id.tirtapatriot.activitycamera.sync.ServerApi
import kotlinx.coroutines.launch

/** First screen: link the app to an organization's server by typing its address or scanning the panel's QR. */
@Composable
fun ConnectScreen(
    api: ServerApi,
    cameraPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onConnected: (serverUrl: String, config: AppConfig) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect(url: String) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            error = null
            try {
                val config = api.fetchConfig(url)
                onConnected(url, config)
            } catch (_: Throwable) {
                error = strings.connectFailed
            } finally {
                isBusy = false
            }
        }
    }

    if (isScanning) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            QrScannerView(Modifier.fillMaxSize()) { content ->
                isScanning = false
                val url = ServerApi.serverUrlFromQr(content)
                if (url == null) {
                    error = strings.invalidQr
                } else {
                    address = url
                    connect(url)
                }
            }
            Button(
                onClick = { isScanning = false },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
            ) { Text(strings.cancel) }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(strings.connectTitle, style = MaterialTheme.typography.headlineSmall)
        Text(strings.connectIntro, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { if (cameraPermissionGranted) isScanning = true else onRequestPermissions() },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(strings.scanQr) }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim() },
            label = { Text(strings.serverUrlLabel) },
            placeholder = { Text("https://camera.example.org") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val url = ServerApi.normalizeUrl(address)
                if (url == null) error = strings.connectFailed else connect(url)
            },
            enabled = !isBusy && address.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isBusy) strings.connecting else strings.connect) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
