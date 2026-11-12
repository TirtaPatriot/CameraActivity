package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.CaptureRecord
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.media.ShareText
import id.tirtapatriot.activitycamera.media.rememberThumbnail
import id.tirtapatriot.activitycamera.model.MediaType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private sealed interface HistoryItem {
    data class Day(val date: LocalDate) : HistoryItem
    data class Capture(val record: CaptureRecord) : HistoryItem
}

/** All captures on this phone, newest first, grouped per day; pick several to share at once. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(settings: AppSettings, onBack: () -> Unit, onOpen: (clientId: String) -> Unit, onOpenAccount: () -> Unit) {
    val strings = LocalStrings.current
    val captures by AppGraph.repository.items.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val zone = remember { TimeZone.currentSystemDefault() }
    val pending = captures.count { it.status != SyncStatus.SYNCED }

    val rows = remember(captures) {
        buildList {
            var lastDay: LocalDate? = null
            captures.forEach { record ->
                val day = Instant.fromEpochMilliseconds(record.meta.takenAtEpochMs).toLocalDateTime(zone).date
                if (day != lastDay) {
                    add(HistoryItem.Day(day))
                    lastDay = day
                }
                add(HistoryItem.Capture(record))
            }
        }
    }

    fun toggle(clientId: String) {
        selected = if (clientId in selected) selected - clientId else selected + clientId
        if (selected.isEmpty()) selecting = false
    }

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, strings.back, tint = Palette.Text) }
            Text(
                if (selecting) strings.selectedCount(selected.size) else strings.historyTitle,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.weight(1f),
            )
            if (captures.isNotEmpty()) {
                TextButton(onClick = {
                    selecting = !selecting
                    selected = emptySet()
                }) { Text(if (selecting) strings.cancel else strings.select) }
            }
        }

        SyncBanner(settings, pending, onOpenAccount)

        if (captures.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(AppIcons.Images, null, tint = Palette.TextMuted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.size(12.dp))
                Text(strings.historyEmpty, color = Palette.TextMuted)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                rows,
                key = { row -> if (row is HistoryItem.Capture) row.record.clientId else "day-${(row as HistoryItem.Day).date}" },
                span = { row -> GridItemSpan(if (row is HistoryItem.Day) maxLineSpan else 1) },
            ) { row ->
                when (row) {
                    is HistoryItem.Day -> Text(
                        strings.dayLabel(row.date),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                    )

                    is HistoryItem.Capture -> CaptureTile(
                        record = row.record,
                        selecting = selecting,
                        isSelected = row.record.clientId in selected,
                        onClick = { if (selecting) toggle(row.record.clientId) else onOpen(row.record.clientId) },
                        onLongClick = {
                            selecting = true
                            toggle(row.record.clientId)
                        },
                    )
                }
            }
        }
    }

    if (selecting && selected.isNotEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Button(
                onClick = {
                    val records = captures.filter { it.clientId in selected }
                    val organization = settings.config.branding.organizationName.takeIf { settings.isConnected }
                    shareCaptures(records, ShareText.forCaptures(records, organization, settings.officerLabel, strings))
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(54.dp),
            ) {
                Icon(AppIcons.Share, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(strings.shareCount(selected.size), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SyncBanner(settings: AppSettings, pending: Int, onOpenAccount: () -> Unit) {
    val strings = LocalStrings.current
    val (message, action) = when {
        !settings.isConnected -> strings.bannerNotConnected to strings.connectToServer
        !settings.canUpload -> strings.bannerSignInRequired(pending) to strings.signIn
        !settings.isSignedIn -> strings.bannerAnonymous to strings.signIn
        pending > 0 -> strings.bannerPending(pending) to strings.sendNow
        else -> return
    }
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, Palette.Outline, RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (settings.canUpload) AppIcons.CloudUp else AppIcons.CloudOff, null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(message, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (pending > 0 && settings.canUpload && settings.isSignedIn) AppGraph.requestSync() else onOpenAccount() }) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaptureTile(record: CaptureRecord, selecting: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isVideo = record.meta.mediaType == MediaType.VIDEO
    val bitmap by rememberThumbnail(AppGraph.repository.filePath(record), isVideo, 320)
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Palette.SurfaceMuted)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (isSelected) Box(Modifier.fillMaxSize().border(3.dp, primary, shape).background(primary.copy(alpha = 0.18f)))
        Row(Modifier.align(Alignment.BottomStart).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (isVideo) TileBadge(AppIcons.Video)
            if (record.sharedAtEpochMs != null) TileBadge(AppIcons.Share)
            if (record.meta.isMockLocation) TileBadge(AppIcons.Alert, Color(0xFFFFB300))
        }
        TileBadge(
            when (record.status) {
                SyncStatus.SYNCED -> AppIcons.CloudDone
                SyncStatus.FAILED -> AppIcons.CloudOff
                else -> AppIcons.CloudUp
            },
            if (record.status == SyncStatus.SYNCED) Color(0xFF9BE7A0) else Color.White,
            Modifier.align(Alignment.BottomEnd).padding(5.dp),
        )
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (isSelected) primary else Color.Black.copy(alpha = 0.25f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (isSelected) Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun TileBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
    }
}
