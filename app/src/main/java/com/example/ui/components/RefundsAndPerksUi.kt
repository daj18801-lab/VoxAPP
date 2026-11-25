package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AdminNotification
import com.example.data.AdminNotificationType
import com.example.data.AppPerkType
import com.example.data.CreatorSubscription
import com.example.data.DirectoryEntry
import com.example.data.PerkGrant
import com.example.data.PointTransaction
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.RefundReport
import com.example.data.RefundType
import com.example.data.SpotlightFrameColor
import com.example.data.StaffTier
import com.example.data.formatUsdCents
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun AdminNotificationsCard(
    notifications: List<AdminNotification>,
    onMarkRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadCount = remember(notifications) { notifications.count { !it.read } }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_notifications_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unreadCount > 0) {
                LiveCoral.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (unreadCount > 0) LiveCoral.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = "Admin Alerts",
                    tint = if (unreadCount > 0) LiveCoral else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Admin Record-Keeping & Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (unreadCount > 0) {
                    Surface(
                        color = LiveCoral,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "$unreadCount unread",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (notifications.isEmpty()) {
                Text(
                    text = "Support refund reports and Support 30-perk (5-day) threshold alerts will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notifications.take(8).forEach { notif ->
                    val isPerkAlert = notif.typeEnum() == AdminNotificationType.SUPPORT_PERK_THRESHOLD_30_IN_5D
                    val accent = if (isPerkAlert) LiveCoral else ModAmber
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (!notif.read) {
                            accent.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (!notif.read) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPerkAlert) Icons.Rounded.WarningAmber else Icons.Rounded.CurrencyExchange,
                                    contentDescription = notif.typeEnum().label,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = notif.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatTimestampShort(notif.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = notif.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!notif.read) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onMarkRead(notif.id) },
                                        modifier = Modifier.testTag("mark_admin_notif_read_${notif.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MarkEmailRead,
                                            contentDescription = "Mark Read",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mark Read")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RefundsManagementSection(
    isAdmin: Boolean,
    isSupport: Boolean,
    refundReports: List<RefundReport>,
    onOpenIssueRefund: () -> Unit,
    onAcknowledgeReport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("refunds_management_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CurrencyExchange,
                    contentDescription = "Refunds & Memberships",
                    tint = ModAmber
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Refunds & Membership Revocation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSupport && !isAdmin) {
                            "Refunding a membership unsubscribes the user and automatically sends a record report to App Admins."
                        } else {
                            "Admins & Support can issue refunds. Refunded memberships are automatically unsubscribed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onOpenIssueRefund,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_issue_refund_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ModAmber, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CurrencyExchange,
                    contentDescription = "Issue Refund",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Issue Refund", fontWeight = FontWeight.Bold)
            }

            if (refundReports.isEmpty()) {
                Text(
                    text = "No refunds have been issued yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider()
                Text(
                    text = "Refund Record Log (${refundReports.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                refundReports.take(10).forEach { report ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (report.reportedToAdmins && !report.acknowledgedByAdmin) {
                                ModAmber.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${report.targetName} · ${formatUsdCents(report.amountCents)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                StaffTierBadge(tier = report.issuedByTierEnum())
                            }
                            Text(
                                text = "${report.itemLabel} · Issued by ${report.issuedByName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Reason: ${report.reason}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (report.membershipRevoked) {
                                    Surface(
                                        color = LiveCoral.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = "Membership Unsubscribed",
                                            color = LiveCoral,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (report.reportedToAdmins) {
                                    Surface(
                                        color = if (report.acknowledgedByAdmin) {
                                            SafetyEmerald.copy(alpha = 0.14f)
                                        } else {
                                            ModAmber.copy(alpha = 0.16f)
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = if (report.acknowledgedByAdmin) {
                                                "Logged by ${report.acknowledgedByAdminName.ifBlank { "Admin" }}"
                                            } else {
                                                "Sent to Admins"
                                            },
                                            color = if (report.acknowledgedByAdmin) SafetyEmerald else ModAmber,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = formatTimestampShort(report.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdmin && report.reportedToAdmins && !report.acknowledgedByAdmin) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onAcknowledgeReport(report.id) },
                                        modifier = Modifier.testTag("ack_refund_report_${report.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Acknowledge Refund Report",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Acknowledge Record")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppPerkGrantsSection(
    perkGrants: List<PerkGrant>,
    onOpenGrantPerk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMs = rememberPresenceClockMillis()
    val support5dCount = remember(perkGrants, nowMs) {
        val cutoff = nowMs - PerkGrant.SUPPORT_ALERT_WINDOW_MS
        perkGrants.count { grant ->
            grant.grantedByTierEnum() == StaffTier.SUPPORT &&
                (grant.createdAtMillis >= cutoff ||
                    (grant.createdAt?.toDate()?.time ?: 0L) >= cutoff)
        }
    }
    val progress = (support5dCount.toFloat() / PerkGrant.SUPPORT_ALERT_THRESHOLD.toFloat()).coerceIn(0f, 1f)
    val thresholdReached = support5dCount >= PerkGrant.SUPPORT_ALERT_THRESHOLD

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_perk_grants_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (thresholdReached) LiveCoral.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Grant App Perks",
                    tint = VoiceCyan
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Grant App Perks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "App Admins & Support Staff can grant any VOX perk to a user.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Support 5-day perk counter (alert at 30 perks in 5 days)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (thresholdReached) LiveCoral.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (thresholdReached) LiveCoral.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Support 5-Day Perk Total",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$support5dCount / ${PerkGrant.SUPPORT_ALERT_THRESHOLD}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (thresholdReached) LiveCoral else VoiceCyan
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (thresholdReached) LiveCoral else VoiceCyan,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = if (thresholdReached) {
                            "Alert triggered: Support Staff reached $support5dCount perks in 5 days. App Admins have been notified."
                        } else {
                            "App Admins are automatically notified if Support Staff grant 30 perks in total within 5 days."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (thresholdReached) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onOpenGrantPerk,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_grant_perk_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Grant App Perk",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Grant Any App Perk", fontWeight = FontWeight.Bold)
            }

            if (perkGrants.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Recent Perk Grants (${perkGrants.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                perkGrants.take(8).forEach { grant ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${grant.perkLabel} → ${grant.targetName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                StaffTierBadge(tier = grant.grantedByTierEnum())
                            }
                            if (grant.perkDetail.isNotBlank()) {
                                Text(
                                    text = grant.perkDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VoiceCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "By ${grant.grantedByName}" +
                                        if (grant.reason.isNotBlank()) " · ${grant.reason}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = formatTimestampShort(grant.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IssueRefundDialog(
    directoryEntries: List<DirectoryEntry>,
    allTransactions: List<PointTransaction>,
    allCreatorSubscriptions: List<CreatorSubscription>,
    isSupportOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirmRefund: (
        targetUserId: String,
        targetName: String,
        refundType: RefundType,
        itemLabel: String,
        amountCents: Long,
        transactionId: String,
        subscriptionId: String,
        reason: String
    ) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<DirectoryEntry?>(null) }
    var selectedType by remember { mutableStateOf(RefundType.VOX_PRO) }
    var selectedSub by remember { mutableStateOf<CreatorSubscription?>(null) }
    var selectedTx by remember { mutableStateOf<PointTransaction?>(null) }
    var amountDollarsInput by remember { mutableStateOf("9.99") }
    var reasonInput by remember { mutableStateOf("") }

    val filteredUsers = remember(directoryEntries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        directoryEntries
            .filter { q.isEmpty() || it.displayName.lowercase().contains(q) || it.handle.lowercase().contains(q) }
            .take(25)
    }
    val userSubs = remember(allCreatorSubscriptions, selectedUser) {
        val uid = selectedUser?.userId ?: return@remember emptyList()
        allCreatorSubscriptions.filter { it.subscriberId == uid && it.isCurrentlyActive() }
    }
    val userTransactions = remember(allTransactions, selectedUser) {
        val uid = selectedUser?.userId ?: return@remember emptyList()
        allTransactions.filter { it.buyerId == uid && !it.refunded && it.grossCents > 0L }.take(10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Refund", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ModAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = buildString {
                            append("Refunding a membership immediately unsubscribes the user.")
                            if (isSupportOnly) {
                                append(" A refund report will automatically be sent to App Admins for record keeping.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // 1. User picker
                Text("1. Select User", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (selectedUser == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search member name or @handle...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("refund_user_search_input")
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredUsers.forEach { entry ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUser = entry },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (entry.proMember || entry.proLifetime) {
                                        Text(
                                            text = "PRO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VoiceCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedUser!!.displayName} (@${selectedUser!!.handle})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                selectedUser = null
                                selectedSub = null
                                selectedTx = null
                            }) {
                                Text("Change")
                            }
                        }
                    }
                }

                // 2. Refund type
                Text("2. Refund Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RefundType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                selectedSub = null
                                selectedTx = null
                                amountDollarsInput = when (type) {
                                    RefundType.VOX_PRO -> "9.99"
                                    RefundType.CREATOR_SUBSCRIPTION -> "15.00"
                                    RefundType.PAID_DM -> "5.00"
                                    RefundType.POINTS_TRANSACTION -> "5.00"
                                }
                            },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("refund_type_${type.name}")
                        )
                    }
                }

                if (selectedType == RefundType.CREATOR_SUBSCRIPTION && userSubs.isNotEmpty()) {
                    Text(
                        text = "Active Creator Subscriptions (tap to select):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    userSubs.forEach { sub ->
                        val picked = selectedSub?.id == sub.id
                        FilterChip(
                            selected = picked,
                            onClick = {
                                selectedSub = sub
                                amountDollarsInput = String.format("%.2f", sub.priceCents / 100.0)
                            },
                            label = { Text("${sub.creatorName} (${formatUsdCents(sub.priceCents)})") }
                        )
                    }
                }

                if (userTransactions.isNotEmpty()) {
                    Text(
                        text = "Optional: Link Recent Purchase",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        userTransactions.forEach { tx ->
                            FilterChip(
                                selected = selectedTx?.id == tx.id,
                                onClick = {
                                    selectedTx = if (selectedTx?.id == tx.id) null else tx
                                    if (selectedTx != null) {
                                        amountDollarsInput = String.format("%.2f", tx.grossCents / 100.0)
                                    }
                                },
                                label = { Text("${tx.typeEnum().label} (${formatUsdCents(tx.grossCents)})") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountDollarsInput,
                    onValueChange = { raw ->
                        if (raw.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amountDollarsInput = raw
                    },
                    label = { Text("Refund Amount (USD)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refund_amount_input")
                )

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it.take(300) },
                    label = { Text("Refund Reason (required)") },
                    placeholder = { Text("e.g., Accidental renewal or billing support request") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refund_reason_input")
                )
            }
        },
        confirmButton = {
            val target = selectedUser
            val cents = ((amountDollarsInput.toDoubleOrNull() ?: 0.0) * 100.0).toLong().coerceAtLeast(0L)
            Button(
                onClick = {
                    val user = target ?: return@Button
                    val label = when {
                        selectedSub != null -> "Creator Sub: ${selectedSub!!.creatorName}"
                        selectedTx != null -> selectedTx!!.typeEnum().label
                        else -> selectedType.label
                    }
                    onConfirmRefund(
                        user.userId,
                        user.displayName,
                        selectedType,
                        label,
                        cents,
                        selectedTx?.id.orEmpty(),
                        selectedSub?.id.orEmpty(),
                        reasonInput.trim()
                    )
                },
                enabled = target != null && reasonInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ModAmber, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_issue_refund_button")
            ) {
                Text("Confirm Refund")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GrantAppPerkDialog(
    directoryEntries: List<DirectoryEntry>,
    onDismiss: () -> Unit,
    onConfirmGrant: (
        targetUserId: String,
        targetName: String,
        perkType: AppPerkType,
        detailValue: String,
        pointsAmount: Long,
        reason: String
    ) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<DirectoryEntry?>(null) }
    var selectedPerk by remember { mutableStateOf(AppPerkType.VOX_PRO_30D) }
    var selectedSpotlightColor by remember { mutableStateOf(SpotlightFrameColor.ELECTRIC_CYAN) }
    var selectedPttTheme by remember { mutableStateOf(PttThemeStyle.NEON_MAGENTA) }
    var selectedPttChirp by remember { mutableStateOf(PttChirpStyle.ROGER_BEEP) }
    var pointsInput by remember { mutableStateOf("10") }
    var reasonInput by remember { mutableStateOf("") }

    val filteredUsers = remember(directoryEntries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        directoryEntries
            .filter { q.isEmpty() || it.displayName.lowercase().contains(q) || it.handle.lowercase().contains(q) }
            .take(25)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant App Perk", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. Select User", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (selectedUser == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search member name or @handle...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("perk_user_search_input")
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredUsers.forEach { entry ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUser = entry },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "@${entry.handle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedUser!!.displayName} (@${selectedUser!!.handle})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { selectedUser = null }) { Text("Change") }
                        }
                    }
                }

                Text("2. Select Perk", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppPerkType.entries.forEach { perk ->
                        val picked = selectedPerk == perk
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPerk = perk }
                                .testTag("perk_option_${perk.name}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (picked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = perk.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = perk.shortDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Dynamic perk options
                when (selectedPerk) {
                    AppPerkType.SPOTLIGHT_FRAME_24H -> {
                        Text("Spotlight Frame Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SpotlightFrameColor.entries.forEach { color ->
                                FilterChip(
                                    selected = selectedSpotlightColor == color,
                                    onClick = { selectedSpotlightColor = color },
                                    label = { Text(color.label) }
                                )
                            }
                        }
                    }
                    AppPerkType.PTT_THEME -> {
                        Text("PTT Floating Button Theme", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PttThemeStyle.entries.filter { it != PttThemeStyle.CLASSIC }.forEach { theme ->
                                FilterChip(
                                    selected = selectedPttTheme == theme,
                                    onClick = { selectedPttTheme = theme },
                                    label = { Text(theme.label) }
                                )
                            }
                        }
                    }
                    AppPerkType.PTT_CHIRP -> {
                        Text("Walkie-Talkie Chirp Sound", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PttChirpStyle.entries.filter { it != PttChirpStyle.STANDARD }.forEach { chirp ->
                                FilterChip(
                                    selected = selectedPttChirp == chirp,
                                    onClick = { selectedPttChirp = chirp },
                                    label = { Text(chirp.label) }
                                )
                            }
                        }
                    }
                    AppPerkType.BONUS_POINTS -> {
                        OutlinedTextField(
                            value = pointsInput,
                            onValueChange = { raw ->
                                if (raw.all { it.isDigit() } && raw.length <= 5) pointsInput = raw
                            },
                            label = { Text("Bonus Points to Add") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("perk_bonus_points_input")
                        )
                    }
                    else -> Unit
                }

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it.take(300) },
                    label = { Text("Note / Reason (optional)") },
                    placeholder = { Text("e.g., Community giveaway or support goodwill") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("perk_reason_input")
                )
            }
        },
        confirmButton = {
            val target = selectedUser
            val detailValue = when (selectedPerk) {
                AppPerkType.SPOTLIGHT_FRAME_24H -> selectedSpotlightColor.primaryHex
                AppPerkType.PTT_THEME -> selectedPttTheme.id
                AppPerkType.PTT_CHIRP -> selectedPttChirp.id
                else -> ""
            }
            val pts = pointsInput.toLongOrNull()?.coerceIn(1L, 10_000L) ?: 5L
            Button(
                onClick = {
                    val user = target ?: return@Button
                    onConfirmGrant(
                        user.userId,
                        user.displayName,
                        selectedPerk,
                        detailValue,
                        pts,
                        reasonInput.trim()
                    )
                },
                enabled = target != null,
                modifier = Modifier.testTag("confirm_grant_perk_button")
            ) {
                Text("Grant Perk")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
