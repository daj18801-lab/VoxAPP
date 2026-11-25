package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ReferralRecord
import com.example.data.SpotlightFrameColor
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.data.generateReferralCode
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun SpotlightFrameShopCard(
    userProfile: UserProfile?,
    isProcessingWallet: Boolean,
    onBuySpotlightFrame: (SpotlightFrameColor) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeHex = userProfile?.activeSpotlightColorHex()
    val initialColor = remember(activeHex) {
        SpotlightFrameColor.entries.firstOrNull { it.primaryHex.equals(activeHex, ignoreCase = true) }
            ?: SpotlightFrameColor.ELECTRIC_CYAN
    }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    val isActive = userProfile?.hasActiveSpotlightFrame() == true
    val selectedAccent = parseHexColor(selectedColor.primaryHex, VoiceCyan)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = selectedAccent.copy(alpha = if (isActive) 0.72f else 0.42f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("spotlight_frame_shop_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "24-Hour Spotlight Frame",
                    tint = selectedAccent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "24-Hour Spotlight Frame",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isActive) SafetyEmerald.copy(alpha = 0.16f) else selectedAccent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (isActive) {
                            "ACTIVE · ${userProfile?.spotlightFrameRemainingLabel().orEmpty()}"
                        } else {
                            "${SpotlightFrameColor.PRICE_POINTS} pts"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isActive) SafetyEmerald else selectedAccent,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Stand out with a glowing animated neon border around your avatar across Discover and Chats for 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live animated preview of the neon frame around the user's avatar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, selectedAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpeakingAvatarCircle(
                        displayName = userProfile?.displayName?.ifBlank { "You" } ?: "You",
                        colorHex = userProfile?.avatarColorHex ?: "#0088FF",
                        speakingLevel = 0f,
                        micActive = false,
                        size = 62.dp,
                        photoBase64 = userProfile?.photoBase64.orEmpty(),
                        spotlightColorHex = selectedColor.primaryHex
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = selectedColor.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = selectedAccent
                        )
                        Text(
                            text = "Live preview in Discover & Chats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isActive) {
                            Text(
                                text = "Glowing now · ${userProfile?.spotlightFrameRemainingLabel().orEmpty()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SafetyEmerald
                            )
                        }
                    }
                }
            }

            // Neon color palette selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpotlightFrameColor.entries.forEach { option ->
                    val optionColor = parseHexColor(option.primaryHex, VoiceCyan)
                    val isSelected = selectedColor == option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedColor = option }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("spotlight_color_${option.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(optionColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else optionColor.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "${option.label} selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = option.label.substringAfter(" "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) optionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Button(
                onClick = { onBuySpotlightFrame(selectedColor) },
                enabled = !isProcessingWallet,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedAccent,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("buy_spotlight_frame_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isActive) {
                        "Activate ${selectedColor.label} (24h · 5 pts)"
                    } else {
                        "Buy 24h Spotlight Frame · 5 pts"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DailyLoginStreakCard(
    userProfile: UserProfile?,
    staffTier: StaffTier,
    modifier: Modifier = Modifier
) {
    val streakDays = (userProfile?.loginStreakDays ?: 0).coerceAtLeast(0)
    val halfPoints = (userProfile?.halfPointsBalance ?: 0).coerceIn(0, 1)
    val isFreeMember = (userProfile?.isProActive(staffTier) != true) && staffTier == StaffTier.USER
    val cycleProgress = if (streakDays == 0) 0 else ((streakDays - 1) % 7) + 1
    val daysRemaining = (7 - cycleProgress).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_login_streak_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = "Daily Login Streak",
                    tint = if (streakDays >= 7) LiveCoral else ModAmber,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Daily Login Streak",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = LiveCoral.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (streakDays == 1) "1 Day" else "$streakDays Days",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = LiveCoral,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (isFreeMember) {
                    "Free members earn +0.5 pt every 7-day login streak! Two 7-day streaks automatically combine into +1 full spendable point."
                } else {
                    "You're on a $streakDays-day login streak! The 7-day +0.5 pt bonus applies to Free members (your Pro/Staff perks are already active)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 7-day check-in dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (day in 1..7) {
                    val completed = day <= cycleProgress
                    val isRewardDay = day == 7
                    val circleBg = when {
                        completed && isRewardDay -> SafetyEmerald
                        completed -> ModAmber
                        isRewardDay -> SafetyEmerald.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val borderCol = when {
                        completed && isRewardDay -> SafetyEmerald
                        completed -> ModAmber
                        isRewardDay -> SafetyEmerald.copy(alpha = 0.65f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(circleBg)
                                .border(1.5.dp, borderCol, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Day $day complete",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = if (isRewardDay) "+½" else "$day",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (isRewardDay) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (isRewardDay) "0.5pt" else "D$day",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isRewardDay || completed) FontWeight.Bold else FontWeight.Normal,
                            color = if (isRewardDay) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isFreeMember) {
                val statusText = when {
                    halfPoints > 0 && daysRemaining == 0 ->
                        "Milestone unlocked! You have 0.5 pt banked — keep logging in 7 more days to convert it into +1 full point."
                    halfPoints > 0 ->
                        "0.5 pt banked! Log in $daysRemaining more consecutive day(s) to earn +0.5 pt and roll into +1 full spendable point."
                    daysRemaining == 0 && streakDays >= 7 ->
                        "7-day streak completed! Your +0.5 pt bonus was credited today."
                    else ->
                        "Log in $daysRemaining more consecutive day(s) to earn +0.5 pt."
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SafetyEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SafetyEmerald,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InviteFriendReferralCard(
    currentUserId: String,
    userProfile: UserProfile?,
    isEmailVerified: Boolean,
    incomingReferral: ReferralRecord?,
    invitedReferrals: List<ReferralRecord>,
    onRedeemReferralCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val myCode = remember(userProfile?.referralCode, currentUserId) {
        userProfile?.referralCode.orEmpty().ifBlank { generateReferralCode(currentUserId) }
    }
    var codeDraft by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("invite_friend_referral_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Invite a friend bonus",
                    tint = SafetyEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Invite-a-Friend Bonus",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SafetyEmerald.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "+2 pts Each",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = SafetyEmerald,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Invite a friend with your code. Once they verify their account and send their first Push-to-Talk clip, BOTH of you automatically earn +2 points!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // My Invite Code Box with Copy + Share
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your Invite Code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = myCode,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("my_referral_code_text")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("VOX Invite Code", myCode))
                                copiedNotice = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("copy_referral_code_button")
                        ) {
                            Icon(
                                imageVector = if (copiedNotice) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Copy invite code",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedNotice) "Copied!" else "Copy Code")
                        }
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join me on VOX! Enter my invite code $myCode in Profile → Settings & Points, verify your account, and send your first PTT clip so we both earn +2 points!"
                                    )
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share VOX Invite"))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyEmerald,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("share_referral_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share invite code",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Invite")
                        }
                    }
                }
            }

            // Redeem a Friend's Invite Code OR Show Active Incoming Referral Checklist
            if (incomingReferral == null) {
                Text(
                    text = "Have a friend's invite code?",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = codeDraft,
                        onValueChange = { codeDraft = it.uppercase().take(24) },
                        placeholder = { Text("VOX-XXXXXX") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("redeem_referral_code_input")
                    )
                    Button(
                        onClick = {
                            if (codeDraft.isNotBlank()) {
                                onRedeemReferralCode(codeDraft.trim())
                                codeDraft = ""
                            }
                        },
                        enabled = codeDraft.trim().length >= 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("redeem_referral_code_button")
                    ) {
                        Text("Apply")
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (incomingReferral.bonusClaimed) {
                        SafetyEmerald.copy(alpha = 0.13f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (incomingReferral.bonusClaimed) SafetyEmerald.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("incoming_referral_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (incomingReferral.bonusClaimed) {
                                "Invite Bonus Claimed! (+2 pts each)"
                            } else {
                                "Invited by ${incomingReferral.inviterName.ifBlank { "Friend" }} · Unlock +2 pts"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (incomingReferral.bonusClaimed) SafetyEmerald else MaterialTheme.colorScheme.onSurface
                        )
                        ReferralChecklistStep(
                            done = true,
                            label = "Invite code ${incomingReferral.referralCode} linked"
                        )
                        ReferralChecklistStep(
                            done = incomingReferral.accountVerified || isEmailVerified,
                            label = "Account verified"
                        )
                        ReferralChecklistStep(
                            done = incomingReferral.firstPttSent || incomingReferral.bonusClaimed,
                            label = "Send your first PTT / voice clip in any DM"
                        )
                    }
                }
            }

            if (invitedReferrals.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Invited Friends (${invitedReferrals.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "+${userProfile?.referralEarnedPoints ?: 0L} pts earned",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SafetyEmerald
                    )
                }
                invitedReferrals.take(5).forEach { rec ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rec.inviteeName.ifBlank { "New Friend" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (rec.bonusClaimed) SafetyEmerald.copy(alpha = 0.16f) else ModAmber.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (rec.bonusClaimed) "+2 pts Awarded" else "Waiting for 1st PTT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (rec.bonusClaimed) SafetyEmerald else ModAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferralChecklistStep(
    done: Boolean,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (done) "Completed" else "Pending",
            tint = if (done) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
