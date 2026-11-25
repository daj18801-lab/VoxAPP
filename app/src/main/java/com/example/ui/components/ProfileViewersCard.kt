package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProfileViewRecord
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.ui.theme.VoiceCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ProBlue = SnapBlue
private val StageAmber = ModAmber
private val VoxElectric = VoiceCyan

/**
 * "Who Viewed My Profile" Teaser Card:
 * - Free users see a count teaser ("X people viewed your profile today") with blurred preview rows,
 *   plus instant options to unlock the full list with VOX Pro or for 2 points (24 hours).
 * - VOX Pro / Lifetime Pro / Staff / 24h-unlocked users see the full interactive viewer list.
 */
@Composable
fun WhoViewedMyProfileCard(
    userProfile: UserProfile?,
    staffTier: StaffTier,
    profileViews: List<ProfileViewRecord>,
    isProcessing: Boolean,
    onUnlockWithPoints: () -> Unit,
    onUpgradeToPro: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val todayKey = remember(profileViews.size) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(now))
    }
    val todayCount = remember(profileViews, todayKey) {
        profileViews.count { it.viewedDayKey == todayKey }
    }
    val isProOrStaff = userProfile?.isProActive(staffTier, now) == true
    val isUnlocked = userProfile?.hasProfileViewersUnlocked(staffTier, now) == true
    val unlockLabel = when {
        userProfile?.proLifetime == true -> "LIFETIME PRO"
        staffTier != StaffTier.USER -> "STAFF PERK"
        isProOrStaff -> "VOX PRO"
        isUnlocked -> "UNLOCKED · ${userProfile?.profileViewersUnlockRemainingLabel(now).orEmpty()}"
        else -> "LOCKED"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnlocked) ProBlue.copy(alpha = 0.45f) else StageAmber.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("who_viewed_profile_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(VoxElectric.copy(alpha = 0.2f), ProBlue.copy(alpha = 0.25f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Who viewed my profile",
                            tint = ProBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Who Viewed My Profile",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                todayCount == 1 -> "1 person viewed your profile today"
                                todayCount > 1 -> "$todayCount people viewed your profile today"
                                profileViews.isNotEmpty() -> "${profileViews.size} recent profile view${if (profileViews.size == 1) "" else "s"}"
                                else -> "No profile views yet today"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (todayCount > 0) ProBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("who_viewed_profile_count_text")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isUnlocked) ProBlue.copy(alpha = 0.16f) else StageAmber.copy(alpha = 0.18f),
                    border = BorderStroke(
                        1.dp,
                        if (isUnlocked) ProBlue.copy(alpha = 0.45f) else StageAmber.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isUnlocked) ProBlue else StageAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = unlockLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (isUnlocked) ProBlue else StageAmber
                        )
                    }
                }
            }

            if (isUnlocked) {
                if (profileViews.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "When members visit your profile from Discover, Reels, or Chats, they will appear right here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profileViews.take(8).forEach { record ->
                            ProfileViewerRow(
                                record = record,
                                blurred = false,
                                onClick = { onOpenUserProfile(record.viewerId) }
                            )
                        }
                    }
                }
            } else {
                // Free-user blurred teaser preview
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .blur(10.dp)
                    ) {
                        if (profileViews.isNotEmpty()) {
                            profileViews.take(3).forEach { record ->
                                ProfileViewerRow(
                                    record = record,
                                    blurred = true,
                                    onClick = {}
                                )
                            }
                        } else {
                            // Subtle blurred silhouette preview when zero views recorded yet
                            listOf("Member Preview", "Recent Visitor").forEachIndexed { idx, label ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (idx == 0) ProBlue else VoxElectric)
                                        )
                                        Column {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "@vox_member · Viewed recently",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Crisp lock badge overlay
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, ProBlue.copy(alpha = 0.45f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Viewers locked",
                                tint = ProBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (profileViews.isNotEmpty()) {
                                    "Reveal ${profileViews.size} viewer${if (profileViews.size == 1) "" else "s"}"
                                } else {
                                    "See everyone who checks your profile"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Dual CTA row: 2-Point 24h unlock OR VOX Pro unlimited
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onUnlockWithPoints,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StageAmber,
                            contentColor = Color(0xFF1F1600)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("unlock_profile_viewers_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unlock · 2 pts",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onUpgradeToPro,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ProBlue.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_viewers_go_pro_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            tint = ProBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VOX Pro",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ProBlue,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileViewerRow(
    record: ProfileViewRecord,
    blurred: Boolean,
    onClick: () -> Unit
) {
    val avatarColor = remember(record.viewerColorHex) { parseHexColor(record.viewerColorHex, VoxElectric) }
    val avatarBitmap = remember(record.viewerPhotoBase64) {
        if (record.viewerPhotoBase64.isNotBlank()) {
            runCatching {
                val bytes = Base64.decode(record.viewerPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }
    val relativeTime = remember(record.viewedAtMillis) {
        formatViewerRelativeTime(record.viewedAtMillis)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !blurred, onClick = onClick)
            .testTag("profile_viewer_row_${record.viewerId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = record.viewerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = record.viewerName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.viewerName.ifBlank { "VOX Member" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        record.viewerTierEnum() == StaffTier.ADMIN -> Color(0xFFEF4444)
                        record.viewerTierEnum() == StaffTier.SUPPORT -> SafetyEmerald
                        record.viewerProMember -> ProBlue
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${record.viewerHandle} · $relativeTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatViewerRelativeTime(viewedAtMillis: Long): String {
    if (viewedAtMillis <= 0L) return "Recently"
    val diffSec = ((System.currentTimeMillis() - viewedAtMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86400L}d ago"
    }
}
