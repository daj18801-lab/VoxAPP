package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BannedDevice
import com.example.data.SafetyReport
import com.example.data.UserModerationFile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue

/**
 * Warning confirmation dialog (Yes / No) shown when a user taps "Delete Account" under Profile.
 */
@Composable
fun DeleteAccountConfirmDialog(
    hasActiveMembership: Boolean,
    isDeleting: Boolean,
    onConfirmYes: () -> Unit,
    onDismissNo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismissNo() },
        modifier = Modifier.testTag("delete_account_confirm_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Delete account warning",
                tint = LiveCoral,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Delete Your Account?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = LiveCoral
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Are you sure you want to permanently delete your account from VOX? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasActiveMembership) {
                        "• Your active VOX membership and any creator subscriptions will be stopped immediately (cancel Google Play auto-renewal in Play Store if billed there)."
                    } else {
                        "• Any memberships and creator subscriptions tied to your account will be stopped immediately."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Your profile and directory listing will be deleted from the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• If any safety reports were filed against your account, those reports will remain on record under Deleted Accounts/Reports along with your username and device ID for safety review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ModAmber
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmYes,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCoral,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_account_confirm_yes_button")
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deleting…")
                } else {
                    Text("Yes, Delete")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissNo,
                enabled = !isDeleting,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_account_confirm_no_button")
            ) {
                Text("No, Keep Account")
            }
        }
    )
}

/**
 * Full-screen lockout overlay shown when a user signs in under a new/different account
 * on a device that has prior safety reports on record. Prevents all app actions until
 * Staff/Admin reviews and grants access, disables specific features, or bans the user.
 */
