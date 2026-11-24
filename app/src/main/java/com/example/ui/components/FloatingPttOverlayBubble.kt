package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PttThemeStyle
import com.example.ui.theme.SnapBlue
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * In-app draggable floating Push-to-Talk button used when a live walkie-talkie PTT
 * session is active while the app is open and Android's system "Display over other apps"
 * overlay permission has not been granted yet.
 *
 * Supports custom [PttThemeStyle] colors and animated glowing halo rings.
 */
@Composable
fun FloatingPttOverlayBubble(
    peerName: String,
    isTransmitting: Boolean,
    remoteSpeakerName: String?,
    onSetTransmitting: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    pttTheme: PttThemeStyle = PttThemeStyle.CLASSIC,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    val standbyColor = remember(pttTheme) { parseHexColor(pttTheme.standbyColorHex) }
    val liveColor = remember(pttTheme) { parseHexColor(pttTheme.liveColorHex) }
    val glowColor = remember(pttTheme) { parseHexColor(pttTheme.ringGlowHex) }

    val circleColor = when {
        isTransmitting -> liveColor
        !remoteSpeakerName.isNullOrBlank() -> SnapBlue
        else -> standbyColor
    }
    val circleLabel = when {
        isTransmitting -> "LIVE"
        !remoteSpeakerName.isNullOrBlank() -> "HEAR"
        else -> "PTT"
    }

    val showGlowRing = pttTheme.hasPulseRing || isTransmitting
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_glow_ring")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTransmitting) 1.24f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isTransmitting) 650 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ptt_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isTransmitting) 0.55f else 0.36f,
        targetValue = if (isTransmitting) 0.14f else 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isTransmitting) 650 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ptt_pulse_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(end = 14.dp, bottom = 92.dp)
            .testTag("in_app_floating_ptt_container")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp)
        ) {
            if (showGlowRing) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(glowColor.copy(alpha = pulseAlpha))
                        .border(BorderStroke(2.dp, glowColor.copy(alpha = 0.75f)), CircleShape)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .shadow(
                        elevation = if (showGlowRing) 14.dp else 10.dp,
                        shape = CircleShape,
                        ambientColor = glowColor,
                        spotColor = glowColor
                    )
                    .clip(CircleShape)
                    .background(circleColor)
                    .border(
                        BorderStroke(
                            width = if (pttTheme.hasPulseRing) 3.dp else 2.5.dp,
                            color = if (pttTheme.hasPulseRing) glowColor else Color.White
                        ),
                        CircleShape
                    )
                    .semantics {
                        contentDescription = "Hold to stream live push to talk audio to $peerName"
                    }
                    .pointerInput(touchSlop) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isDragging = false
                            var transmittingStarted = true
                            onSetTransmitting(true)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    break
                                }
                                val delta = change.position - change.previousPosition
                                totalDragX += delta.x
                                totalDragY += delta.y
                                if (!isDragging && hypot(totalDragX.toDouble(), totalDragY.toDouble()) > touchSlop) {
                                    isDragging = true
                                    if (transmittingStarted) {
                                        transmittingStarted = false
                                        onSetTransmitting(false)
                                    }
                                }
                                if (isDragging) {
                                    offsetX += delta.x
                                    offsetY += delta.y
                                    change.consume()
                                }
                            }

                            if (transmittingStarted) {
                                onSetTransmitting(false)
                            }
                        }
                    }
                    .testTag("in_app_floating_ptt_button")
            ) {
                Text(
                    text = circleLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xDD111827),
            border = BorderStroke(
                1.dp,
                if (pttTheme.hasPulseRing) glowColor.copy(alpha = 0.65f) else Color(0x44FFFFFF)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp)
            ) {
                Text(
                    text = peerName.ifBlank { "Private Chat" }.take(14),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable(onClick = onOpenChat)
                        .testTag("in_app_floating_ptt_open_chat")
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .testTag("in_app_floating_ptt_dismiss")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Hide floating push to talk button",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
