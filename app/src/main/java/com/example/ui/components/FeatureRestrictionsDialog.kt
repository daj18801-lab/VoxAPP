package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.RestrictableFeature
import com.example.data.UserProfile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

private data class SuspensionDurationOption(
    val label: String,
    val minutes: Long
)

private val SUSPENSION_DURATION_OPTIONS = listOf(
    SuspensionDurationOption("None", 0L),
    SuspensionDurationOption("15m", 15L),
    SuspensionDurationOption("1h", 60L),
    SuspensionDurationOption("6h", 360L),
    SuspensionDurationOption("24h", 1440L),
    SuspensionDurationOption("3d", 4320L),
    SuspensionDurationOption("7d", 10080L),
    SuspensionDurationOption("30d", 43200L)
)

/**
 * Granular 9-Feature Restrictions & Timed Suspensions dialog.
 *
 * - App Admin (`isAdmin == true`): Can permanently disable OR set a timed suspension
 *   for each of the 9 restrictable features independently.
 * - Support Staff (`isAdmin == false`): Can ONLY apply timed suspensions (x time) per feature;
 *   permanent disable controls are omitted and blocked in backend rules.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureRestrictionsDialog(
    targetName: String,
    targetProfile: UserProfile?,
    isLoadingProfile: Boolean,
    isAdmin: Boolean,
    onSave: (
        disabledFeatures: List<String>?,
        featureSuspensions: Map<String, Long>,
        reason: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val nowMs = remember { System.currentTimeMillis() }

    val permanentMap = remember(targetProfile?.userId, targetProfile?.disabledFeatures, targetProfile?.disableMessaging, targetProfile?.disableEnterRoom, targetProfile?.globallyBanned) {
        mutableStateMapOf<RestrictableFeature, Boolean>().apply {
            RestrictableFeature.entries.forEach { feature ->
                this[feature] = targetProfile?.isFeaturePermanentlyDisabled(feature) == true
            }
        }
    }

    // Stores expiry timestamp millis per feature (> nowMs means active timed suspension; 0L means none)
    val suspensionUntilMap = remember(targetProfile?.userId, targetProfile?.featureSuspensions) {
        mutableStateMapOf<RestrictableFeature, Long>().apply {
            RestrictableFeature.entries.forEach { feature ->
                val existing = targetProfile?.featureSuspendedUntilMillis(feature) ?: 0L
                this[feature] = if (existing > nowMs) existing else 0L
            }
        }
    }

    // Tracks which duration chip was tapped during this edit session (null = unchanged from existing)
    val selectedPresetMinutes = remember(targetProfile?.userId) {
        mutableStateMapOf<RestrictableFeature, Long?>()
    }

    var reasonDraft by remember(targetProfile?.userId, targetProfile?.suspensionReason) {
        mutableStateOf(targetProfile?.suspensionReason.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Schedule,
                contentDescription = "Feature Restrictions",
                tint = if (isAdmin) LiveCoral else ModAmber,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (isAdmin) "Feature Restrictions · $targetName" else "Timed Suspensions · $targetName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
        },
        text = {
            if (isLoadingProfile) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VoiceCyan)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading current restrictions…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAdmin) LiveCoral.copy(alpha = 0.10f) else ModAmber.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (isAdmin) LiveCoral.copy(alpha = 0.4f) else ModAmber.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAdmin) {
                                "App Admin Controls: Permanently disable OR set a timed suspension for any individual VOX feature below."
                            } else {
                                "Support Staff Controls: You can temporarily suspend any feature for a chosen duration. Permanent disables require an App Admin."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = reasonDraft,
                        onValueChange = { reasonDraft = it.take(240) },
                        label = { Text("Moderation Reason (Shown to user)") },
                        placeholder = { Text("e.g., Spamming rooms, PTT abuse, harassment…") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feature_restrictions_reason_input")
                    )

                    RestrictableFeature.entries.forEach { feature ->
                        val isPerm = permanentMap[feature] == true
                        val untilMs = suspensionUntilMap[feature] ?: 0L
                        val isTimed = untilMs > System.currentTimeMillis()
                        val chosenPreset = selectedPresetMinutes[feature]

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isPerm -> LiveCoral.copy(alpha = 0.65f)
                                    isTimed -> ModAmber.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feature_restriction_card_${feature.key}")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = feature.label,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val statusText = when {
                                            isPerm -> "PERMANENTLY DISABLED"
                                            isTimed -> {
                                                val minsLeft = ((untilMs - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
                                                val label = when {
                                                    minsLeft >= 1440L -> "${minsLeft / 1440L}d ${(minsLeft % 1440L) / 60L}h"
                                                    minsLeft >= 60L -> "${minsLeft / 60L}h ${minsLeft % 60L}m"
                                                    else -> "${minsLeft}m"
                                                }
                                                "SUSPENDED · $label remaining"
                                            }
                                            else -> "Active"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                isPerm -> LiveCoral
                                                isTimed -> ModAmber
                                                else -> SafetyEmerald
                                            }
                                        )
                                    }

                                    if (isAdmin) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Perm",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isPerm) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Switch(
                                                checked = isPerm,
                                                onCheckedChange = { checked ->
                                                    permanentMap[feature] = checked
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = LiveCoral
                                                ),
                                                modifier = Modifier.testTag("restrict_perm_${feature.key}")
                                            )
                                        }
                                    } else if (isPerm) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = LiveCoral.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "ADMIN LOCK",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = LiveCoral,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Timed suspension duration chips (available to both Admin and Support Staff)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SUSPENSION_DURATION_OPTIONS.forEach { option ->
                                        val isSelected = if (chosenPreset != null) {
                                            chosenPreset == option.minutes
                                        } else {
                                            option.minutes == 0L && !isTimed
                                        }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedPresetMinutes[feature] = option.minutes
                                                suspensionUntilMap[feature] = if (option.minutes <= 0L) {
                                                    0L
                                                } else {
                                                    System.currentTimeMillis() + (option.minutes * 60_000L)
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (option.minutes == 0L) SafetyEmerald else ModAmber,
                                                selectedLabelColor = if (option.minutes == 0L) Color.White else Color.Black
                                            ),
                                            modifier = Modifier.testTag("restrict_timer_${feature.key}_${option.minutes}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nowSave = System.currentTimeMillis()
                    val permList = if (isAdmin) {
                        RestrictableFeature.entries
                            .filter { permanentMap[it] == true }
                            .map { it.key }
                    } else {
                        null
                    }
                    val timedMap = buildMap<String, Long> {
                        RestrictableFeature.entries.forEach { feature ->
                            val until = suspensionUntilMap[feature] ?: 0L
                            if (until > nowSave) {
                                put(feature.key, until)
                            }
                        }
                    }
                    onSave(permList, timedMap, reasonDraft.trim())
                },
                enabled = !isLoadingProfile,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdmin) LiveCoral else ModAmber,
                    contentColor = if (isAdmin) Color.White else Color.Black
                ),
                modifier = Modifier.testTag("save_feature_restrictions_button")
            ) {
                Text(
                    text = if (isAdmin) "Save Restrictions" else "Save Timed Suspensions",
                    fontWeight = FontWeight.Bold
                )
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
 * Full-screen or tab placeholder displayed when a user tries to open a restricted feature tab
 * (e.g., Discovery Tab or Rooms Tab) or when Login is restricted.
 */
@Composable
fun FeatureRestrictedPlaceholder(
    featureTitle: String,
    notice: String,
    isPermanent: Boolean,
    testTagName: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .testTag(testTagName),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isPermanent) LiveCoral.copy(alpha = 0.14f) else ModAmber.copy(alpha = 0.16f),
            border = BorderStroke(
                1.5.dp,
                if (isPermanent) LiveCoral.copy(alpha = 0.55f) else ModAmber.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isPermanent) Icons.Rounded.Block else Icons.Rounded.Schedule,
                    contentDescription = featureTitle,
                    tint = if (isPermanent) LiveCoral else ModAmber,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = featureTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveCoral,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("${testTagName}_action_button")
                    ) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
