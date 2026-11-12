package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.sync.ServerApi

/**
 * Phone-side settings: only the language and the session. Everything else
 * (branding, capture rules, accounts) is managed in the server's admin panel.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    api: ServerApi,
    pendingCount: Int,
    onLanguageChange: (AppLanguage) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val branding = settings.config.branding
    val user = settings.user

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(strings.backToCamera) }
            Spacer(Modifier.weight(1f))
            Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(settings.serverUrl, branding.logoVersion, api, Modifier.height(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(branding.organizationName, style = MaterialTheme.typography.titleSmall)
                Text(branding.appTitle.forLanguage(strings.language.code), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()

        Text(strings.server, style = MaterialTheme.typography.labelLarge)
        Text(settings.serverUrl, style = MaterialTheme.typography.bodyMedium)

        Text(strings.account, style = MaterialTheme.typography.labelLarge)
        if (user != null) {
            Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(user.username, user.userCode).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
        Row {
            AppLanguage.entries.forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = settings.effectiveLanguage == option,
                    onClick = { onLanguageChange(option) },
                    label = { Text(option.displayName) },
                )
            }
        }

        HorizontalDivider()
        if (pendingCount > 0) {
            Text(strings.pendingWarning(pendingCount), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(strings.signOut)
        }
    }
}
