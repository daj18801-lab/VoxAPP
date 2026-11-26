package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.example.data.AdminPreviewMode
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan
import kotlin.math.roundToInt

private val ADMIN_DISGUISE_SWATCHES = listOf(
    "#FF3B30", // Crimson Red
    "#FF9500", // Solar Orange
    "#FFD60A", // Gold
    "#34C759", // Emerald Green
    "#00C4FF", // Electric Cyan
    "#0088FF", // VOX Blue
    "#7C4DFF", // Royal Violet
    "#FF2D95", // Neon Magenta
    "#FFFFFF", // Pure White
    "#111827"  // Obsidian Black
)

private val ADMIN_DISGUISE_MEMBERSHIPS = listOf(
    "" to "Default (Admin)",
    "FREE" to "Free Member",
    "PRO" to "VOX Pro",
    "VIP_PRO" to "VIP Pro",
    "BUSINESS" to "Business Account",
    "SUPPORT" to "Support Staff",
    "ADMIN" to "Admin"
)

/**
 * Floating, draggable quick-switcher card anchored on the right side for the real App Admin
 * whenever Account Preview Mode (Free / VOX Pro / VIP Pro / Support Staff)
 * OR Custom Identity Disguise is active.
 * Can be dragged anywhere on screen; if exited via the close button, logs the admin out
 * and back in as Admin.
 */
