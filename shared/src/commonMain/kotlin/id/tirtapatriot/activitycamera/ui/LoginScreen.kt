package id.tirtapatriot.activitycamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import id.tirtapatriot.activitycamera.auth.SingleSignOn
import id.tirtapatriot.activitycamera.auth.SsoResult
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.tirtapatriot.activitycamera.data.AppSettings
import id.tirtapatriot.activitycamera.i18n.LocalStrings
import id.tirtapatriot.activitycamera.model.UserProfile
import id.tirtapatriot.activitycamera.platform.PlatformInfo
import id.tirtapatriot.activitycamera.sync.ServerApi
import kotlinx.coroutines.launch

/** Officer sign-in, branded with the organization's logo and title. */
@Composable
fun LoginScreen(
    settings: AppSettings,
    api: ServerApi,
    notice: String?,
    onSignedIn: (token: String, user: UserProfile) -> Unit,
    onChangeServer: () -> Unit,
    onSkip: () -> Unit,
) {
    val strings = LocalStrings.current
    val branding = settings.config.branding
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn() {
        if (isBusy || username.isBlank() || password.isEmpty()) return
        scope.launch {
            isBusy = true
            error = null
            try {
                val response = api.login(settings.serverUrl, username, password, PlatformInfo.deviceModel)
                if (response == null) error = strings.signInFailed else onSignedIn(response.token, response.user)
            } catch (_: Throwable) {
                error = strings.connectFailed
            } finally {
                isBusy = false
            }
        }
    }

    fun signInSso() {
        if (isBusy) return
        scope.launch {
            isBusy = true
            error = null
            try {
                when (val result = SingleSignOn.signIn(api, settings.serverUrl)) {
                    is SsoResult.Success -> onSignedIn(result.response.token, result.response.user)
                    SsoResult.Rejected -> error = strings.ssoRejected
                    SsoResult.Cancelled -> Unit
                }
            } catch (_: Throwable) {
                error = strings.connectFailed
            } finally {
                isBusy = false
            }
        }
    }

    val auth = settings.config.auth
    val anonymousAllowed = settings.config.capture.allowAnonymousUpload && !settings.config.capture.requireSignIn
    Column(
        Modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandLogo(settings.serverUrl, branding.logoVersion, api, Modifier.height(72.dp))
        Text(branding.organizationName, style = MaterialTheme.typography.titleMedium)
        Text(
            branding.appTitle.forLanguage(strings.language.code),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (auth.oidcEnabled) {
            Button(
                onClick = ::signInSso,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(auth.oidcLabel ?: strings.signInSso, fontWeight = FontWeight.SemiBold) }
            if (auth.passwordEnabled) Text(strings.orWithPassword, color = Palette.TextMuted, fontSize = 13.sp)
        }
        if (auth.passwordEnabled) PasswordForm(
            username = username,
            password = password,
            isBusy = isBusy,
            primary = !auth.oidcEnabled,
            onUsername = { username = it.trim() },
            onPassword = { password = it },
            onSubmit = ::signIn,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onSkip, enabled = !isBusy) {
            Text(if (anonymousAllowed) strings.skipUseWithoutAccount else strings.laterToCamera, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onChangeServer, enabled = !isBusy) {
            Text("${strings.changeServer} · ${settings.serverUrl}", color = Palette.TextMuted)
        }
    }
}

@Composable
private fun PasswordForm(
    username: String,
    password: String,
    isBusy: Boolean,
    primary: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsername,
            label = { Text(strings.username) },
            singleLine = true,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text(strings.password) },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        val enabled = !isBusy && username.isNotBlank() && password.isNotEmpty()
        val label = if (isBusy) strings.signingIn else strings.signIn
        if (primary) {
            Button(onClick = onSubmit, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        } else {
            OutlinedButton(onClick = onSubmit, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
