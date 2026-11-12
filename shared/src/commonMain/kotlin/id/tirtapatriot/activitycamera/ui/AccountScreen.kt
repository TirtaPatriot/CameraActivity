package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.AppGraph
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.data.SyncStatus
import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.i18n.LocalStrings

/** Account, server and language. Signing in is encouraged here, never forced (unless the admin requires it). */
@Composable
fun AccountScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val strings = LocalStrings.current
    val primary = MaterialTheme.colorScheme.primary
    val captures by AppGraph.repository.items.collectAsState()
    val pending = captures.count { it.status != SyncStatus.SYNCED }
    val branding = settings.config.branding

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, strings.back, tint = Palette.Text) }
            Text(strings.account, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Who
            Card {
                val user = settings.user
                if (settings.isSignedIn && user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(initials(user.displayName), color = primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                            user.userCode?.let { Text(it, fontSize = 13.sp, color = Palette.TextMuted) }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = onSignOut) {
                        Icon(AppIcons.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(strings.signOut)
                    }
                } else {
                    Text(strings.notSignedIn, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    Text(
                        if (settings.sessionExpired) strings.sessionExpired else strings.notSignedInIntro,
                        fontSize = 13.sp, color = if (settings.sessionExpired) Palette.Warning else Palette.TextMuted,
                    )
                    Spacer(Modifier.size(10.dp))
                    Benefit(AppIcons.User, strings.benefitName)
                    Benefit(AppIcons.Task, strings.benefitTasks)
                    Benefit(AppIcons.CloudDone, strings.benefitRecorded)
                    Spacer(Modifier.size(12.dp))
                    Button(
                        onClick = if (settings.isConnected) onSignIn else onConnect,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(if (settings.isConnected) strings.signIn else strings.connectToServer, fontWeight = FontWeight.SemiBold) }
                }
            }

            // Server
            Card {
                SectionTitle(AppIcons.Server, strings.server)
                if (settings.isConnected) {
                    Text(branding.organizationName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    Text(settings.serverUrl, fontSize = 13.sp, color = Palette.TextMuted)
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val status = when {
                            pending == 0 -> strings.allSent
                            settings.canUpload -> strings.bannerPending(pending)
                            else -> strings.bannerSignInRequired(pending)
                        }
                        Text(status, fontSize = 13.sp, color = Palette.Text, modifier = Modifier.weight(1f))
                        if (pending > 0 && settings.canUpload) {
                            TextButton(onClick = { AppGraph.requestSync() }) { Text(strings.sendNow) }
                        }
                    }
                    Row {
                        TextButton(onClick = onConnect) { Text(strings.changeServer) }
                        TextButton(onClick = onDisconnect) { Text(strings.disconnect, color = Palette.Danger) }
                    }
                } else {
                    Text(strings.notConnectedIntro, fontSize = 13.sp, color = Palette.TextMuted)
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(AppIcons.Qr, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(strings.connectToServer)
                    }
                }
            }

            // Language
            Card {
                SectionTitle(AppIcons.Globe, strings.languageLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        val selected = language == settings.effectiveLanguage
                        Text(
                            language.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) primary else Palette.Text,
                            modifier = Modifier
                                .background(if (selected) primary.copy(alpha = 0.10f) else Palette.SurfaceMuted, RoundedCornerShape(50))
                                .border(1.dp, if (selected) primary else Palette.Outline, RoundedCornerShape(50))
                                .clickable { AppGraph.settingsStore.update { it.copy(language = language) } }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Text(strings.deviceId(settings.deviceId.take(8)), fontSize = 12.sp, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(22.dp))
            .border(1.dp, Palette.Outline, RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, null, tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextMuted)
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(30.dp).background(primary.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(text, fontSize = 14.sp, color = Palette.Text)
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
