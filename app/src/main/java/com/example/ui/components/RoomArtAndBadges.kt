package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ParticipantRole
import com.example.data.PresenceStatus
import com.example.data.RoomCoverKey
import com.example.data.StaffTier
import com.example.ui.theme.AdminNameRedDark
import com.example.ui.theme.AdminNameRedLight
import com.example.ui.theme.BusinessNameGoldDark
import com.example.ui.theme.BusinessNameGoldLight
import com.example.ui.theme.ElectricIndigoBright
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOfflineRed
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.RoomLeaderPurpleDark
import com.example.ui.theme.RoomLeaderPurpleLight
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SupportNameGreenDark
import com.example.ui.theme.SupportNameGreenLight
import com.example.ui.theme.UserNameBlueDark
import com.example.ui.theme.UserNameBlueLight
import com.example.ui.theme.VoiceCyan
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
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
 * App-wide CompositionLocal providing the set of user IDs who currently have an active
 * VoxStage Pro ($9.99/mo) membership so usernames render in Blue everywhere automatically.
 */
val LocalProUserIds = compositionLocalOf<Set<String>> { emptySet() }

/**
 * App-wide CompositionLocal mapping userId -> neon spotlight hex color for users who
 * currently have an active 24-Hour Spotlight Frame (5 pts).
 */
