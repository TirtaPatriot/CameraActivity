package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.i18n.Strings
import id.tirtapatriot.activitycamera.media.ShareFile
import id.tirtapatriot.activitycamera.media.ShareSheet
import id.tirtapatriot.activitycamera.media.ShareText
import id.tirtapatriot.activitycamera.media.mimeType
import id.tirtapatriot.activitycamera.media.rememberThumbnail
import id.tirtapatriot.activitycamera.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shares captures through the system sheet and records the share (reported to the server by sync). */
fun shareCaptures(records: List<CaptureRecord>, text: String) {
    if (records.isEmpty()) return
    val repository = AppGraph.repository
    ShareSheet.share(records.map { ShareFile(repository.filePath(it), it.mimeType()) }, text) {
        AppGraph.appScope.launch {
            repository.markShared(records.map { it.clientId }, currentEpochMs())
            AppGraph.requestSync()
        }
    }
}

/** After a capture: look at it, adjust the caption and share it to a supervisor or group. */
@Composable
fun ReviewScreen(settings: AppSettings, clientId: String, onBack: () -> Unit, onRetake: () -> Unit) {
    val strings = LocalStrings.current
    val items by AppGraph.repository.items.collectAsState()
    val record = items.firstOrNull { it.clientId == clientId }
    if (record == null) {
        LaunchedEffect(clientId) { onBack() }
        return
    }
    val organization = settings.config.branding.organizationName.takeIf { settings.isConnected }
    var note by remember(clientId) { mutableStateOf(record.meta.note.orEmpty()) }
    var text by remember(clientId) { mutableStateOf(ShareText.forCapture(record, organization, settings.officerLabel, strings)) }
    // Follow the note in the caption until the officer edits the caption by hand.
    var captionEdited by remember(clientId) { mutableStateOf(false) }
    LaunchedEffect(clientId, note) {
        delay(500) // save after typing pauses
        val before = AppGraph.repository.find(clientId)?.meta?.note.orEmpty()
        AppGraph.repository.updateNote(clientId, note)
        if (before != note.trim()) AppGraph.requestSync()
    }
    val isVideo = record.meta.mediaType == MediaType.VIDEO
    val bitmap by rememberThumbnail(AppGraph.repository.filePath(record), isVideo, 2048)

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            bitmap?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (isVideo) {
                Box(Modifier.align(Alignment.Center).size(64.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Play, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Row(Modifier.statusBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(AppIcons.Close, strings.close, onClick = onBack)
                if (record.meta.isMockLocation) {
                    Spacer(Modifier.size(8.dp))
                    Row(
                        Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Alert, null, tint = Color(0xFFFFB300), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.mockLocationRecorded, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SyncLine(record, settings, strings)
            OutlinedTextField(
                value = note,
                onValueChange = { value ->
                    note = value.take(NOTE_MAX)
                    if (!captionEdited) {
                        val withNote = record.copy(meta = record.meta.copy(note = note.trim().ifEmpty { null }))
                        text = ShareText.forCapture(withNote, organization, settings.officerLabel, strings)
                    }
                },
                label = { Text(strings.noteForCapture) },
                placeholder = { Text(strings.notePlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    captionEdited = true
                },
                label = { Text(strings.shareCaption) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { shareCaptures(listOf(record), text) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(AppIcons.Share, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(strings.share, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRetake) { Text(strings.retake) }
                TextButton(onClick = onBack) { Text(strings.keepOnly) }
            }
        }
    }
}

@Composable
private fun SyncLine(record: CaptureRecord, settings: AppSettings, strings: Strings) {
    val (icon, label, color) = syncState(record, settings, strings)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 13.sp, color = color)
    }
}

private const val NOTE_MAX = 200

/** Icon, label and color describing where a capture is: on the server, waiting, or phone only. */
fun syncState(record: CaptureRecord, settings: AppSettings, strings: Strings): Triple<ImageVector, String, Color> = when {
    record.status == SyncStatus.SYNCED -> Triple(AppIcons.CloudDone, strings.recordedOnServer, Palette.Success)
    !settings.isConnected -> Triple(AppIcons.CloudOff, strings.savedOnPhoneOnly, Palette.TextMuted)
    !settings.canUpload -> Triple(AppIcons.CloudOff, strings.waitingForSignIn, Palette.Warning)
    record.status == SyncStatus.FAILED -> Triple(AppIcons.CloudUp, strings.uploadRetrying, Palette.Warning)
    else -> Triple(AppIcons.CloudUp, strings.uploadingInBackground, Palette.TextMuted)
}
