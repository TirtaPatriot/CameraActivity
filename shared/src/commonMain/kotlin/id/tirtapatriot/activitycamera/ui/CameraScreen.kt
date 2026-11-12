package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.camera.CameraController
import id.tirtapatriot.activitycamera.camera.CameraPreview
import id.tirtapatriot.activitycamera.camera.Stamp
import id.tirtapatriot.activitycamera.camera.StampFormatter
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.location.GeoFix
import id.tirtapatriot.activitycamera.location.LocationProvider
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.platform.PlatformInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

private const val MAX_NOTE_INPUT = 120

@Composable
fun CameraScreen(
    repository: CaptureRepository,
    settings: AppSettings,
    locationProvider: LocationProvider,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val fix by locationProvider.fix.collectAsState()

    var controller by remember { mutableStateOf<CameraController?>(null) }
    var mode by remember { mutableStateOf(MediaType.PHOTO) }
    var note by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(currentEpochMs()) }

    // Video currently being recorded: metadata is taken when recording starts.
    var pendingVideo by remember { mutableStateOf<CaptureRecord?>(null) }

    DisposableEffect(locationProvider) {
        locationProvider.start()
        onDispose { locationProvider.stop() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentEpochMs()
            delay(1_000)
        }
    }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2_500)
            toastMessage = null
        }
    }

    fun buildRecord(type: MediaType, takenAt: Long, geo: GeoFix?): CaptureRecord {
        val clientId = StampFormatter.newClientId()
        val metadata = CaptureMetadata(
            clientId = clientId,
            mediaType = type,
            takenAtEpochMs = takenAt,
            timezone = timeZone.id,
            latitude = geo?.latitude,
            longitude = geo?.longitude,
            accuracyMeters = geo?.accuracyMeters,
            altitudeMeters = geo?.altitude,
            note = note.trim().ifEmpty { null },
            userCode = settings.userCode.trim().ifEmpty { null },
            deviceModel = PlatformInfo.deviceModel,
            platform = PlatformInfo.name,
        )
        return CaptureRecord(
            meta = metadata,
            fileName = StampFormatter.fileName(type, takenAt, timeZone, clientId, PlatformInfo.videoExtension),
        )
    }

    fun onShutter() {
        val camera = controller ?: return
        if (isBusy) return
        scope.launch {
            isBusy = true
            try {
                when (mode) {
                    MediaType.PHOTO -> {
                        val takenAt = currentEpochMs()
                        val geo = fix
                        val record = buildRecord(MediaType.PHOTO, takenAt, geo)
                        val stamp = Stamp(
                            StampFormatter.lines(takenAt, timeZone, geo, note, settings.userCode, strings),
                            geo,
                        )
                        val saved = camera.takePhoto(repository.filePath(record.fileName), stamp)
                        if (saved) repository.add(record)
                        toastMessage = if (saved) strings.photoSaved else strings.photoFailed
                    }

                    MediaType.VIDEO -> {
                        val activeVideo = pendingVideo
                        if (activeVideo == null) {
                            val record = buildRecord(MediaType.VIDEO, currentEpochMs(), fix)
                            if (camera.startRecording(repository.filePath(record.fileName), settings.recordAudio)) {
                                pendingVideo = record
                            } else {
                                toastMessage = strings.recordingStartFailed
                            }
                        } else {
                            val saved = camera.stopRecording()
                            pendingVideo = null
                            if (saved) repository.add(activeVideo)
                            toastMessage = if (saved) strings.videoSaved else strings.videoFailed
                        }
                    }
                }
            } finally {
                isBusy = false
            }
        }
    }

    val isRecording = pendingVideo != null
    val stampLines = StampFormatter.lines(now, timeZone, fix, note, settings.userCode, strings)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(Modifier.fillMaxSize()) { controller = it }

        // Top: mode switch + activity note
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = mode == MediaType.PHOTO,
                    onClick = { if (!isRecording) mode = MediaType.PHOTO },
                    label = { Text(strings.modePhoto) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = mode == MediaType.VIDEO,
                    onClick = { if (!isRecording) mode = MediaType.VIDEO },
                    label = { Text(strings.modeVideo) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSettings, enabled = !isRecording) { Text(strings.settings) }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(MAX_NOTE_INPUT) },
                enabled = !isRecording,
                singleLine = true,
                placeholder = { Text(strings.notePlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                    disabledContainerColor = Color.Black.copy(alpha = 0.4f),
                ),
            )
        }

        // Stamp preview, identical to what gets burned into the photo
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 120.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            stampLines.forEach { Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }

        toastMessage?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }

        // Bottom: gallery + shutter
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenGallery, enabled = !isRecording) { Text(strings.gallery) }
            Spacer(Modifier.weight(1f))
            ShutterButton(
                mode = mode,
                isRecording = isRecording,
                enabled = controller != null && !isBusy,
                onClick = ::onShutter,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }
    }
}

@Composable
private fun ShutterButton(mode: MediaType, isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val innerColor = when {
        isRecording -> Color.Red
        mode == MediaType.VIDEO -> Color(0xFFE53935)
        else -> Color.White
    }
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(
                if (enabled) innerColor else innerColor.copy(alpha = 0.4f),
                if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    )
}

private fun currentEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