val LocalSpotlightFrameByUserId = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * App-wide CompositionLocal mapping userId -> custom username hex color (#RRGGBB)
 * configured by an App Admin using Custom Identity Disguise.
 */
val LocalCustomNameColors = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * App-wide CompositionLocal providing the set of user IDs who hold a Business Account
 * so App Admins and Support Staff see them in a bold gold color everywhere.
 */
val LocalBusinessUserIds = compositionLocalOf<Set<String>> { emptySet() }

/**
 * App-wide CompositionLocal indicating whether the currently signed-in viewer is
 * an App Admin or Support Staff member.
 */
val LocalViewerIsStaff = compositionLocalOf<Boolean> { false }

@Composable
fun isBusinessGoldForStaff(
    userId: String? = null,
    isBusiness: Boolean = false
): Boolean = LocalViewerIsStaff.current &&
    (isBusiness || (userId != null && userId in LocalBusinessUserIds.current))

@Composable
fun usernameFontWeight(
    userId: String? = null,
    isBusiness: Boolean = false,
    defaultWeight: FontWeight = FontWeight.SemiBold
): FontWeight = if (isBusinessGoldForStaff(userId, isBusiness)) FontWeight.ExtraBold else defaultWeight

/**
 * Role & Membership-coded username colors across the entire app:
 * - Admin Custom Identity Color (if set for this userId)
 * - Administrators: Red
 * - Support Staff: Green
 * - Business Accounts (seen by App Admins & Support Staff): Bold Gold
 * - Room Creators / Room Admins / Room Co-Admins: Purple
 * - Pro Members ($9.99/mo): Blue
 * - Free Members: White in Dark Theme, Black in Light Theme
 */
@Composable
fun usernameColor(
    tier: StaffTier,
    isPro: Boolean = false,
    isRoomLeader: Boolean = false,
    userId: String? = null,
    isBusiness: Boolean = false
): Color {
    val customHex = userId?.let { LocalCustomNameColors.current[it] }
    if (!customHex.isNullOrBlank()) {
        return parseHexColor(customHex)
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val proIds = LocalProUserIds.current
    val resolvedPro = isPro || (userId != null && userId in proIds)
    val resolvedBusinessGold = isBusinessGoldForStaff(userId, isBusiness)
    return when {
        tier == StaffTier.ADMIN -> if (isDark) AdminNameRedDark else AdminNameRedLight
        tier == StaffTier.SUPPORT -> if (isDark) SupportNameGreenDark else SupportNameGreenLight
        resolvedBusinessGold -> if (isDark) BusinessNameGoldDark else BusinessNameGoldLight
        isRoomLeader -> if (isDark) RoomLeaderPurpleDark else RoomLeaderPurpleLight
        resolvedPro -> if (isDark) UserNameBlueDark else UserNameBlueLight
        else -> if (isDark) Color.White else Color.Black
    }
}

@Composable
fun BusinessAccountBadge(
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gold = if (isDark) BusinessNameGoldDark else BusinessNameGoldLight
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = gold.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, gold.copy(alpha = 0.65f))
    ) {
        Text(
            text = "BUSINESS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = gold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ProMemberBadge(
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = if (isDark) UserNameBlueDark else UserNameBlueLight
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Text(
            text = "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val purpleTint = if (isDark) RoomLeaderPurpleDark else RoomLeaderPurpleLight
    val (bg, fg) = when (role) {
        ParticipantRole.HOST -> purpleTint.copy(alpha = 0.22f) to purpleTint
        ParticipantRole.MODERATOR -> purpleTint.copy(alpha = 0.18f) to purpleTint
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
    modifier: Modifier = Modifier,
    userId: String? = null,
    spotlightColorHex: String? = null
) {
    val accent = parseHexColor(colorHex)
    val spotlightMap = LocalSpotlightFrameByUserId.current
    val resolvedSpotlightHex = spotlightColorHex?.takeIf { it.isNotBlank() }
        ?: userId?.let { spotlightMap[it] }?.takeIf { it.isNotBlank() }
    val hasSpotlight = !resolvedSpotlightHex.isNullOrBlank()
    val spotlightColor = remember(resolvedSpotlightHex) {
        if (resolvedSpotlightHex != null) parseHexColor(resolvedSpotlightHex, VoiceCyan) else VoiceCyan
    }

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

    val spotlightModifier = if (hasSpotlight) {
        val infiniteTransition = rememberInfiniteTransition(label = "spotlight_frame")
        val sweepAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spotlight_rotation"
        )
        val glowPulse by infiniteTransition.animateFloat(
            initialValue = 0.52f,
            targetValue = 0.96f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "spotlight_pulse"
        )
        val hotCore = lerp(spotlightColor, Color.White, 0.52f)
        val deepEdge = lerp(spotlightColor, Color.Black, 0.12f)
        Modifier.drawBehind {
            val haloRadius = (this.size.minDimension / 2f) + 5.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        spotlightColor.copy(alpha = 0.58f * glowPulse),
                        spotlightColor.copy(alpha = 0.22f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )
            val strokePx = if (size >= 52.dp) 3.dp.toPx() else 2.4.dp.toPx()
            rotate(degrees = sweepAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            spotlightColor,
                            hotCore,
                            spotlightColor,
                            deepEdge,
                            hotCore,
                            spotlightColor
                        ),
                        center = center
                    ),
                    radius = (this.size.minDimension / 2f) - (strokePx / 2f),
                    style = Stroke(width = strokePx)
                )
            }
        }
    } else {
        Modifier.border(ringWidth, ringColor, CircleShape)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(spotlightModifier)
            .padding(
                if (hasSpotlight) {
                    if (size >= 52.dp) 4.dp else 3.dp
                } else {
                    if (photoBitmap != null) 3.dp else 4.dp
                }
            )
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

/**
 * Mutual DM "Talk Streak" badge shown beside friends whenever both users have exchanged
 * at least one PTT clip or message for 3+ consecutive days.
 */
@Composable
fun TalkStreakBadge(
    streakDays: Int,
    expiringToday: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (streakDays < 3) return
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val flameTint = if (expiringToday) ModAmber else LiveCoral
    val towerTint = if (isDark) VoiceCyan else ElectricIndigoBright
    val bgColor = flameTint.copy(alpha = if (isDark) 0.20f else 0.13f)
    val borderColor = flameTint.copy(alpha = if (expiringToday) 0.72f else 0.46f)
    val a11yText = buildString {
        append("$streakDays-day mutual Talk Streak")
        if (expiringToday) append(". Send a message or PTT clip today to keep it alive")
    }

    Surface(
        modifier = modifier.semantics { contentDescription = a11yText },
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 2.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = flameTint,
                modifier = Modifier.size(if (compact) 13.dp else 14.dp)
            )
            Icon(
                imageVector = Icons.Rounded.CellTower,
                contentDescription = null,
                tint = towerTint,
                modifier = Modifier.size(if (compact) 11.dp else 12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (expiringToday && !compact) "${streakDays}d • Talk today" else "${streakDays}d",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = flameTint
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

/**
 * High-contrast circular presence dot rendered beside usernames:
 * - Green when the user is online
 * - Red when the user is offline
 */
@Composable
fun OnlinePresenceDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val dotColor = if (isOnline) PresenceOnlineGreen else PresenceOfflineRed
    val statusLabel = if (isOnline) "Online" else "Offline"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(dotColor)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .semantics { contentDescription = statusLabel }
    )
}

/**
 * 3-state presence indicator dot:
 * - Online: Emerald Green
 * - Idle: Warm Amber
 * - Offline: Muted Red
 */
@Composable
fun PresenceStatusDot(
    status: PresenceStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val dotColor = when (status) {
        PresenceStatus.ONLINE -> PresenceOnlineGreen
        PresenceStatus.IDLE -> ModAmber
        PresenceStatus.OFFLINE -> PresenceOfflineRed
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(dotColor)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .semantics { contentDescription = status.label }
    )
}

/**
 * Periodic wall-clock tick so stale online indicators automatically transition to offline
 * if a peer loses network or force-closes the app without writing an offline event.
 */
@Composable
fun rememberPresenceClockMillis(intervalMs: Long = 30_000L): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}
