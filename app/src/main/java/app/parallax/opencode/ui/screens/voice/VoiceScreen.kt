package app.parallax.opencode.ui.screens.voice

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.Orb
import app.parallax.opencode.ui.components.OrbMood
import app.parallax.opencode.ui.components.PrimaryButton
import app.parallax.opencode.ui.components.StatusPill
import app.parallax.opencode.ui.components.softShadow
import app.parallax.opencode.ui.theme.Elevation
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space

/** Full-screen hands-free conversation with your agent — like a call. */
@Composable
fun VoiceScreen(
    repository: AgentRepository,
    sessionId: String?,
    onClose: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val c = ParallaxTheme.colors
    val context = LocalContext.current
    val vm: VoiceViewModel = viewModel(key = "voice-${sessionId ?: "new"}") {
        VoiceViewModel(context.applicationContext as Application, repository, sessionId)
    }
    val phase by vm.phase.collectAsStateWithLifecycle()
    val level by vm.engine.level.collectAsStateWithLifecycle()
    val partial by vm.engine.partial.collectAsStateWithLifecycle()
    val spoken by vm.engine.caption.collectAsStateWithLifecycle()
    val heard by vm.lastHeard.collectAsStateWithLifecycle()
    val sid by vm.sessionId.collectAsStateWithLifecycle()
    val session by repository.sessions.collectAsStateWithLifecycle()
    val title = session.find { it.id == sid }?.title?.ifBlank { null } ?: "Voice"
    val voices by vm.engine.voices.collectAsStateWithLifecycle()
    val voiceName by vm.engine.voiceName.collectAsStateWithLifecycle()
    var pickVoice by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    var resumeAfterPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val canChooseVoice = phase == VoicePhase.Listening || phase is VoicePhase.Paused || phase is VoicePhase.Error

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.micDenied()
    }
    fun askMic() {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (has) vm.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { askMic() }
    LaunchedEffect(phase == VoicePhase.Ended) { if (phase == VoicePhase.Ended) onClose() }

    // Keep the screen awake for the whole conversation.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val listening = phase == VoicePhase.Listening || phase is VoicePhase.Approval
    val orbScale by animateFloatAsState(if (listening) 1f + level * 0.18f else 1f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "orb")
    val mood = when (phase) {
        is VoicePhase.Working, VoicePhase.Sending -> OrbMood.WORKING
        is VoicePhase.Approval -> OrbMood.NEEDS_YOU
        is VoicePhase.Error -> OrbMood.FAILED
        else -> OrbMood.IDLE
    }
    val label = when (phase) {
        VoicePhase.Listening -> "Listening"
        VoicePhase.Sending, is VoicePhase.Working -> "Working"
        is VoicePhase.Speaking -> "Speaking"
        is VoicePhase.Approval -> "Needs your OK"
        is VoicePhase.Paused -> "Paused"
        is VoicePhase.Error -> "Can't listen"
        else -> "Voice"
    }
    val caption = when (val p = phase) {
        VoicePhase.Listening -> partial.ifBlank { "I'm listening…" }
        VoicePhase.Sending -> heard
        is VoicePhase.Working -> p.caption
        is VoicePhase.Speaking -> spoken.ifBlank { p.text }
        is VoicePhase.Approval -> p.question
        is VoicePhase.Paused -> p.hint
        is VoicePhase.Error -> p.message
        else -> ""
    }
    val hint = when (phase) {
        is VoicePhase.Speaking -> "Tap to interrupt"
        VoicePhase.Listening -> "Say \"stop\" to end"
        is VoicePhase.Working -> heard.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: ""
        else -> ""
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm)) {
                CircleIconButton(Icons.Rounded.Close, "Close", onClick = onClose, modifier = Modifier.align(Alignment.CenterStart))
                StatusPill(title, label, Modifier.align(Alignment.Center))
                if (canChooseVoice) CircleIconButton(Icons.Rounded.RecordVoiceOver, "Choose voice", onClick = {
                    resumeAfterPicker = phase == VoicePhase.Listening
                    if (resumeAfterPicker) vm.toggleMute()
                    pickVoice = true
                }, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Spacer(Modifier.weight(0.8f))
            Box(
                Modifier.scale(orbScale).clip(CircleShape)
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) { vm.onOrbTap() },
            ) { Orb(200.dp, mood) }
            Spacer(Modifier.height(Space.xxxl))
            val captionScroll = androidx.compose.foundation.rememberScrollState()
            LaunchedEffect(caption, captionScroll.maxValue) {
                captionScroll.scrollTo(if (phase == VoicePhase.Listening) captionScroll.maxValue else 0)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = Space.xxl).height(140.dp), contentAlignment = Alignment.TopCenter) {
                Text(caption, style = ParallaxTheme.type.headline, color = c.text, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().verticalScroll(captionScroll))
            }
            Text(hint, style = ParallaxTheme.type.bodySmall, color = c.textFaint, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = Space.xxl))
            if (phase is VoicePhase.Error) {
                Spacer(Modifier.height(Space.lg))
                PrimaryButton("Try again", onClick = { askMic() })
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.xxxl, vertical = Space.xl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallButton(if (phase is VoicePhase.Paused) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Microphone", c.surface, c.text, enabled = phase == VoicePhase.Listening || phase is VoicePhase.Paused) { vm.toggleMute() }
                CallButton(Icons.Rounded.CallEnd, "End", c.failed, Color.White, size = 72.dp) { onClose() }
                CallButton(Icons.Rounded.ChatBubbleOutline, "Open chat", c.surface, c.text, enabled = sid != null) {
                    sid?.let(onOpenChat)
                }
            }
        }
    }
    if (pickVoice) {
        VoicePickerSheet(voices, voiceName, onPick = { vm.engine.preview(it) }, onDismiss = {
            vm.engine.stopSpeaking()
            pickVoice = false
            if (resumeAfterPicker) vm.start()
        })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(voices: List<app.parallax.opencode.ui.voice.VoiceOption>, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = ParallaxTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Space.gutter).navigationBarsPadding().padding(bottom = Space.xl)) {
            Text("Voice", style = ParallaxTheme.type.headline, color = c.text)
            Text("Tap a voice to select it and hear a sample.", style = ParallaxTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 2.dp, bottom = Space.md))
            if (voices.isEmpty()) Text("No voices found. Install Google's Speech Services from the Play Store.", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
            voices.forEach { v ->
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { onPick(v.name) }.padding(vertical = 12.dp, horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    app.parallax.opencode.ui.components.Mascot(36.dp, seed = v.name)
                    Spacer(Modifier.size(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(v.label, style = ParallaxTheme.type.label, color = c.text)
                        Text(if (v.natural) "Uses internet" else "Works offline", style = ParallaxTheme.type.caption, color = c.textMuted)
                    }
                    if (v.name == current) Icon(Icons.Rounded.Check, "Selected", tint = c.accentText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, bg: Color, tint: Color, size: Dp = 60.dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(size).softShadow(Elevation.card, CircleShape).clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) tint else ParallaxTheme.colors.textFaint, modifier = Modifier.size(size * 0.42f)) }
}
