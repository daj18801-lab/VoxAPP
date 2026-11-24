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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.UserProfile
import com.example.media.PttChirpSoundEngine
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald

/**
 * Custom PTT Themes & Walkie Chirps shop card rendered in Profile -> Settings & Points.
 * Users can spend earned or bought points to unlock custom floating PTT button colors,
 * animated glowing rings, and custom radio squawk/roger-beep audio chirps.
 */
@Composable
fun PttCustomizationShopCard(
    userProfile: UserProfile?,
    isProcessingWallet: Boolean,
    onSelectOrBuyTheme: (PttThemeStyle) -> Unit,
    onSelectOrBuyChirp: (PttChirpStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTheme = userProfile?.selectedPttThemeEnum() ?: PttThemeStyle.CLASSIC
    val activeChirp = userProfile?.selectedPttChirpEnum() ?: PttChirpStyle.STANDARD
    val pointsBalance = userProfile?.pointsBalance ?: 0L

    val previewStandby = remember(activeTheme) { parseHexColor(activeTheme.standbyColorHex) }
    val previewGlow = remember(activeTheme) { parseHexColor(activeTheme.ringGlowHex) }

    val pulseTransition = rememberInfiniteTransition(label = "ptt_shop_preview_pulse")
    val previewPulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ptt_shop_pulse_scale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ptt_customization_shop_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Custom PTT Themes & Walkie Chirps",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "PTT Themes & Walkie Chirps",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Customize your floating PTT button glow & radio tones",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "$pointsBalance pts",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Equipped Preview Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (activeTheme.hasPulseRing) previewGlow.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(62.dp)
                    ) {
                        if (activeTheme.hasPulseRing) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .scale(previewPulseScale)
                                    .clip(CircleShape)
                                    .background(previewGlow.copy(alpha = 0.28f))
                                    .border(BorderStroke(2.dp, previewGlow.copy(alpha = 0.75f)), CircleShape)
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(previewStandby)
                                .border(
                                    BorderStroke(
                                        width = if (activeTheme.hasPulseRing) 2.5.dp else 2.dp,
                                        color = if (activeTheme.hasPulseRing) previewGlow else Color.White
                                    ),
                                    CircleShape
                                )
                        ) {
                            Text(
                                text = "PTT",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Equipped: ${activeTheme.label}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chirp: ${activeChirp.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { PttChirpSoundEngine.playPreview(activeChirp) },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("ptt_shop_test_equipped_chirp")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Test equipped chirp sound",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Section 1: Floating PTT Button Colors & Glowing Rings
            Text(
                text = "Floating PTT Colors & Glow Rings",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            PttThemeStyle.entries.chunked(2).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowThemes.forEach { theme ->
                        val owned = userProfile?.ownsPttTheme(theme) == true
                        val equipped = activeTheme == theme
                        val swatchColor = remember(theme) { parseHexColor(theme.standbyColorHex) }
                        val ringColor = remember(theme) { parseHexColor(theme.ringGlowHex) }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (equipped) {
                                swatchColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = if (equipped) 1.5.dp else 1.dp,
                                color = if (equipped) swatchColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isProcessingWallet) {
                                    onSelectOrBuyTheme(theme)
                                }
                                .testTag("ptt_theme_item_${theme.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (theme.hasPulseRing) ringColor.copy(alpha = 0.25f)
                                                else Color.Transparent
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(
                                                    BorderStroke(
                                                        width = 2.dp,
                                                        color = if (theme.hasPulseRing) ringColor else Color.White
                                                    ),
                                                    CircleShape
                                                )
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = theme.label,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when {
                                                equipped -> "Equipped"
                                                owned -> "Owned · Tap to equip"
                                                else -> "${theme.pricePoints} pts"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = when {
                                                equipped -> SafetyEmerald
                                                owned -> MaterialTheme.colorScheme.primary
                                                else -> ModAmber
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = theme.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rowThemes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 2: Walkie-Talkie Chirps & Radio Squawks
            Text(
                text = "Walkie-Talkie Chirps & Roger Beeps",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            PttChirpStyle.entries.forEach { chirp ->
                val owned = userProfile?.ownsPttChirp(chirp) == true
                val equipped = activeChirp == chirp
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (equipped) {
                        SafetyEmerald.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        width = if (equipped) 1.5.dp else 1.dp,
                        color = if (equipped) SafetyEmerald.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ptt_chirp_row_${chirp.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { PttChirpSoundEngine.playPreview(chirp) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ptt_chirp_preview_${chirp.id}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Preview ${chirp.label}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chirp.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chirp.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (equipped) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SafetyEmerald.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Equipped",
                                        tint = SafetyEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SafetyEmerald
                                    )
                                }
                            }
                        } else if (owned) {
                            OutlinedButton(
                                onClick = { onSelectOrBuyChirp(chirp) },
                                enabled = !isProcessingWallet,
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("ptt_chirp_equip_${chirp.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RadioButtonChecked,
                                    contentDescription = "Equip ${chirp.label}",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Equip", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Button(
                                onClick = { onSelectOrBuyChirp(chirp) },
                                enabled = !isProcessingWallet,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ModAmber,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("ptt_chirp_buy_${chirp.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Unlock ${chirp.label} for ${chirp.pricePoints} points",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${chirp.pricePoints} pts",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
