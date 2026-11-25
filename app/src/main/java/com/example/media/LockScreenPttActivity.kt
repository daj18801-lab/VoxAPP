package com.example.media

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.VoxStageTheme

/**
 * Lock-Screen Push-to-Talk Activity rendered over the Android lock screen (`showWhenLocked="true"`)
 * whenever the user has an active live walkie-talkie PTT session and their phone screen locks.
 *
 * Uses zero sensitive permissions (no USE_FULL_SCREEN_INTENT, no DISABLE_KEYGUARD) and supports
 * both direct touch hold-to-talk and hardware Volume Down Push-to-Talk.
 */
class LockScreenPttActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()

        setContent {
            VoxStageTheme(darkTheme = true) {
                LockScreenPttContent(
                    onClose = { finish() }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (LivePttSessionManager.handleHardwarePttKey(this, pressed = true)) {
                            LivePttSessionManager.triggerPttHaptic(this)
                            return true
                        }
                    } else if (!LivePttSessionManager.activeThreadId.value.isNullOrBlank()) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (LivePttSessionManager.handleHardwarePttKey(this, pressed = false)) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        // Release transmission safely if user leaves the lock-screen overlay
        if (LivePttSessionManager.isTransmitting.value) {
            LivePttSessionManager.setTransmitting(this, false)
        }
    }
}

@Composable
private fun LockScreenPttContent(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activeThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val peerName by LivePttSessionManager.activePeerName.collectAsStateWithLifecycle()
    val isConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val isTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val remoteSpeakingName by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    val elapsedSec by LivePttSessionManager.transmittingElapsedSec.collectAsStateWithLifecycle()
    val maxSec by LivePttSessionManager.maxPttSeconds.collectAsStateWithLifecycle()
    val activeTheme by LivePttSessionManager.activePttTheme.collectAsStateWithLifecycle()

    LaunchedEffect(activeThreadId) {
        if (activeThreadId.isNullOrBlank()) {
            onClose()
        }
    }

    val standbyColor = parseHexOrFallback(activeTheme.standbyColorHex, Color(0xFF10B981))
    val liveColor = parseHexOrFallback(activeTheme.liveColorHex, Color(0xFFEF4444))
    val ringColor = parseHexOrFallback(activeTheme.ringGlowHex, Color(0xFF34D399))
    val activeCoreColor = if (isTransmitting) liveColor else standbyColor

    val infiniteTransition = rememberInfiniteTransition(label = "lock_ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTransmitting || remoteSpeakingName != null) 1.18f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isTransmitting) 550 else 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_ptt_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060B18),
                        Color(0xFF0B162C),
                        Color(0xFF050A14)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Lock screen PTT",
                        tint = ringColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "VOX • LOCK-SCREEN PTT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.1.sp
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("lock_screen_ptt_close_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close lock-screen PTT"
                )
            }
        }

        // Center Live PTT Channel & Giant Hold-to-Talk Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = peerName.ifBlank { "Private Walkie Channel" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))

            val statusText = when {
                isTransmitting -> "ON AIR • ${elapsedSec}s / ${maxSec}s"
                !remoteSpeakingName.isNullOrBlank() -> "🔊 $remoteSpeakingName is talking…"
                isConnected -> "LIVE STANDBY • Ready to transmit"
                else -> "Connecting walkie channel…"
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = when {
                    isTransmitting -> liveColor.copy(alpha = 0.25f)
                    !remoteSpeakingName.isNullOrBlank() -> Color(0xFF0EA5E9).copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.10f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (!remoteSpeakingName.isNullOrBlank()) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.Filled.Radio
                        },
                        contentDescription = null,
                        tint = if (isTransmitting) liveColor else ringColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            // Giant Lock-Screen Hold-to-Talk Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(236.dp)
            ) {
                // Outer animated glow ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    (if (isTransmitting) liveColor else ringColor).copy(alpha = 0.42f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Core touch target
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(188.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    activeCoreColor,
                                    activeCoreColor.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .border(
                            width = 4.dp,
                            color = if (isTransmitting) Color.White else ringColor,
                            shape = CircleShape
                        )
                        .testTag("lock_screen_ptt_hold_button")
                        .pointerInput(activeThreadId) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                LivePttSessionManager.triggerPttHaptic(context)
                                LivePttSessionManager.setTransmitting(context, true)
                                waitForUpOrCancellation()
                                LivePttSessionManager.setTransmitting(context, false)
                            }
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Hold to talk on lock screen",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isTransmitting) "TALKING" else "HOLD TO TALK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Hold the button above — or hold Volume Down — to talk without unlocking your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

private fun parseHexOrFallback(hex: String, fallback: Color): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrElse { fallback }
}
