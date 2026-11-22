package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ParticipantRole
import com.example.data.RoomCoverKey
import com.example.data.StaffTier
import com.example.ui.theme.AdminNameRedDark
import com.example.ui.theme.AdminNameRedLight
import com.example.ui.theme.ElectricIndigoBright
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SupportNameGreenDark
import com.example.ui.theme.SupportNameGreenLight
import com.example.ui.theme.UserNameBlueDark
import com.example.ui.theme.UserNameBlueLight
import com.example.ui.theme.VoiceCyan
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@DrawableRes
fun coverDrawableRes(coverKey: RoomCoverKey): Int = when (coverKey) {
    RoomCoverKey.HERO -> R.drawable.img_lounge_hero_1795298732457
    RoomCoverKey.MUSIC -> R.drawable.img_room_music_1795298732623
    RoomCoverKey.TECH -> R.drawable.img_room_tech_1795298732795
    RoomCoverKey.DEBATE -> R.drawable.img_room_debate_1795298731646
}

/**
 * Role-coded username colors across the entire app:
 * - Regular Users: Blue
 * - Support Staff: Green
 * - Administrators: Red
 */
@Composable
fun usernameColor(tier: StaffTier): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (tier) {
        StaffTier.ADMIN -> if (isDark) AdminNameRedDark else AdminNameRedLight
        StaffTier.SUPPORT -> if (isDark) SupportNameGreenDark else SupportNameGreenLight
        StaffTier.USER -> if (isDark) UserNameBlueDark else UserNameBlueLight
    }
}

@Composable
fun StaffTierBadge(
    tier: StaffTier,
    modifier: Modifier = Modifier
) {
    if (tier == StaffTier.USER) return
    val tint = usernameColor(tier)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Text(
            text = tier.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

fun parseHexColor(hex: String, fallback: Color = ElectricIndigoBright): Color {
    return try {
        val clean = hex.removePrefix("#")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else fallback
    } catch (_: Exception) {
        fallback
    }
}

fun formatTimestampShort(ts: Timestamp?): String {
    val date = (ts ?: Timestamp.now()).toDate()
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(date)
}

@Composable
fun LivePulseBadge(
    label: String = "LIVE",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = LiveCoral
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
fun RolePillBadge(
    role: ParticipantRole,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (role) {
        ParticipantRole.HOST -> ModAmber.copy(alpha = 0.22f) to ModAmber
        ParticipantRole.MODERATOR -> SafetyEmerald.copy(alpha = 0.20f) to SafetyEmerald
        ParticipantRole.SPEAKER -> VoiceCyan.copy(alpha = 0.18f) to VoiceCyan
        ParticipantRole.LISTENER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = role.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SafetyTagChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
fun SpeakingAvatarCircle(
    displayName: String,
    colorHex: String,
    speakingLevel: Float,
    micActive: Boolean,
    size: Dp = 56.dp,
    photoBase64: String = "",
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(colorHex)
    val ringColor = if (micActive && speakingLevel > 0.04f) {
        VoiceCyan
    } else if (micActive) {
        VoiceCyan.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val ringWidth = if (micActive && speakingLevel > 0.06f) {
        (2.5f + speakingLevel * 2.5f).dp
    } else {
        2.dp
    }
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    val photoBitmap = rememberBase64ImageBitmap(photoBase64)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(ringWidth, ringColor, CircleShape)
            .padding(if (photoBitmap != null) 3.dp else 4.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center
    ) {
        if (photoBitmap != null) {
            Image(
                bitmap = photoBitmap,
                contentDescription = "$displayName profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                style = if (size >= 52.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}

@Composable
fun VoiceWaveformBars(
    amplitude: Float,
    active: Boolean,
    barColor: Color = VoiceCyan,
    modifier: Modifier = Modifier
        .width(44.dp)
        .height(18.dp)
) {
    Canvas(modifier = modifier) {
        val barCount = 5
        val gap = size.width * 0.08f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val weights = floatArrayOf(0.55f, 0.9f, 1.0f, 0.8f, 0.5f)
        for (i in 0 until barCount) {
            val factor = if (active) {
                (0.22f + amplitude.coerceIn(0f, 1f) * weights[i]).coerceIn(0.2f, 1f)
            } else {
                0.18f
            }
            val h = size.height * factor
            val left = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = if (active) barColor else barColor.copy(alpha = 0.3f),
                topLeft = Offset(left, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
