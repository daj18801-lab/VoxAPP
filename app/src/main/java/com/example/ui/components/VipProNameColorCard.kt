package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.data.VipProNameColorPalette
import com.example.data.isAllowedVipProNameColorHex
import com.example.ui.theme.ModAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VipProUsernameColorCard(
    userProfile: UserProfile?,
    staffTier: StaffTier,
    onSelectColorHex: (String) -> Unit,
    onUpgradeToVipPro: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canCustomize = userProfile?.canChooseVipProNameColor(staffTier) == true
    val currentHex = userProfile?.customNameColorHex?.trim()?.uppercase().orEmpty()
    val effectiveSelectedHex = if (isAllowedVipProNameColorHex(currentHex)) currentHex else ""
    val previewName = userProfile?.effectiveDisplayName()?.ifBlank { "Your Username" } ?: "Your Username"
    val previewColor = if (canCustomize && effectiveSelectedHex.isNotBlank()) {
        parseHexColor(effectiveSelectedHex)
    } else {
        usernameColor(
            tier = staffTier,
            isPro = userProfile?.isProActive(staffTier) == true,
            userId = userProfile?.userId
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (canCustomize) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("vip_pro_username_color_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canCustomize) MaterialTheme.colorScheme.primaryContainer
                            else ModAmber.copy(alpha = 0.16f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (canCustomize) Icons.Rounded.Palette else Icons.Rounded.Lock,
                        contentDescription = "VIP Pro Username Color",
                        tint = if (canCustomize) MaterialTheme.colorScheme.primary else ModAmber
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VIP Pro Username Color",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (canCustomize) {
                            "Choose your custom username color across rooms, DMs, and user lists."
                        } else {
                            "Upgrade to VIP Pro to customize your username color across VOX."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live bold username preview
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = previewName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = previewColor,
                            modifier = Modifier.testTag("vip_pro_name_color_preview")
                        )
                    }
                    if (canCustomize && effectiveSelectedHex.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onSelectColorHex("") },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("vip_pro_name_color_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reset to default color",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Default")
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (canCustomize) 1f else 0.45f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VipProNameColorPalette.forEach { option ->
                    val swatch = parseHexColor(option.hex)
                    val selected = canCustomize && effectiveSelectedHex.equals(option.hex, ignoreCase = true)
                    val checkTint = if (swatch.luminance() > 0.55f) Color.Black else Color.White
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Select ${option.label} username color" }
                            .clip(CircleShape)
                            .clickable(enabled = canCustomize) {
                                onSelectColorHex(option.hex)
                            }
                            .testTag("vip_pro_color_swatch_${option.hex.removePrefix("#")}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.18f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = checkTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Red (Admin), Green (Support), and Purple (Room Leader) are reserved and cannot be selected.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!canCustomize) {
                Button(
                    onClick = onUpgradeToVipPro,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ModAmber,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("vip_pro_color_upgrade_button")
                ) {
                    Text(
                        text = "Upgrade to VIP Pro to Unlock",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
