package id.tirtapatriot.activitycamera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.camera.CameraController
import id.tirtapatriot.activitycamera.camera.CameraPreview
import id.tirtapatriot.activitycamera.camera.FlashMode
import id.tirtapatriot.activitycamera.camera.RawPhoto
import id.tirtapatriot.activitycamera.camera.WatermarkFormatter
import id.tirtapatriot.activitycamera.camera.WatermarkPrefs
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.location.GeoFix
import id.tirtapatriot.activitycamera.location.LocationProvider
import id.tirtapatriot.activitycamera.media.rememberThumbnail
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.platform.PlatformInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.math.roundToLong

private enum class CameraSheet { NONE, TASKS, WATERMARK, NOTE }

private val TimerOptions = listOf(0, 3, 10)
private const val MAX_CUSTOM_TASKS = 20

/** The first screen: full camera with floating glass controls. Works without a server or an account. */
@Composable
fun CameraScreen(
    settings: AppSettings,
    locationProvider: LocationProvider,
    onOpenReview: (clientId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val store = AppGraph.settingsStore
    val repository = AppGraph.repository
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val fix by locationProvider.fix.collectAsState()
    val captures by repository.items.collectAsState()

    val policy = settings.config.capture
    val branding = settings.config.branding
    val tasks = settings.config.tasks
    val task = settings.selectedTask
    val taskName = settings.pickedTaskName
    val accent = brandColor(branding.primaryColor)
    val allowedModes = buildList {
        if (policy.allowVideo) add(MediaType.VIDEO)
        if (policy.allowPhoto) add(MediaType.PHOTO)
    }.ifEmpty { listOf(MediaType.PHOTO) }

    var controller by remember { mutableStateOf<CameraController?>(null) }
    var mode by remember { mutableStateOf(if (MediaType.PHOTO in allowedModes) MediaType.PHOTO else allowedModes.first()) }
    var flash by remember { mutableStateOf(FlashMode.OFF) }
    var showGrid by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var isCapturing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(CameraSheet.NONE) }
    var toast by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(currentEpochMs()) }
    var justCaptured by remember { mutableStateOf<String?>(null) }
    // Drag position while moving the watermark; saved when the finger lifts.
    var dragAnchor by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingVideo by remember { mutableStateOf<CaptureRecord?>(null) }
    val shutterFlash = remember { Animatable(0f) }

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
    LaunchedEffect(allowedModes) {
        if (mode !in allowedModes && pendingVideo == null) mode = allowedModes.last()
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_500)
            toast = null
        }
    }
    LaunchedEffect(justCaptured) {
        if (justCaptured != null) {
            delay(5_000)
            justCaptured = null
        }
    }

    val prefs = settings.watermark.let { saved ->
        dragAnchor?.let { (x, y) -> saved.copy(anchorX = x, anchorY = y) } ?: saved
    }

    fun buildSpec(prefs: WatermarkPrefs, epochMs: Long, geo: GeoFix?) = WatermarkFormatter.build(
        prefs = prefs,
        epochMs = epochMs,
        timeZone = timeZone,
        fix = geo,
        organization = branding.organizationName.takeIf { settings.isConnected },
        taskName = taskName,
        officer = settings.officerLabel,
        accentArgb = accent.toArgb().toLong() and 0xFFFFFFFFL,
        strings = strings,
    )

    fun buildRecord(type: MediaType, takenAt: Long, geo: GeoFix?): CaptureRecord {
        val clientId = WatermarkFormatter.newClientId()
        val metadata = CaptureMetadata(
            clientId = clientId,
            mediaType = type,
            takenAtEpochMs = takenAt,
            timezone = timeZone.id,
            latitude = geo?.latitude,
            longitude = geo?.longitude,
            accuracyMeters = geo?.accuracyMeters,
            altitudeMeters = geo?.altitude,
            isMockLocation = geo?.isMock == true,
            note = settings.watermark.note.trim().ifEmpty { null },
            taskId = task?.id,
            taskName = taskName,
            userCode = settings.user?.userCode,
            deviceId = settings.deviceId,
            deviceModel = PlatformInfo.deviceModel,
            platform = PlatformInfo.name,
        )
        return CaptureRecord(
            meta = metadata,
            fileName = WatermarkFormatter.fileName(type, takenAt, timeZone, clientId, PlatformInfo.videoExtension),
        )
    }

    /** Why a new capture may not start yet, per the server's rules; null when allowed. */
    fun blockReason(): String? = when {
        policy.requireTask && taskName == null -> {
            sheet = CameraSheet.TASKS
            strings.taskRequired
        }
        policy.requireLocation && fix == null -> strings.locationRequired
        else -> null
    }

    /** Saving runs in the app scope so it finishes even if the officer leaves the camera right away. */
    fun savePhoto(raw: RawPhoto, record: CaptureRecord, geo: GeoFix?, takenAt: Long) {
        val spec = buildSpec(settings.watermark, takenAt, geo)
        AppGraph.appScope.launch {
            val saved = raw.saveWithWatermark(repository.filePath(record), spec, geo)
            if (saved) {
                repository.add(record)
                justCaptured = record.clientId
                AppGraph.requestSync()
            } else {
                toast = strings.photoFailed
            }
        }
    }

    fun takeNow() {
        val camera = controller ?: return
        scope.launch {
            when (mode) {
                MediaType.PHOTO -> {
                    isCapturing = true
                    val takenAt = currentEpochMs()
                    val geo = fix
                    val raw = try {
                        camera.capture(flash)
                    } finally {
                        isCapturing = false
                    }
                    if (raw == null) {
                        toast = strings.photoFailed
                        return@launch
                    }
                    launch {
                        shutterFlash.snapTo(0.7f)
                        shutterFlash.animateTo(0f, tween(220))
                    }
                    savePhoto(raw, buildRecord(MediaType.PHOTO, takenAt, geo), geo, takenAt)
                }

                MediaType.VIDEO -> {
                    val active = pendingVideo
                    if (active == null) {
                        val record = buildRecord(MediaType.VIDEO, currentEpochMs(), fix)
                        if (camera.startRecording(repository.filePath(record), policy.recordAudio, flash)) {
                            pendingVideo = record
                        } else {
                            toast = strings.recordingStartFailed
                        }
                    } else {
                        isCapturing = true
                        val saved = try {
                            camera.stopRecording()
                        } finally {
                            isCapturing = false
                        }
                        pendingVideo = null
                        if (saved) {
                            repository.add(active)
                            justCaptured = active.clientId
                            AppGraph.requestSync()
                        } else {
                            toast = strings.videoFailed
                        }
                    }
                }
            }
        }
    }

    fun onShutter() {
        if (controller == null || isCapturing || countdown != null) return
        val isStarting = mode == MediaType.PHOTO || pendingVideo == null
        if (isStarting) {
            blockReason()?.let {
                toast = it
                return
            }
        }
        if (isStarting && timerSeconds > 0) {
            scope.launch {
                for (second in timerSeconds downTo 1) {
                    countdown = second
                    delay(1_000)
                }
                countdown = null
                takeNow()
            }
        } else {
            takeNow()
        }
    }

    // Stop automatically at the maximum video length set on the server.
    LaunchedEffect(pendingVideo) {
        if (pendingVideo == null) return@LaunchedEffect
        delay(policy.maxVideoSeconds * 1_000L)
        if (pendingVideo != null) takeNow()
    }

    val isRecording = pendingVideo != null
    val recordingSeconds = pendingVideo?.let { ((now - it.meta.takenAtEpochMs) / 1_000).coerceAtLeast(0) }
    val latest = captures.firstOrNull()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))
            // 3:4 viewfinder = exactly the photo frame, so the watermark lands where it is shown.
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp))) {
                CameraPreview(Modifier.fillMaxSize()) { controller = it }
                if (showGrid) GridLines(Modifier.fillMaxSize())
                if (mode == MediaType.PHOTO) {
                    WatermarkOverlay(
                        spec = buildSpec(prefs, now, fix),
                        modifier = Modifier.fillMaxSize(),
                        onMove = { x, y -> dragAnchor = x to y },
                        onMoveEnd = {
                            dragAnchor?.let { (x, y) ->
                                store.update { it.copy(watermark = it.watermark.copy(anchorX = x, anchorY = y)) }
                            }
                            dragAnchor = null
                        },
                    )
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.White))
                countdown?.let {
                    Text("$it", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // Top bar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                GlassIconButton(AppIcons.Settings, strings.settings, onClick = onOpenAccount, enabled = !isRecording)
                if (settings.isConnected && !settings.isSignedIn) {
                    Box(Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFFFFB300), CircleShape).border(1.5.dp, Color.Black, CircleShape))
                }
            }
            if (recordingSeconds != null) {
                Row(
                    Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Palette.Record, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${formatDuration(recordingSeconds)} / ${formatDuration(policy.maxVideoSeconds.toLong())}",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                GpsPill(fix)
            }
            Spacer(Modifier.weight(1f))
            GlassIconButton(
                when (flash) {
                    FlashMode.OFF -> AppIcons.FlashOff
                    FlashMode.ON -> AppIcons.Flash
                    FlashMode.AUTO -> AppIcons.FlashAuto
                },
                strings.flash,
                active = flash == FlashMode.ON,
                onClick = { flash = FlashMode.entries[(flash.ordinal + 1) % FlashMode.entries.size] },
            )
            GlassIconButton(AppIcons.Grid, strings.grid, active = showGrid, onClick = { showGrid = !showGrid })
            GlassIconButton(
                AppIcons.Timer, strings.timer,
                badge = timerSeconds.takeIf { it > 0 }?.let { "$it" },
                enabled = !isRecording,
                onClick = { timerSeconds = TimerOptions[(TimerOptions.indexOf(timerSeconds) + 1) % TimerOptions.size] },
            )
        }

        toast?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).glass(RoundedCornerShape(14.dp), strength = 1.6f).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Bottom controls
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    TaskPill(
                        taskName = taskName,
                        icon = task?.let { AppIcons.task(it.icon) } ?: if (taskName != null) AppIcons.Other else AppIcons.Task,
                        hint = if (policy.requireTask) strings.pickTaskRequired else strings.pickTask,
                        enabled = !isRecording,
                        onClick = { sheet = CameraSheet.TASKS },
                    )
                }
                ZoomPill(zoom) { ratio ->
                    zoom = ratio
                    controller?.setZoom(ratio)
                }
                GlassIconButton(
                    AppIcons.Note, strings.noteButton,
                    active = settings.watermark.note.isNotBlank(),
                    enabled = !isRecording,
                    onClick = { sheet = CameraSheet.NOTE },
                )
                if (mode == MediaType.PHOTO) {
                    GlassIconButton(AppIcons.Stamp, strings.watermark, enabled = !isRecording, onClick = { sheet = CameraSheet.WATERMARK })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (allowedModes.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    allowedModes.forEach { option ->
                        val selected = option == mode
                        Text(
                            if (option == MediaType.PHOTO) strings.modePhoto.uppercase() else strings.modeVideo.uppercase(),
                            color = if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.clickable(enabled = !isRecording) { mode = option }.padding(6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    LatestThumb(latest, enabled = !isRecording, onClick = onOpenHistory)
                    // Fully qualified: inside Row → Box, the RowScope overload would be picked and is not allowed here.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = justCaptured != null && !isRecording,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.offset(y = (-62).dp),
                    ) {
                        Row(
                            Modifier
                                .glass(RoundedCornerShape(50), strength = 1.4f)
                                .clickable { justCaptured?.let(onOpenReview); justCaptured = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.share, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                ShutterButton(
                    mode = mode,
                    isRecording = isRecording,
                    enabled = controller != null && !isCapturing,
                    onClick = ::onShutter,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    GlassIconButton(AppIcons.Flip, strings.flipCamera, size = 52, enabled = !isRecording) {
                        controller?.switchCamera()
                        zoom = 1f
                    }
                }
            }
        }

        // Sheets float over the camera, which keeps running underneath.
        if (sheet != CameraSheet.NONE) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable(interactionSource = null, indication = null) { sheet = CameraSheet.NONE })
        }
        AnimatedVisibility(
            visible = sheet == CameraSheet.TASKS,
            enter = sheetEnter(), exit = sheetExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TaskSheet(
                settings = settings,
                onPick = { picked ->
                    store.update { it.copy(selectedTaskId = picked?.id, selectedCustomTask = null) }
                    sheet = CameraSheet.NONE
                },
                onPickCustom = { name ->
                    store.update { it.copy(selectedTaskId = null, selectedCustomTask = name) }
                    sheet = CameraSheet.NONE
                },
                onAddCustom = { name ->
                    store.update {
                        val list = (it.customTasks.filterNot { t -> t.equals(name, ignoreCase = true) } + name).takeLast(MAX_CUSTOM_TASKS)
                        it.copy(customTasks = list, selectedTaskId = null, selectedCustomTask = name)
                    }
                    sheet = CameraSheet.NONE
                },
                onRemoveCustom = { name ->
                    store.update {
                        it.copy(
                            customTasks = it.customTasks - name,
                            selectedCustomTask = it.selectedCustomTask.takeIf { selected -> selected != name },
                        )
                    }
                },
                onOpenAccount = {
                    sheet = CameraSheet.NONE
                    onOpenAccount()
                },
                onClose = { sheet = CameraSheet.NONE },
            )
        }
        AnimatedVisibility(
            visible = sheet == CameraSheet.WATERMARK,
            enter = sheetEnter(), exit = sheetExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            WatermarkSheet(
                prefs = settings.watermark,
                previewSpec = { buildSpec(it, now, fix) },
                onChange = { changed -> store.update { it.copy(watermark = changed) } },
                onClose = { sheet = CameraSheet.NONE },
            )
        }
        AnimatedVisibility(
            visible = sheet == CameraSheet.NOTE,
            enter = sheetEnter(), exit = sheetExit(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NoteSheet(
                note = settings.watermark.note,
                onChange = { text -> store.update { it.copy(watermark = it.watermark.copy(note = text)) } },
                onClose = { sheet = CameraSheet.NONE },
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Int = 40,
    active: Boolean = false,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .glass(CircleShape, strength = if (active) 2f else 1f)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (active) Color(0xFFFFD54F) else Color.White, modifier = Modifier.size((size * 0.48f).dp))
        badge?.let {
            Text(
                it,
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                    .background(Color(0xFFFFD54F), CircleShape).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GpsPill(fix: GeoFix?) {
    val strings = LocalStrings.current
    val (label, color) = when {
        fix == null -> strings.gpsSearching to Color.White.copy(alpha = 0.85f)
        fix.isMock -> strings.mockLocation to Color(0xFFFFB300)
        else -> (fix.accuracyMeters?.let { "±${it.roundToLong()} m" } ?: "GPS") to Color.White
    }
    Row(
        Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (fix?.isMock == true) AppIcons.Alert else AppIcons.Pin, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TaskPill(taskName: String?, icon: ImageVector, hint: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .glass(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            taskName ?: hint,
            color = if (taskName != null) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (taskName != null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ZoomPill(zoom: Float, onZoom: (Float) -> Unit) {
    Row(Modifier.glass(RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(1f, 2f).forEach { ratio ->
            val selected = zoom == ratio
            Box(
                Modifier
                    .size(34.dp)
                    .background(if (selected) Color.Black.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                    .clickable { onZoom(ratio) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${ratio.toInt()}×",
                    color = if (selected) Color(0xFFFFD54F) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LatestThumb(latest: CaptureRecord?, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(52.dp)
            .glass(shape)
            .border(2.dp, Color.White.copy(alpha = 0.85f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (latest == null) {
            Icon(AppIcons.Images, null, tint = Color.White, modifier = Modifier.size(22.dp))
        } else {
            val path = AppGraph.repository.filePath(latest)
            val bitmap by rememberThumbnail(path, latest.meta.mediaType == MediaType.VIDEO, 160)
            bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(shape)) }
                ?: Icon(if (latest.meta.mediaType == MediaType.VIDEO) AppIcons.Video else AppIcons.Images, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ShutterButton(mode: MediaType, isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val inner = when {
        isRecording -> Palette.Record
        mode == MediaType.VIDEO -> Palette.Record
        else -> Color.White
    }
    Box(
        Modifier
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(if (isRecording) 22.dp else 7.dp)
            .background(if (enabled) inner else inner.copy(alpha = 0.5f), if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun GridLines(modifier: Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = 0.3f)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        }
    }
}

internal fun currentEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

internal fun formatDuration(totalSeconds: Long): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
