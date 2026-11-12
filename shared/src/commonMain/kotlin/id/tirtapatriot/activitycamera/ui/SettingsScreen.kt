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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.i18n.AppLanguage
import id.tirtapatriot.activitycamera.i18n.LocalStrings

@Composable
fun SettingsScreen(initial: AppSettings, onSave: (AppSettings) -> Unit, onBack: () -> Unit) {
    val strings = LocalStrings.current
    var serverUrl by remember { mutableStateOf(initial.serverUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var userCode by remember { mutableStateOf(initial.userCode) }
    var recordAudio by remember { mutableStateOf(initial.recordAudio) }
    var language by remember { mutableStateOf(initial.language) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(strings.backToCamera) }
            Spacer(Modifier.weight(1f))
            Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it.trim() },
            label = { Text(strings.serverUrlLabel) },
            placeholder = { Text("http://192.168.1.10:8080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim() },
            label = { Text(strings.apiKeyLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = userCode,
            onValueChange = { userCode = it },
            label = { Text(strings.officerCodeLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.recordAudioLabel, Modifier.weight(1f))
            Switch(checked = recordAudio, onCheckedChange = { recordAudio = it })
        }
        Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
        Row {
            AppLanguage.entries.forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = language == option,
                    onClick = { language = option },
                    label = { Text(option.displayName) },
                )
            }
        }
        Button(
            onClick = {
                onSave(
                    AppSettings(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        userCode = userCode.trim(),
                        recordAudio = recordAudio,
                        language = language,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(strings.save) }
    }
}
