package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.camera.StampFormatter
import id.tirtapatriot.activitycamera.camera.toFixed
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.CaptureRepository
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.sync.SyncClient
import id.tirtapatriot.activitycamera.sync.SyncSkipReason
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

@Composable
fun GalleryScreen(
    repository: CaptureRepository,
    syncClient: SyncClient,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val records by repository.items.collectAsState()
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val pendingCount = records.count { it.status != SyncStatus.SYNCED }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(strings.backToCamera) }
            Spacer(Modifier.weight(1f))
            Text(strings.gallerySummary(records.size, pendingCount), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                scope.launch {
                    isSyncing = true
                    val result = syncClient.syncAll()
                    isSyncing = false
                    resultMessage = when (result.skipped) {
                        SyncSkipReason.ALREADY_RUNNING -> strings.syncAlreadyRunning
                        SyncSkipReason.NOT_SIGNED_IN, SyncSkipReason.SESSION_EXPIRED -> {
                            // The app returns to the sign-in screen; show why there.
                            onSessionExpired()
                            strings.sessionExpired
                        }
                        null -> strings.syncResult(result.uploaded, result.failed)
                    }
                }
            },
            enabled = !isSyncing && pendingCount > 0,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(if (isSyncing) strings.syncing else strings.syncButton(pendingCount))
        }
        resultMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.padding(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records, key = { it.meta.clientId }) { CaptureRow(it) }
        }
    }
}

@Composable
private fun CaptureRow(record: CaptureRecord) {
    val strings = LocalStrings.current
    val metadata = record.meta
    val timeZone = remember(metadata.timezone) {
        runCatching { TimeZone.of(metadata.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (metadata.mediaType == MediaType.PHOTO) "📷" else "🎥")
                Spacer(Modifier.width(8.dp))
                Text(
                    StampFormatter.dateTime(metadata.takenAtEpochMs, timeZone, strings),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                StatusLabel(record.status)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            val latitude = metadata.latitude
            val longitude = metadata.longitude
            Text(
                if (latitude != null && longitude != null) {
                    "${latitude.toFixed(6)}, ${longitude.toFixed(6)}"
                } else {
                    strings.noLocation
                },
                style = MaterialTheme.typography.bodySmall,
            )
            metadata.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(record.fileName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            record.lastError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350)) }
        }
    }
}

@Composable
private fun StatusLabel(status: SyncStatus) {
    val strings = LocalStrings.current
    val (label, color) = when (status) {
        SyncStatus.PENDING -> strings.statusPending to Color(0xFFFFB74D)
        SyncStatus.SYNCING -> strings.statusSyncing to Color(0xFF64B5F6)
        SyncStatus.SYNCED -> strings.statusSynced to Color(0xFF81C784)
        SyncStatus.FAILED -> strings.statusFailed to Color(0xFFEF5350)
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium)
}