@Composable
fun ReturningReportedDeviceLockOverlay(
    deviceRecord: BannedDevice,
    onSignOut: () -> Unit
) {
    val shortDevice = deviceRecord.deviceHash.take(12).ifBlank { "recorded" }
    val priorReports = deviceRecord.priorReportCount.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .padding(24.dp)
            .testTag("returning_reported_device_lock_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, ModAmber.copy(alpha = 0.65f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ModAmber.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GppMaybe,
                        contentDescription = "Account flagged for staff review",
                        tint = ModAmber,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Account Flagged for Staff Review",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This device ($shortDevice…) has $priorReports prior safety report${if (priorReports == 1) "" else "s"} on record from a previous or deleted account. All app features are locked while Support Staff and Admins review your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ModAmber.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = ModAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Staff have been automatically notified. Once reviewed, they may grant access, restrict specific features, or permanently ban the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("flagged_device_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

/**
 * Staff & Admin review queue for returning users who signed in under a different name
 * on a device with prior safety reports. Staff can Grant Access, Disable App Features, or Ban.
 */
@Composable
fun FlaggedReturningDevicesCard(
    deviceRecords: List<BannedDevice>,
    onGrantAccess: (device: BannedDevice) -> Unit,
    onDisableAppFeatures: (device: BannedDevice) -> Unit,
    onBanUser: (device: BannedDevice, reason: String) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    onLiftDeviceSuspension: ((device: BannedDevice) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pendingDevices = remember(deviceRecords) {
        deviceRecords.filter { it.isPendingStaffReview() }
    }
    val bounceSuspendedDevices = remember(deviceRecords, nowMillis) {
        deviceRecords.filter { it.isTimedSuspended(nowMillis) }
    }
    var banTargetDevice by remember { mutableStateOf<BannedDevice?>(null) }
    var banReasonDraft by remember { mutableStateOf("") }

    if (pendingDevices.isEmpty() && bounceSuspendedDevices.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, ModAmber.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("staff_flagged_returning_devices_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GppMaybe,
                    contentDescription = "Flagged returning devices",
                    tint = ModAmber,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Flagged & Suspended Devices (${pendingDevices.size + bounceSuspendedDevices.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (bounceSuspendedDevices.isNotEmpty()) LiveCoral else ModAmber
                    )
                    Text(
                        text = "Returning reported devices & 5-day bounce-evasion device suspensions — Staff can review or lift bans",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            bounceSuspendedDevices.forEach { device ->
                val shortDev = device.deviceHash.take(14)
                val userName = device.returningUserName.ifBlank {
                    device.bannedUserName.ifBlank { "Suspended User" }
                }
                val remaining = device.remainingSuspensionLabel(nowMillis).ifBlank { "Active" }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LiveCoral.copy(alpha = 0.08f),
                    border = BorderStroke(1.2.dp, LiveCoral.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bounce_evasion_device_row_${device.deviceHash.take(12)}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$userName · Bounce Evasion",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Device ID: $shortDev… · $remaining remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LiveCoral
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = LiveCoral.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "5-DAY BAN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = LiveCoral,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (device.reason.isNotBlank()) {
                            Text(
                                text = device.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onLiftDeviceSuspension != null) {
                            Button(
                                onClick = { onLiftDeviceSuspension(device) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyEmerald,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("staff_lift_device_suspension_${device.deviceHash.take(12)}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Lift 5-day device suspension",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lift 5-Day Device Ban",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            pendingDevices.forEach { device ->
                val shortDev = device.deviceHash.take(14)
                val returningName = device.returningUserName.ifBlank { "Returning User" }
                val priorName = device.bannedUserName.ifBlank { "Deleted/Prior Account" }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ModAmber.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("flagged_device_row_${device.deviceHash.take(12)}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Returning as: $returningName",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Prior account: $priorName · ${device.priorReportCount.coerceAtLeast(1)} report(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LiveCoral
                                )
                                Text(
                                    text = "Device ID: $shortDev…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ModAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "LOCKED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = ModAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (device.reason.isNotBlank()) {
                            Text(
                                text = device.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onGrantAccess(device) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyEmerald,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("staff_grant_flagged_device_${device.deviceHash.take(12)}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Grant access",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Grant", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { onDisableAppFeatures(device) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, SnapBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SnapBlue),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                                    .testTag("staff_restrict_flagged_device_${device.deviceHash.take(12)}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Disable app features",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Features", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    banTargetDevice = device
                                    banReasonDraft = "Banned after returning on reported device (prior account: $priorName)"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiveCoral,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("staff_ban_flagged_device_${device.deviceHash.take(12)}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = "Ban user",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ban", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    banTargetDevice?.let { target ->
        AlertDialog(
            onDismissRequest = { banTargetDevice = null },
            modifier = Modifier.testTag("confirm_ban_flagged_device_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = LiveCoral,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Ban Returning User & Device?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Permanently ban ${target.returningUserName.ifBlank { "this user" }} and lock device ${target.deviceHash.take(14)}… from accessing VOX.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = banReasonDraft,
                        onValueChange = { banReasonDraft = it.take(240) },
                        label = { Text("Ban reason") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ban_flagged_device_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBanUser(target, banReasonDraft.trim())
                        banTargetDevice = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_ban_flagged_device_button")
                ) {
                    Text("Ban User")
                }
            },
            dismissButton = {
                TextButton(onClick = { banTargetDevice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dedicated "Deleted Accounts / Reports" section in Safety Center showing users who deleted
 * their account while having safety reports on file (preserving username, device ID, and reports).
 * Admins can resolve each file by either Keeping it on record or Permanently Deleting it.
 */
@Composable
fun DeletedAccountsReportsCard(
    moderationFiles: List<UserModerationFile>,
    allReports: List<SafetyReport>,
    isAdmin: Boolean,
    onInspectFile: (UserModerationFile) -> Unit,
    onKeepFile: (UserModerationFile) -> Unit,
    onDeleteFile: (UserModerationFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val deletedFiles = remember(moderationFiles) {
        moderationFiles
            .filter { it.deletedAccount }
            .sortedByDescending { it.deletedAtMillis }
    }
    var confirmDeleteFile by remember { mutableStateOf<UserModerationFile?>(null) }

    if (deletedFiles.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("deleted_accounts_reports_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderDelete,
                    contentDescription = "Deleted accounts with reports",
                    tint = LiveCoral,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deleted Accounts / Reports (${deletedFiles.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = LiveCoral
                    )
                    Text(
                        text = "Retained reports, former usernames & device IDs — Admin can Keep or Delete each file",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            deletedFiles.forEach { file ->
                val linkedReports = remember(allReports, file.userId) {
                    allReports.filter { it.targetUserId == file.userId }
                }
                val isKept = file.deletedFileDecision.equals("KEPT", ignoreCase = true)
                val badgeColor = if (isKept) SafetyEmerald else ModAmber
                val badgeText = if (isKept) "KEPT ON FILE" else "NEEDS ADMIN DECISION"

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deleted_account_file_${file.userId}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buildString {
                                        append(file.targetName.ifBlank { file.userId.take(12) })
                                        if (file.deletedHandle.isNotBlank()) {
                                            append(" (@${file.deletedHandle})")
                                        }
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Deleted Account · Reports: ${maxOf(file.reportCount, linkedReports.size)} · Warnings: ${file.warningCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (file.deletedDeviceHash.isNotBlank()) {
                                    Text(
                                        text = "Device ID: ${file.deletedDeviceHash.take(18)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SnapBlue
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (linkedReports.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            linkedReports.take(3).forEach { rep ->
                                Text(
                                    text = "• [${rep.categoryEnum().label}] ${rep.details.ifBlank { "No details" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onInspectFile(file) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("deleted_file_notes_${file.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = "Inspect file notes",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Notes", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }

                            if (isAdmin) {
                                if (!isKept) {
                                    Button(
                                        onClick = { onKeepFile(file) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SafetyEmerald,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("admin_keep_deleted_file_${file.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Inventory2,
                                            contentDescription = "Keep file",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Keep", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                }

                                Button(
                                    onClick = { confirmDeleteFile = file },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LiveCoral,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("admin_delete_deleted_file_${file.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = "Delete file",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteFile?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = null },
            modifier = Modifier.testTag("admin_confirm_delete_file_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = LiveCoral,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Delete Retained Report File?") },
            text = {
                Text(
                    text = "Permanently delete ${file.targetName.ifBlank { "this user" }}'s deleted-account moderation file, all retained safety reports, and any non-ban device flag? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(file)
                        confirmDeleteFile = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("admin_confirm_delete_file_yes")
                ) {
                    Text("Delete File")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
