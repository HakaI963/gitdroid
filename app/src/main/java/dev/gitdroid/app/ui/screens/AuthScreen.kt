package dev.gitdroid.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.auth.AuthManager
import dev.gitdroid.app.core.auth.DeviceFlowClient
import dev.gitdroid.app.ui.components.CodeWell
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.PageBackground
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalMotion
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The sign-in screen.
 *
 * Two paths, both fully in-app:
 *
 * * **Device flow** — the user types a short code on any device. No browser
 *   redirect has to come back into the app, which is what makes it work
 *   reliably on Android.
 * * **Personal access token** — paste a token and go. Useful for CI-style
 *   accounts and for people who already have one.
 */
@Composable
fun AuthScreen(
    authManager: AuthManager,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var mode by remember { mutableStateOf(AuthMode.Device) }
    var pat by remember { mutableStateOf("") }
    var patVisible by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<DeviceFlowClient.DeviceCode?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Give the device-flow code a head start: the user should see the code
        // as soon as they land on this screen.
        runCatching { authManager.beginDeviceFlow() }
            .onSuccess { deviceCode = it }
            .onFailure { error = it.message }
    }

    PageBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(space.xxxl * 2))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "GD",
                    style = type.headline,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(space.lg))
            Text(
                text = "GitDroid",
                style = type.display,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.xs))
            Text(
                text = "Your GitHub, in your pocket — and on the wire for your agents.",
                style = type.body,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = space.md),
            )

            Spacer(Modifier.height(space.xxl))

            // ── Mode switch ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AuthTab(
                    label = "Device code",
                    selected = mode == AuthMode.Device,
                    modifier = Modifier.weight(1f),
                    onClick = { mode = AuthMode.Device },
                )
                AuthTab(
                    label = "Access token",
                    selected = mode == AuthMode.Pat,
                    modifier = Modifier.weight(1f),
                    onClick = { mode = AuthMode.Pat },
                )
            }

            Spacer(Modifier.height(space.lg))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(motion.quick()) + slideInVertically(motion.quick()) { it / 8 }) togetherWith
                        (fadeOut(motion.quick()) + slideOutVertically(motion.quick()) { -it / 8 })
                },
                label = "auth-mode",
            ) { current ->
                when (current) {
                    AuthMode.Device -> DeviceCodePanel(
                        code = deviceCode,
                        waiting = waiting,
                        busy = busy,
                        error = error,
                        onCopy = { deviceCode?.userCode?.let { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", text))) } } },
                        onRefresh = {
                            error = null
                            scope.launch {
                                busy = true
                                runCatching { authManager.beginDeviceFlow() }
                                    .onSuccess { deviceCode = it }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                        onStart = {
                            val current = deviceCode ?: return@DeviceCodePanel
                            scope.launch {
                                waiting = true
                                error = null
                                authManager.completeDeviceFlow(current)
                                    .onSuccess { onSignedIn() }
                                    .onFailure { error = it.message }
                                waiting = false
                            }
                        },
                    )

                    AuthMode.Pat -> TokenPanel(
                        token = pat,
                        visible = patVisible,
                        busy = busy,
                        error = error,
                        onTokenChange = {
                            pat = it
                            error = null
                        },
                        onToggleVisibility = { patVisible = !patVisible },
                        onSubmit = {
                            scope.launch {
                                busy = true
                                error = null
                                authManager.signInWithToken(pat)
                                    .onSuccess { onSignedIn() }
                                    .onFailure { error = it.message ?: "Sign-in failed" }
                                busy = false
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(space.xxl))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(space.lg)) {
                    Text(
                        text = "What you get",
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(space.sm))
                    listOf(
                        "Repositories, code, issues, pull requests and Actions.",
                        "A built-in MCP server your agents can drive directly.",
                        "Nothing reloads when you switch apps — the session stays warm.",
                    ).forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                text = "•",
                                style = type.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(space.sm))
                            Text(
                                text = line,
                                style = type.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(space.xxxl * 2))
        }
    }
}

private enum class AuthMode { Device, Pat }

@Composable
private fun AuthTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val motion = LocalMotion.current

    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = motion.quick(),
        label = "tab-bg",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.label,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun DeviceCodePanel(
    code: DeviceFlowClient.DeviceCode?,
    waiting: Boolean,
    busy: Boolean,
    error: String?,
    onCopy: () -> Unit,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(space.lg)) {
            Text(
                text = "Enter this code on GitHub",
                style = type.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.xs))
            Text(
                text = "Open github.com/login/device in any browser and type the code.",
                style = type.bodySmall,
                color = colors.textTertiary,
            )

            Spacer(Modifier.height(space.lg))

            if (code == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = "Could not fetch a code",
                            style = type.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
            } else {
                CodeWell(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(space.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = code.userCode,
                            style = type.headline.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCopy) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy code",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(space.sm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaPill(
                        text = "Expires in ${code.expiresInSeconds / 60} min",
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.width(space.sm))
                    if (code.verificationUri.isNotBlank()) {
                        MetaPill(
                            text = code.verificationUri,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.danger,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text(
                        text = error,
                        style = type.bodySmall,
                        color = colors.danger,
                    )
                }
            }

            Spacer(Modifier.height(space.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Button(
                    onClick = onStart,
                    enabled = code != null && !waiting && !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (waiting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(space.sm))
                        Text("Waiting…", style = type.label)
                    } else {
                        Text("I've entered the code", style = type.label)
                    }
                }
                OutlinedButton(onClick = onRefresh, enabled = !waiting && !busy) {
                    Text("New code", style = type.label)
                }
            }
        }
    }
}

@Composable
private fun TokenPanel(
    token: String,
    visible: Boolean,
    busy: Boolean,
    error: String?,
    onTokenChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(space.lg)) {
            Text(
                text = "Personal access token",
                style = type.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.xs))
            Text(
                text = "Create a fine-grained token with the `repo` and `workflow` scopes.",
                style = type.bodySmall,
                color = colors.textTertiary,
            )

            Spacer(Modifier.height(space.lg))

            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token", style = type.bodySmall) },
                singleLine = true,
                textStyle = type.code.copy(fontSize = 13.sp()),
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (visible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = if (visible) "Hide token" else "Show token",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
            )

            if (error != null) {
                Spacer(Modifier.height(space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.danger,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text(
                        text = error,
                        style = type.bodySmall,
                        color = colors.danger,
                    )
                }
            }

            Spacer(Modifier.height(space.lg))

            Button(
                onClick = onSubmit,
                enabled = token.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Sign in", style = type.label)
                }
            }
        }
    }
}

/** Small helper so the token field can use a sized code style. */
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)
