package id.tirtapatriot.activitycamera.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.camera.TextMeasure
import id.tirtapatriot.activitycamera.camera.WatermarkLayout
import id.tirtapatriot.activitycamera.camera.WatermarkPrefs
import id.tirtapatriot.activitycamera.camera.WatermarkSpec
import id.tirtapatriot.activitycamera.camera.WatermarkStyle
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.model.TaskType

private val SheetShape = RoundedCornerShape(32.dp)

fun sheetEnter(): EnterTransition = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(200))

fun sheetExit(): ExitTransition = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160))

/** Floating light panel, 10 dp from the screen edges, over the running camera. */
@Composable
private fun FloatingSheet(title: String, subtitle: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(10.dp)
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .lightGlass(SheetShape)
            // swallow taps so they don't reach the scrim
            .clickable(interactionSource = null, indication = null) {}
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                subtitle?.let { Text(it, fontSize = 13.sp, color = Palette.TextMuted) }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Palette.SurfaceMuted).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, LocalStrings.current.close, tint = Palette.Text, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(end = 8.dp), content = content)
    }
}

@Composable
fun TaskSheet(settings: AppSettings, onPick: (TaskType?) -> Unit, onOpenAccount: () -> Unit, onClose: () -> Unit) {
    val strings = LocalStrings.current
    val policy = settings.config.capture
    val tasks = settings.config.tasks
    FloatingSheet(
        title = strings.tasksTitle,
        subtitle = if (policy.requireTask) strings.tasksRequiredHint else strings.tasksOptionalHint,
        onClose = onClose,
    ) {
        if (tasks.isEmpty()) {
            Text(
                if (settings.isConnected) strings.tasksEmpty else strings.tasksNeedServer,
                color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
            if (!settings.isConnected) {
                TextButton(onClick = onOpenAccount) { Text(strings.connectToServer) }
            }
            return@FloatingSheet
        }
        tasks.forEach { task ->
            TaskRow(task.name, AppIcons.task(task.icon), selected = task.id == settings.selectedTaskId) { onPick(task) }
        }
        if (!policy.requireTask) {
            TaskRow(strings.noTask, AppIcons.Close, selected = settings.selectedTaskId == null, muted = true) { onPick(null) }
        }
    }
}

@Composable
private fun TaskRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(if (muted) Palette.SurfaceMuted else primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (muted) Palette.TextMuted else primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.size(14.dp))
        Text(name, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (muted) Palette.TextMuted else Palette.Text)
        if (selected) Icon(AppIcons.Check, null, tint = primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun WatermarkSheet(
    prefs: WatermarkPrefs,
    previewSpec: (WatermarkPrefs) -> WatermarkSpec,
    onChange: (WatermarkPrefs) -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    // Text fields edit locally and save as they change; toggles save immediately.
    var note by remember { mutableStateOf(prefs.note) }
    var extra by remember { mutableStateOf(prefs.extraText) }
    FloatingSheet(title = strings.watermark, subtitle = strings.watermarkHint, onClose = onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WatermarkStyle.entries.forEach { style ->
                StyleChoice(
                    label = strings.styleName(style),
                    selected = prefs.style == style,
                    spec = previewSpec(prefs.copy(style = style, anchorX = 0f, anchorY = 1f)),
                    modifier = Modifier.weight(1f),
                ) { onChange(prefs.copy(style = style)) }
            }
        }
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it.take(WatermarkPrefs.MAX_TEXT)
                onChange(prefs.copy(note = note))
            },
            label = { Text(strings.noteLabel) },
            placeholder = { Text(strings.notePlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = extra,
            onValueChange = {
                extra = it.take(WatermarkPrefs.MAX_TEXT)
                onChange(prefs.copy(extraText = extra))
            },
            label = { Text(strings.extraTextLabel) },
            placeholder = { Text(strings.extraTextPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(10.dp))
        Text(strings.showOnPhoto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextMuted)
        ToggleRow(strings.fieldOrganization, prefs.showOrganization) { onChange(prefs.copy(showOrganization = it)) }
        ToggleRow(strings.fieldTask, prefs.showTask) { onChange(prefs.copy(showTask = it)) }
        ToggleRow(strings.fieldCoordinates, prefs.showCoordinates) { onChange(prefs.copy(showCoordinates = it)) }
        ToggleRow(strings.fieldOfficer, prefs.showOfficer) { onChange(prefs.copy(showOfficer = it)) }
        ToggleRow(strings.fieldNote, prefs.showNote) { onChange(prefs.copy(showNote = it)) }
        TextButton(onClick = { onChange(prefs.copy(anchorX = 0f, anchorY = 1f)) }) { Text(strings.resetPosition) }
    }
}

@Composable
private fun StyleChoice(label: String, selected: Boolean, spec: WatermarkSpec, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(shape)
                .background(Color(0xFF55606E))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) primary else Palette.Outline, shape),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val measure = TextMeasure { text, sizePx, bold ->
                    textMeasurer.measure(
                        text,
                        androidx.compose.ui.text.TextStyle(
                            fontSize = (sizePx / (density * fontScale)).sp,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        ),
                        softWrap = false,
                        maxLines = 1,
                    ).size.width.toFloat()
                }
                drawWatermark(WatermarkLayout.layout(spec, size.width, size.height, measure), textMeasurer)
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) primary else Palette.Text)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp, color = Palette.Text)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
