package app.parallax.opencode.ui.screens.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.Orb
import app.parallax.opencode.ui.components.OrbMood
import app.parallax.opencode.ui.components.PrimaryButton
import app.parallax.opencode.ui.components.SoftCard
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode

@Composable
fun ConnectScreen(
    repository: AgentRepository,
    initialLink: String?,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ConnectViewModel = viewModel { ConnectViewModel(repository) },
) {
    val c = ParallaxTheme.colors
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    fun scanOpenCode() {
        if (scanning || busy) return
        scanning = true
        scanError = null
        vm.clearError()
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { result ->
                val link = result.rawValue
                if (link.isNullOrBlank()) scanError = "Couldn't read this QR code."
                else vm.pairOpenCode(link)
            }
            .addOnFailureListener { scanError = "Scanner unavailable. Enter your server address below." }
            .addOnCompleteListener { scanning = false }
    }

    var mode by remember { mutableStateOf<Mode?>(null) }
    var ocUrl by remember { mutableStateOf("") }
    var ocPassword by remember { mutableStateOf("") }
    var ocPwVisible by remember { mutableStateOf(false) }
    var opUrl by remember { mutableStateOf("") }
    var opPassword by remember { mutableStateOf("") }
    var opPwVisible by remember { mutableStateOf(false) }

    // Pairing deep link: run the same redeem flow.
    LaunchedEffect(initialLink) {
        if (initialLink != null && (initialLink.startsWith("openchamber://") || initialLink.startsWith("parallax://connect"))) {
            vm.redeem(initialLink)
        }
    }
    LaunchedEffect(connected) {
        if (connected != null) onConnected()
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.xxxl))
            Orb(140.dp, OrbMood.IDLE)
            Spacer(Modifier.height(Space.lg))
            Text(
                "Connect your server",
                style = ParallaxTheme.type.display,
                color = c.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Parallax drives the opencode agents running on your computer.",
                style = ParallaxTheme.type.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xxl))

            SoftCard(Modifier.fillMaxWidth(), onClick = { mode = if (mode == Mode.OPENCODE) null else Mode.OPENCODE }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Dns, size = 44.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Log in with OpenCode", style = ParallaxTheme.type.title, color = c.text)
                        Text("Server address and password", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(20.dp))
                }
                if (mode == Mode.OPENCODE) {
                    Spacer(Modifier.height(Space.lg))
                    PrimaryButton("Scan OpenCode QR", onClick = ::scanOpenCode, modifier = Modifier.fillMaxWidth(), enabled = !busy && !scanning)
                    Text("On your computer, run opencode pair --remote.", style = ParallaxTheme.type.bodySmall, color = c.textMuted,
                        modifier = Modifier.padding(vertical = Space.sm))
                    Spacer(Modifier.height(Space.sm))
                    AiryField(value = opUrl, onValue = { opUrl = it }, hint = "https://your-server")
                    if (opUrl.startsWith("http://", ignoreCase = true)) Text("HTTP sends credentials unencrypted. Use only on a trusted private network.", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                    Spacer(Modifier.height(Space.sm))
                    AiryPasswordField(value = opPassword, onValue = { opPassword = it }, visible = opPwVisible, onToggle = { opPwVisible = !opPwVisible })
                    Spacer(Modifier.height(Space.md))
                    PrimaryButton(if (busy) "Connecting…" else "Log in", onClick = { vm.verifyOpencode(opUrl, opPassword) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && opUrl.isNotBlank())
                }
            }

            Spacer(Modifier.height(Space.md))

            SoftCard(Modifier.fillMaxWidth(), onClick = { mode = if (mode == Mode.OPENCHAMBER) null else Mode.OPENCHAMBER }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Layers, tint = c.textMuted, background = c.raised, size = 44.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Log in with OpenChamber", style = ParallaxTheme.type.title, color = c.text)
                        Text("Address and UI password", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(20.dp))
                }
                if (mode == Mode.OPENCHAMBER) {
                    Spacer(Modifier.height(Space.lg))
                    AiryField(value = ocUrl, onValue = { ocUrl = it }, hint = "https://your-server")
                    if (ocUrl.startsWith("http://", ignoreCase = true)) Text("HTTP sends credentials unencrypted. Use only on a trusted private network.", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                    Spacer(Modifier.height(Space.sm))
                    AiryPasswordField(value = ocPassword, onValue = { ocPassword = it }, visible = ocPwVisible, onToggle = { ocPwVisible = !ocPwVisible })
                    Spacer(Modifier.height(Space.md))
                    PrimaryButton(if (busy) "Connecting…" else "Log in", onClick = { vm.loginOpenChamber(ocUrl, ocPassword) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && ocUrl.isNotBlank())
                }
            }

            Spacer(Modifier.height(Space.xxxl))
        }

        if (error != null || scanError != null) {
            ErrorPill(
                message = error ?: scanError.orEmpty(),
                onDismiss = { scanError = null; vm.clearError() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Space.lg, start = Space.gutter, end = Space.gutter),
            )
        }
    }


}

private enum class Mode { OPENCHAMBER, OPENCODE }

@Composable
private fun AiryField(value: String, onValue: (String) -> Unit, hint: String) {
    val c = ParallaxTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = ParallaxTheme.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.raised)
            .padding(horizontal = Space.lg, vertical = Space.md),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = ParallaxTheme.type.body, color = c.textFaint)
                inner()
            }
        },
    )
}

@Composable
private fun AiryPasswordField(value: String, onValue: (String) -> Unit, visible: Boolean, onToggle: () -> Unit) {
    val c = ParallaxTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        textStyle = ParallaxTheme.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.raised)
            .padding(horizontal = Space.lg, vertical = Space.md),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Password", style = ParallaxTheme.type.body, color = c.textFaint)
                    inner()
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = c.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Connect light")
@Composable
private fun ConnectPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        ConnectScreen(repository = FakeAgentRepository(), initialLink = null, onConnected = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Connect dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        ConnectScreen(repository = FakeAgentRepository(), initialLink = null, onConnected = {})
    }
}