@Composable
fun AdminPreviewFloatingBanner(
    previewMode: AdminPreviewMode,
    hasCustomDisguise: Boolean,
    customDisplayName: String,
    customMembershipLabel: String,
    onSelectPreviewMode: (AdminPreviewMode) -> Unit,
    onResetIdentityDisguise: () -> Unit,
    onExitAndReloginAsAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showQuickSwitcher by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF01A1033),
        shadowElevation = 10.dp,
        border = BorderStroke(1.5.dp, ModAmber.copy(alpha = 0.85f)),
        modifier = modifier
            .statusBarsPadding()
            .padding(end = 8.dp)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .widthIn(min = 168.dp, max = 236.dp)
            .testTag("admin_preview_banner")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = "Drag floating admin widget",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Admin preview active",
                    tint = ModAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (previewMode.isPreviewActive()) previewMode.shortLabel else "Disguise",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onExitAndReloginAsAdmin() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("admin_banner_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit preview and re-login as Admin",
                        tint = LiveCoral,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (hasCustomDisguise) {
                Text(
                    text = buildString {
                        if (customDisplayName.isNotBlank()) append(customDisplayName)
                        if (customMembershipLabel.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(customMembershipLabel)
                        }
                    }.ifBlank { "Custom color active" },
                    style = MaterialTheme.typography.labelSmall,
                    color = VoiceCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VoiceCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VoiceCyan.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showQuickSwitcher = true }
                        .testTag("admin_banner_switch_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch account preview",
                            tint = VoiceCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Switch",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VoiceCyan
                        )
                    }
                }
                if (previewMode.isPreviewActive()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ModAmber.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectPreviewMode(AdminPreviewMode.ADMIN) }
                            .testTag("admin_banner_restore_admin_button")
                    ) {
                        Text(
                            text = "Admin",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ModAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showQuickSwitcher) {
        AlertDialog(
            onDismissRequest = { showQuickSwitcher = false },
            title = { Text("Switch Admin Account View") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Drag the floating widget anywhere on screen. Closing it with X logs you out and right back in as Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AdminPreviewMode.entries.forEach { mode ->
                        val selected = mode == previewMode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectPreviewMode(mode)
                                    showQuickSwitcher = false
                                }
                                .testTag("quick_switch_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (hasCustomDisguise) {
                        OutlinedButton(
                            onClick = {
                                onResetIdentityDisguise()
                                showQuickSwitcher = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_reset_disguise_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Turn Off Custom Name / Color / Badge")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickSwitcher = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Full Admin Sandbox Card shown in Profile Settings & Safety Center for the real App Admin:
 * 1) Account View Switcher (Admin, Support Staff, Free, VOX Pro, VIP Pro)
 * 2) Custom Identity Disguise (any username, any hex color, any membership badge — toggle ON/OFF)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSandboxControlCard(
    realProfile: UserProfile?,
    previewMode: AdminPreviewMode,
    onSelectPreviewMode: (AdminPreviewMode) -> Unit,
    onSaveCustomIdentity: (customName: String, customColorHex: String, customMembershipTier: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val existingName = realProfile?.customDisplayName.orEmpty()
    val existingColor = realProfile?.customNameColorHex.orEmpty()
    val existingTier = realProfile?.customMembershipTier.orEmpty()
    val disguiseCurrentlyActive = realProfile?.hasCustomIdentityDisguise() == true

    var disguiseEnabled by remember(disguiseCurrentlyActive) { mutableStateOf(disguiseCurrentlyActive) }
    var draftName by remember(existingName) { mutableStateOf(existingName) }
    var draftColorHex by remember(existingColor) { mutableStateOf(existingColor.ifBlank { "#00C4FF" }) }
    var draftTier by remember(existingTier) { mutableStateOf(existingTier) }

    val previewColor = remember(draftColorHex, disguiseEnabled) {
        if (disguiseEnabled && Regex("^#[0-9A-Fa-f]{6}$").matches(draftColorHex.trim())) {
            parseHexColor(draftColorHex.trim())
        } else {
            LiveCoral
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_sandbox_control_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Admin Sandbox",
                    tint = ModAmber
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Admin Account Switcher & Custom Identity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Test Free, VOX Pro, VIP Pro, or Support Staff tools — or customize how your username appears.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Account View Switcher
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = VoiceCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Switch Account View",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AdminPreviewMode.entries.forEach { mode ->
                        val selected = previewMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPreviewMode(mode) },
                            label = {
                                Text(
                                    text = mode.shortLabel,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (mode) {
                                    AdminPreviewMode.ADMIN -> LiveCoral.copy(alpha = 0.2f)
                                    AdminPreviewMode.SUPPORT -> SafetyEmerald.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                            modifier = Modifier.testTag("admin_preview_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                if (previewMode == AdminPreviewMode.SUPPORT) {
                    Text(
                        text = "Support Staff Test Mode ON: Safety Center, Suspension Requests, Refund Reports, and 30/5d Perk Grant tools now behave exactly like Support Staff.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyEmerald
                    )
                } else if (previewMode.isPreviewActive()) {
                    Text(
                        text = "Previewing ${previewMode.label} experience (caps, badges, and limits match ${previewMode.shortLabel}). Tap Admin anytime to exit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoiceCyan
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Custom Identity Disguise (Name, Color, Membership)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = ModAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Custom Username, Color & Membership",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Appear as any name, color, and membership across VOX",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = disguiseEnabled,
                        onCheckedChange = { enabled ->
                            disguiseEnabled = enabled
                            if (!enabled) {
                                onSaveCustomIdentity("", "", "")
                            }
                        },
                        modifier = Modifier.testTag("admin_custom_identity_toggle")
                    )
                }

                if (disguiseEnabled) {
                    // Live Preview Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Live preview",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = draftName.trim().ifBlank { realProfile?.displayName ?: "Admin" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = previewColor
                            )
                            val badgeText = ADMIN_DISGUISE_MEMBERSHIPS.firstOrNull { it.first == draftTier }?.second
                                ?: "Admin"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = previewColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = badgeText.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = previewColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it.take(40) },
                        label = { Text("Custom Display Name") },
                        placeholder = { Text("Enter any name (leave blank to keep real name)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_custom_name_input")
                    )

                    Text(
                        text = "Username Color (pick swatch or type #RRGGBB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ADMIN_DISGUISE_SWATCHES.forEach { hex ->
                            val isChosen = draftColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (isChosen) 3.dp else 1.dp,
                                        color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { draftColorHex = hex }
                                    .testTag("admin_color_swatch_${hex.removePrefix("#").lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChosen) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected color",
                                        tint = if (hex == "#FFFFFF" || hex == "#FFD60A") Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draftColorHex,
                        onValueChange = { raw ->
                            val cleaned = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                            draftColorHex = cleaned
                        },
                        label = { Text("Custom Hex Color (#RRGGBB)") },
                        placeholder = { Text("#00C4FF") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_custom_color_input")
                    )

                    Text(
                        text = "Appears-As Membership / Role",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ADMIN_DISGUISE_MEMBERSHIPS.forEach { (code, label) ->
                            FilterChip(
                                selected = draftTier == code,
                                onClick = { draftTier = code },
                                label = { Text(label) },
                                modifier = Modifier.testTag("admin_disguise_tier_${code.ifBlank { "default" }.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                disguiseEnabled = false
                                draftName = ""
                                draftColorHex = ""
                                draftTier = ""
                                onSaveCustomIdentity("", "", "")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_reset_custom_identity_button")
                        ) {
                            Text("Reset to Normal")
                        }
                        Button(
                            onClick = {
                                val normalizedHex = draftColorHex.trim().let { raw ->
                                    if (raw.isBlank()) ""
                                    else if (raw.startsWith("#")) raw else "#$raw"
                                }
                                onSaveCustomIdentity(
                                    draftName.trim(),
                                    normalizedHex,
                                    draftTier
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ModAmber,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_apply_custom_identity_button")
                        ) {
                            Text("Apply Identity", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
