package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DirectMessage
import com.example.data.DirectThread
import com.example.data.DmBannerColor
import com.example.media.PttChirpSoundEngine
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SnapBlue

private fun parseAccentHex(hex: String, fallback: Color = SnapBlue): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/**
 * 24-Hour Scrolling Banner displayed near the top of a DM when active.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DmScrollingBannerBar(
    thread: DirectThread,
    onTapEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = remember(thread.bannerColorHex) {
        parseAccentHex(thread.bannerColorHex, SnapBlue)
    }
    val remaining = thread.bannerRemainingLabel()
    val author = thread.bannerAuthorName.ifBlank { "Member" }

    Surface(
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTapEdit)
            .testTag("dm_scrolling_banner_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = "Scrolling DM banner",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "★ ${thread.bannerText}   •   by $author ($remaining)   ★   ${thread.bannerText}   ",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 44.dp
                    )
                    .testTag("dm_scrolling_banner_text")
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.25f)
            ) {
                Text(
                    text = remaining,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Dialog for purchasing/replacing a 24-Hour Scrolling Banner in a DM (1 point).
 */
@Composable
fun PurchaseDmBannerDialog(
    pointsBalance: Long,
    isSending: Boolean,
    existingBannerText: String,
    onBuyBanner: (text: String, colorHex: String) -> Unit,
    onGoBuyPoints: () -> Unit,
    onDismiss: () -> Unit
) {
    var bannerDraft by remember { mutableStateOf(existingBannerText) }
    var selectedColor by remember { mutableStateOf(DmBannerColor.ELECTRIC_CYAN) }
    val hasEnoughPoints = pointsBalance >= 1L

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = "DM Scrolling Banner",
                tint = parseAccentHex(selectedColor.hex, SnapBlue),
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("24-Hour Scrolling DM Banner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Pin a glowing marquee banner near the top of this chat for 24 hours. Costs 1 point (Balance: $pointsBalance pts).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = bannerDraft,
                    onValueChange = { bannerDraft = it.take(140) },
                    label = { Text("Banner Message") },
                    placeholder = { Text("e.g., Party tonight at 9PM! Jump on PTT 🔥") },
                    supportingText = { Text("${bannerDraft.length}/140 characters") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dm_banner_message_input")
                )

                Text(
                    text = "Banner Color",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DmBannerColor.entries.forEach { option ->
                        val swatchColor = parseAccentHex(option.hex, SnapBlue)
                        val isSelected = selectedColor == option
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 38.dp else 32.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = option }
                                .testTag("dm_banner_color_${option.name.lowercase()}")
                        )
                    }
                }

                if (!hasEnoughPoints) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ModAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "You need 1 point to set a banner.",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onGoBuyPoints()
                                },
                                modifier = Modifier.testTag("dm_banner_buy_points_button")
                            ) {
                                Text("Buy Points", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasEnoughPoints && bannerDraft.trim().isNotEmpty()) {
                        onBuyBanner(bannerDraft.trim(), selectedColor.hex)
                    }
                },
                enabled = hasEnoughPoints && bannerDraft.trim().isNotEmpty() && !isSending,
                modifier = Modifier.testTag("confirm_dm_banner_button")
            ) {
                Icon(Icons.Rounded.Stars, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Launch (1 pt)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for sending an Emergency / Priority "Break-In" Chirp (1–2 pts) to an opted-in friend,
 * plus toggling your own opt-in for this DM.
 */
@Composable
fun PriorityBreakInChirpDialog(
    recipientName: String,
    recipientOptedIn: Boolean,
    myThreadOptIn: Boolean,
    pointsBalance: Long,
    isSending: Boolean,
    onToggleMyThreadOptIn: (Boolean) -> Unit,
    onSendPriorityChirp: (message: String, pointsCost: Int) -> Unit,
    onGoBuyPoints: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCost by remember { mutableIntStateOf(1) }
    var alertDraft by remember { mutableStateOf("") }
    val hasEnoughPoints = pointsBalance >= selectedCost.toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = "Priority Break-In Chirp",
                tint = LiveCoral,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Priority Break-In Chirp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Send a distinct two-tone radio alert that stands out at the top of $recipientName's Chats inbox until they open it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Recipient opt-in status banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (recipientOptedIn) LiveCoral.copy(alpha = 0.12f) else ModAmber.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (recipientOptedIn) LiveCoral.copy(alpha = 0.45f) else ModAmber.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (recipientOptedIn) {
                            "$recipientName has opted in to Priority Break-In Chirps."
                        } else {
                            "$recipientName hasn't opted in yet. Ask them to turn on Priority Chirps in this chat or Profile Settings."
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Tier selector: 1 pt vs 2 pts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCost == 1,
                        onClick = { selectedCost = 1 },
                        label = { Text("Priority · 1 pt") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ModAmber,
                            selectedLabelColor = Color(0xFF1F1600)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("priority_chirp_tier_1pt")
                    )
                    FilterChip(
                        selected = selectedCost == 2,
                        onClick = { selectedCost = 2 },
                        label = { Text("Urgent · 2 pts") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LiveCoral,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("priority_chirp_tier_2pt")
                    )
                }

                OutlinedButton(
                    onClick = {
                        PttChirpSoundEngine.playPriorityBreakInChirp(urgent = selectedCost >= 2)
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_priority_chirp_tone_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedCost >= 2) "Preview Urgent Two-Tone Alert" else "Preview Priority Radio Chirp")
                }

                OutlinedTextField(
                    value = alertDraft,
                    onValueChange = { alertDraft = it.take(160) },
                    label = { Text("Break-In Alert Message") },
                    placeholder = { Text("e.g., Need you on PTT ASAP!") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("priority_chirp_message_input")
                )

                if (!hasEnoughPoints) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ModAmber.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Balance: $pointsBalance pts (need $selectedCost)",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onGoBuyPoints()
                                }
                            ) {
                                Text("Buy Points", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Own opt-in switch for this chat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow $recipientName to break in",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Let them send you Priority Chirps in this chat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = myThreadOptIn,
                        onCheckedChange = onToggleMyThreadOptIn,
                        modifier = Modifier.testTag("dm_priority_chirp_opt_in_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientOptedIn && hasEnoughPoints) {
                        PttChirpSoundEngine.playPriorityBreakInChirp(urgent = selectedCost >= 2)
                        onSendPriorityChirp(alertDraft.trim(), selectedCost)
                        onDismiss()
                    }
                },
                enabled = recipientOptedIn && hasEnoughPoints && !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCost >= 2) LiveCoral else ModAmber,
                    contentColor = if (selectedCost >= 2) Color.White else Color(0xFF1F1600)
                ),
                modifier = Modifier.testTag("confirm_send_priority_chirp_button")
            ) {
                Text("Send (${selectedCost} pt${if (selectedCost == 1) "" else "s"})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * High-contrast Priority Break-In Chirp card pinned at the very top of ChatsHomeScreen.
 */
@Composable
fun PriorityChirpPinnedInboxCard(
    thread: DirectThread,
    onOpenThread: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUrgent = thread.lastPriorityChirpPoints >= 2
    val accent = if (isUrgent) LiveCoral else ModAmber

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, accent),
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onOpenThread)
            .testTag("priority_chirp_inbox_card_${thread.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = "Priority Break-In Chirp",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isUrgent) "URGENT BREAK-IN CHIRP" else "PRIORITY CHIRP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = accent
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = "${thread.lastPriorityChirpPoints.coerceIn(1, 2)} PT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = thread.lastPriorityChirpSenderName.ifBlank { "Close Friend" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = thread.lastPriorityChirpMessage.ifBlank { "Tap to jump in!" },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent
            ) {
                Text(
                    text = "OPEN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Distinct two-tone radio alert bubble rendered inside DirectChatScreen for PRIORITY_CHIRP messages.
 */
@Composable
fun PriorityChirpMessageCard(
    message: DirectMessage,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = LiveCoral.copy(alpha = 0.14f),
        border = BorderStroke(1.5.dp, LiveCoral.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("priority_chirp_message_${message.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(LiveCoral),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = "Priority Chirp Alert",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PRIORITY BREAK-IN CHIRP · ${message.senderName}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = LiveCoral
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
