package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AlertSeverity
import com.example.data.AppConfig
import com.example.data.ChatRoom
import com.example.data.ReportStatus
import com.example.data.SafetyReport
import com.example.data.StaffCapabilities
import com.example.data.StaffInvite
import com.example.data.StaffRole
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun SafetyScreen(
    reportsState: UiState<List<SafetyReport>>,
    userProfile: UserProfile?,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    staffRoles: List<StaffRole>,
    staffInvites: List<StaffInvite>,
    allUsers: List<UserProfile>,
    allAlerts: List<UserAlert>,
    appConfig: AppConfig = AppConfig(),
    liveRooms: List<ChatRoom> = emptyList(),
    onResolveReport: (reportId: String, status: ReportStatus, note: String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onAddStaffInvite: (email: String, displayName: String, staffTier: StaffTier, canEnterInvisible: Boolean) -> Unit,
    onToggleSupportInviteInvisible: (inviteId: String, allow: Boolean) -> Unit,
    onRemoveSupportInvite: (inviteId: String) -> Unit,
    onToggleSupportRoleInvisible: (targetUserId: String, displayName: String, allow: Boolean) -> Unit,
    onSetUserStaffTier: (targetUserId: String, targetName: String, tier: StaffTier, canEnterInvisible: Boolean) -> Unit,
    onAdminEditUser: (
        targetUserId: String,
        displayName: String,
        handle: String,
        bio: String,
        globallyBanned: Boolean,
        disableCreateRoom: Boolean,
        disableEnterRoom: Boolean,
        disableMic: Boolean,
        disableVideo: Boolean,
        suspendedUntilMillis: Long,
        suspensionReason: String
    ) -> Unit,
    onSuspendUser: (targetUserId: String, targetName: String, minutes: Long, reason: String) -> Unit,
    onSendDirectAlert: (targetUserId: String, targetName: String, title: String, message: String, severity: AlertSeverity) -> Unit,
    onAdminEditAlert: (alertId: String, title: String, message: String, severity: AlertSeverity, reopenForUser: Boolean) -> Unit,
    onAdminDeleteAlert: (alertId: String) -> Unit,
    onUpdateAppConfig: (bannerTitle: String, bannerMessage: String, bannerEnabled: Boolean, trialLoginsEnabled: Boolean) -> Unit = { _, _, _, _ -> },
    onSendUserToRoom: (targetUserId: String, targetName: String, targetRoom: ChatRoom, fromRoomId: String, reason: String) -> Unit = { _, _, _, _, _ -> }
) {
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val openCount = reports.count { it.statusEnum() == ReportStatus.OPEN }
    val resolvedCount = reports.count { it.statusEnum() != ReportStatus.OPEN }
    val blockedUsers = userProfile?.blockedUserIds.orEmpty()

    val staffTierByUid = remember(staffRoles) {
        staffRoles.associate { it.userId to it.tierEnum() }
    }

    var adminConsoleTab by remember { mutableIntStateOf(0) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    var inviteTier by remember { mutableStateOf(StaffTier.SUPPORT) }
    var inviteAllowInvisible by remember { mutableStateOf(false) }
    var userSearch by remember { mutableStateOf("") }

    // App Banner & Trial Login Editor State (synced with Firestore AppConfig)
    var bannerTitleDraft by remember(appConfig.bannerTitle) { mutableStateOf(appConfig.bannerTitle) }
    var bannerMessageDraft by remember(appConfig.bannerMessage) { mutableStateOf(appConfig.bannerMessage) }
    var bannerEnabledDraft by remember(appConfig.bannerEnabled) { mutableStateOf(appConfig.bannerEnabled) }
    var trialLoginsEnabledDraft by remember(appConfig.trialLoginsEnabled) { mutableStateOf(appConfig.trialLoginsEnabled) }

    var userBeingEdited by remember { mutableStateOf<UserProfile?>(null) }
    var userBeingSuspended by remember { mutableStateOf<UserProfile?>(null) }
    var userReceivingAlert by remember { mutableStateOf<UserProfile?>(null) }
    var userBeingTransferred by remember { mutableStateOf<UserProfile?>(null) }
    var alertBeingEdited by remember { mutableStateOf<UserAlert?>(null) }

    val filteredUsers = remember(allUsers, userSearch) {
        val q = userSearch.trim()
        if (q.isEmpty()) allUsers
        else allUsers.filter {
            it.displayName.contains(q, ignoreCase = true) || it.handle.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (staffCapabilities.isAdmin) "Admin & Safety Command" else "Safety Center",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        StaffTierBadge(tier = staffCapabilities.tier)
                    }
                    Text(
                        text = if (staffCapabilities.isAdmin) {
                            "Manage Support Staff, user permissions, alerts, and safety reports"
                        } else {
                            "Incident reports, community shield & personal blocklist"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Full Admin Command Console (only visible to Main App Owner Administrator)
            if (staffCapabilities.isAdmin) {
                item(key = "admin_console_header") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = usernameColor(StaffTier.ADMIN).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, usernameColor(StaffTier.ADMIN).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    contentDescription = "Admin console",
                                    tint = usernameColor(StaffTier.ADMIN),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "App Owner Admin Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = usernameColor(StaffTier.ADMIN)
                                )
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = adminConsoleTab == 0,
                                    onClick = { adminConsoleTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                                    modifier = Modifier.testTag("admin_tab_support")
                                ) {
                                    Text("Staff")
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 1,
                                    onClick = { adminConsoleTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                                    modifier = Modifier.testTag("admin_tab_users")
                                ) {
                                    Text("Users")
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 2,
                                    onClick = { adminConsoleTab = 2 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                                    modifier = Modifier.testTag("admin_tab_alerts")
                                ) {
                                    Text("Alerts")
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 3,
                                    onClick = { adminConsoleTab = 3 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                                    modifier = Modifier.testTag("admin_tab_app_config")
                                ) {
                                    Text("App")
                                }
                            }
                        }
                    }
                }

                when (adminConsoleTab) {
                    0 -> {
                        item(key = "add_support_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
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
                                            imageVector = if (inviteTier == StaffTier.ADMIN) {
                                                Icons.Rounded.AdminPanelSettings
                                            } else {
                                                Icons.Rounded.SupportAgent
                                            },
                                            contentDescription = "Add Staff",
                                            tint = usernameColor(inviteTier)
                                        )
                                        Text(
                                            text = "Invite App Admin or Support by Email",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = inviteTier == StaffTier.SUPPORT,
                                            onClick = { inviteTier = StaffTier.SUPPORT },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                            modifier = Modifier.testTag("invite_tier_support")
                                        ) {
                                            Text("Support Staff")
                                        }
                                        SegmentedButton(
                                            selected = inviteTier == StaffTier.ADMIN,
                                            onClick = { inviteTier = StaffTier.ADMIN },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                            modifier = Modifier.testTag("invite_tier_admin")
                                        ) {
                                            Text("App Admin")
                                        }
                                    }

                                    OutlinedTextField(
                                        value = inviteEmail,
                                        onValueChange = { inviteEmail = it.take(120) },
                                        label = { Text("Google Email Address") },
                                        placeholder = { Text("member@gmail.com") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("support_invite_email_input")
                                    )
                                    OutlinedTextField(
                                        value = inviteName,
                                        onValueChange = { inviteName = it.take(40) },
                                        label = { Text("Display Name (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("support_invite_name_input")
                                    )
                                    if (inviteTier == StaffTier.SUPPORT) {
                                        ToggleSettingRow(
                                            title = "Allow Invisible Room Entry",
                                            subtitle = "Let this Support member enter rooms invisibly (must be visible to use mic)",
                                            checked = inviteAllowInvisible,
                                            onCheckedChange = { inviteAllowInvisible = it }
                                        )
                                    } else {
                                        Text(
                                            text = "App Admins get full console control, user renaming, room editing, invisible entry, and exclusive mic priority.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = usernameColor(StaffTier.ADMIN)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (inviteEmail.contains("@")) {
                                                onAddStaffInvite(
                                                    inviteEmail.trim(),
                                                    inviteName.trim(),
                                                    inviteTier,
                                                    inviteTier == StaffTier.ADMIN || inviteAllowInvisible
                                                )
                                                inviteEmail = ""
                                                inviteName = ""
                                                inviteAllowInvisible = false
                                            }
                                        },
                                        enabled = inviteEmail.contains("@"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = usernameColor(inviteTier),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("add_support_invite_button")
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (inviteTier == StaffTier.ADMIN) "Add App Admin" else "Add Support Staff"
                                        )
                                    }
                                }
                            }
                        }

                        val activeAdmins = staffRoles.filter { it.tierEnum() == StaffTier.ADMIN }
                        if (activeAdmins.isNotEmpty()) {
                            item(key = "active_admins_label") {
                                Text(
                                    text = "App Administrators (${activeAdmins.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = usernameColor(StaffTier.ADMIN)
                                )
                            }
                            items(activeAdmins, key = { "admin_role_${it.userId}" }) { role ->
                                val isOwnerAccount = role.email.equals("daj18801@gmail.com", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, usernameColor(StaffTier.ADMIN).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = role.displayName.ifBlank { "App Admin" },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = usernameColor(StaffTier.ADMIN)
                                                )
                                                StaffTierBadge(tier = StaffTier.ADMIN)
                                            }
                                            if (role.email.isNotBlank()) {
                                                Text(
                                                    text = if (isOwnerAccount) "${role.email} · Primary Owner" else role.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (!isOwnerAccount && role.userId != currentUserId) {
                                            IconButton(
                                                onClick = {
                                                    onSetUserStaffTier(role.userId, role.displayName, StaffTier.USER, false)
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Remove App Admin",
                                                    tint = LiveCoral
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val activeSupport = staffRoles.filter { it.tierEnum() == StaffTier.SUPPORT }
                        if (activeSupport.isNotEmpty()) {
                            item(key = "active_support_label") {
                                Text(
                                    text = "Active Support Staff (${activeSupport.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = usernameColor(StaffTier.SUPPORT)
                                )
                            }
                            items(activeSupport, key = { "role_${it.userId}" }) { role ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = role.displayName.ifBlank { "Support Member" },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = usernameColor(StaffTier.SUPPORT)
                                                    )
                                                    StaffTierBadge(tier = StaffTier.SUPPORT)
                                                }
                                                if (role.email.isNotBlank()) {
                                                    Text(
                                                        text = role.email,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onSetUserStaffTier(role.userId, role.displayName, StaffTier.ADMIN, true)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Make Admin", style = MaterialTheme.typography.labelSmall, color = usernameColor(StaffTier.ADMIN))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onSetUserStaffTier(role.userId, role.displayName, StaffTier.USER, false)
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Delete,
                                                        contentDescription = "Remove support staff",
                                                        tint = LiveCoral
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Allow Invisible Room Entry",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Switch(
                                                checked = role.canEnterInvisible,
                                                onCheckedChange = { allow ->
                                                    onToggleSupportRoleInvisible(role.userId, role.displayName, allow)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (staffInvites.isNotEmpty()) {
                            item(key = "pending_invites_label") {
                                Text(
                                    text = "Pending Staff Invites (${staffInvites.size})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            items(staffInvites, key = { "invite_${it.id}" }) { invite ->
                                val invTier = invite.tierEnum()
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = invite.displayName.ifBlank { invite.email },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = usernameColor(invTier)
                                                    )
                                                    StaffTierBadge(tier = invTier)
                                                }
                                                Text(
                                                    text = invite.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveSupportInvite(invite.id) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Remove invite",
                                                    tint = LiveCoral
                                                )
                                            }
                                        }
                                        if (invTier == StaffTier.SUPPORT) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Allow Invisible Entry on Sign-In",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Switch(
                                                    checked = invite.canEnterInvisible,
                                                    onCheckedChange = { allow ->
                                                        onToggleSupportInviteInvisible(invite.id, allow)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        item(key = "user_search") {
                            OutlinedTextField(
                                value = userSearch,
                                onValueChange = { userSearch = it.take(40) },
                                label = { Text("Search Users") },
                                placeholder = { Text("Filter by display name or handle…") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_user_search_input")
                            )
                        }

                        items(filteredUsers, key = { "user_${it.userId}" }) { user ->
                            val userTier = if (user.userId == currentUserId) {
                                StaffTier.ADMIN
                            } else {
                                staffTierByUid[user.userId] ?: StaffTier.USER
                            }
                            AdminUserDirectoryCard(
                                profile = user,
                                tier = userTier,
                                isSelf = user.userId == currentUserId,
                                onEdit = { userBeingEdited = user },
                                onSuspend = { userBeingSuspended = user },
                                onAlert = { userReceivingAlert = user },
                                onSendToRoom = { userBeingTransferred = user },
                                onSetStaffTier = { nextTier ->
                                    onSetUserStaffTier(
                                        user.userId,
                                        user.displayName,
                                        nextTier,
                                        nextTier == StaffTier.ADMIN
                                    )
                                }
                            )
                        }
                    }

                    2 -> {
                        if (allAlerts.isEmpty()) {
                            item(key = "alerts_empty") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No alerts sent yet. Send alerts from any room or the Users tab above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(allAlerts, key = { "alert_${it.id}" }) { alert ->
                                AdminAlertHistoryCard(
                                    alert = alert,
                                    onEdit = { alertBeingEdited = alert },
                                    onDelete = { onAdminDeleteAlert(alert.id) }
                                )
                            }
                        }
                    }

                    3 -> {
                        item(key = "admin_app_banner_and_trial_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Campaign,
                                            contentDescription = "Login news banner",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Login Screen News Banner & Trial Access",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Show News & Updates Banner on Login",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Broadcast app news, maintenance notices, or updates on the main login screen",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = bannerEnabledDraft,
                                            onCheckedChange = { bannerEnabledDraft = it },
                                            modifier = Modifier.testTag("admin_toggle_login_banner")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = bannerTitleDraft,
                                        onValueChange = { bannerTitleDraft = it.take(80) },
                                        label = { Text("Banner Headline") },
                                        placeholder = { Text("e.g., Welcome to VoxStage v2!") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_banner_title_input")
                                    )

                                    OutlinedTextField(
                                        value = bannerMessageDraft,
                                        onValueChange = { bannerMessageDraft = it.take(400) },
                                        label = { Text("Updates / News Message") },
                                        placeholder = { Text("Write app news or feature updates shown on the login screen…") },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_banner_message_input")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Allow 1-Day Trial Logins",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "When disabled, guest trial logins are hidden and active trial sessions require Google Sign-In.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = trialLoginsEnabledDraft,
                                            onCheckedChange = { trialLoginsEnabledDraft = it },
                                            modifier = Modifier.testTag("admin_toggle_trial_logins")
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onUpdateAppConfig(
                                                bannerTitleDraft,
                                                bannerMessageDraft,
                                                bannerEnabledDraft,
                                                trialLoginsEnabledDraft
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("admin_save_banner_button")
                                    ) {
                                        Text("Save Login Banner & Trial Settings")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "stats_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SafetyMetricCard(
                        label = "Open Reports",
                        value = "$openCount",
                        accent = if (openCount > 0) ModAmber else SafetyEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    SafetyMetricCard(
                        label = "Resolved",
                        value = "$resolvedCount",
                        accent = VoiceCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SafetyMetricCard(
                        label = "Blocked",
                        value = "${blockedUsers.size}",
                        accent = LiveCoral,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "guidelines_banner") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SafetyEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SafetyEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = "Active protection",
                                tint = SafetyEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Role-Coded Moderation Hierarchy",
                                style = MaterialTheme.typography.titleSmall,
                                color = SafetyEmerald
                            )
                            Text(
                                text = "Admins (Red) · Support Staff (Green) · Members (Blue). Room Creators act as Room Admins and can appoint Co-Admins.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item(key = "reports_heading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = ModAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Safety Reports Queue",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            when (reportsState) {
                is UiState.Loading -> {
                    item(key = "reports_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VoiceCyan)
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "reports_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = reportsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (reports.isEmpty()) {
                        item(key = "reports_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "All clear",
                                        tint = SafetyEmerald,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.empty_reports_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.empty_reports_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            SafetyReportCard(
                                report = report,
                                isHostOrAdmin = report.roomHostId == currentUserId || staffCapabilities.isAdmin,
                                onResolve = {
                                    onResolveReport(report.id, ReportStatus.RESOLVED, "Reviewed and action taken")
                                },
                                onDismiss = {
                                    onResolveReport(report.id, ReportStatus.DISMISSED, "Reviewed — no violation found")
                                }
                            )
                        }
                    }
                }
            }

            item(key = "blocked_heading") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = LiveCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Blocked Users (${blockedUsers.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            if (blockedUsers.isEmpty()) {
                item(key = "blocked_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You haven't blocked anyone. Tap any user or chat message in a room to block them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(blockedUsers, key = { it }) { blockedUid ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "User ${blockedUid.take(14)}…",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Messages hidden across all rooms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onUnblockUser(blockedUid) },
                                modifier = Modifier.testTag("unblock_$blockedUid")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }

            item(key = "bottom_pad") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    userBeingEdited?.let { target ->
        EditUserAdminDialog(
            profile = target,
            onSave = { displayName, handle, bio, globallyBanned, disableCreateRoom, disableEnterRoom, disableMic, disableVideo, suspendedUntilMillis, suspensionReason ->
                onAdminEditUser(
                    target.userId,
                    displayName,
                    handle,
                    bio,
                    globallyBanned,
                    disableCreateRoom,
                    disableEnterRoom,
                    disableMic,
                    disableVideo,
                    suspendedUntilMillis,
                    suspensionReason
                )
            },
            onDismiss = { userBeingEdited = null }
        )
    }

    userBeingSuspended?.let { target ->
        SuspendUserDialog(
            targetName = target.displayName,
            onSuspend = { mins, reason ->
                onSuspendUser(target.userId, target.displayName, mins, reason)
            },
            onDismiss = { userBeingSuspended = null }
        )
    }

    userReceivingAlert?.let { target ->
        SendOrEditAlertDialog(
            existingAlert = null,
            targetName = target.displayName,
            onSave = { title, message, severity, _ ->
                onSendDirectAlert(target.userId, target.displayName, title, message, severity)
            },
            onDismiss = { userReceivingAlert = null }
        )
    }

    userBeingTransferred?.let { target ->
        SendUserToRoomDialog(
            targetName = target.displayName,
            availableRooms = liveRooms,
            onSendToRoom = { destRoom, reason ->
                onSendUserToRoom(target.userId, target.displayName, destRoom, "", reason)
                userBeingTransferred = null
            },
            onDismiss = { userBeingTransferred = null }
        )
    }

    alertBeingEdited?.let { existing ->
        SendOrEditAlertDialog(
            existingAlert = existing,
            targetName = existing.targetName.ifBlank { "Member" },
            onSave = { title, message, severity, reopen ->
                onAdminEditAlert(existing.id, title, message, severity, reopen)
            },
            onDelete = { onAdminDeleteAlert(existing.id) },
            onDismiss = { alertBeingEdited = null }
        )
    }
}

@Composable
private fun AdminUserDirectoryCard(
    profile: UserProfile,
    tier: StaffTier,
    isSelf: Boolean,
    onEdit: () -> Unit,
    onSuspend: () -> Unit,
    onAlert: () -> Unit,
    onSendToRoom: () -> Unit,
    onSetStaffTier: (StaffTier) -> Unit
) {
    val badges = buildList {
        if (profile.globallyBanned) add("BANNED" to LiveCoral)
        if (profile.currentlySuspended()) add("SUSPENDED ${profile.remainingSuspensionLabel()}" to ModAmber)
        if (profile.disableCreateRoom) add("NO ROOM CREATE" to LiveCoral)
        if (profile.disableEnterRoom) add("NO ROOM ENTER" to LiveCoral)
        if (profile.disableMic) add("NO MIC" to ModAmber)
        if (profile.disableVideo) add("NO VIDEO" to ModAmber)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isSelf) "${profile.displayName} (You)" else profile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = usernameColor(tier)
                )
                StaffTierBadge(tier = tier)
            }
            Text(
                text = "@${profile.handle} · ${profile.bio.ifBlank { "No bio" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badges.forEach { (label, tint) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tint.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (!isSelf) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename / Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            onSetStaffTier(if (tier == StaffTier.ADMIN) StaffTier.USER else StaffTier.ADMIN)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = usernameColor(StaffTier.ADMIN),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (tier == StaffTier.ADMIN) "Remove Admin" else "Make Admin",
                            style = MaterialTheme.typography.labelSmall,
                            color = usernameColor(StaffTier.ADMIN)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onSetStaffTier(if (tier == StaffTier.SUPPORT) StaffTier.USER else StaffTier.SUPPORT)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SupportAgent,
                            contentDescription = null,
                            tint = usernameColor(StaffTier.SUPPORT),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (tier == StaffTier.SUPPORT) "Remove Support" else "Make Support",
                            style = MaterialTheme.typography.labelSmall,
                            color = usernameColor(StaffTier.SUPPORT)
                        )
                    }
                    if (tier != StaffTier.ADMIN) {
                        OutlinedButton(onClick = onSuspend, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Suspend", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = onAlert, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alert", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onSendToRoom,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("admin_send_room_${profile.userId}")
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = VoiceCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send to Room", style = MaterialTheme.typography.labelSmall, color = VoiceCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAlertHistoryCard(
    alert: UserAlert,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val severity = alert.severityEnum()
    val tint = when (severity) {
        AlertSeverity.URGENT -> LiveCoral
        AlertSeverity.WARNING -> ModAmber
        AlertSeverity.INFO -> VoiceCyan
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tint.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${severity.label.uppercase()} · ${if (alert.acknowledged) "READ" else "UNREAD"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit alert", tint = VoiceCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete alert", tint = LiveCoral, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(text = alert.title, style = MaterialTheme.typography.titleSmall)
            Text(text = alert.message, style = MaterialTheme.typography.bodySmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "To: ${alert.targetName.ifBlank { alert.targetUserId.take(10) }} · From:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = alert.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = usernameColor(alert.senderTierEnum())
                )
                Text(
                    text = "· ${formatTimestampShort(alert.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SafetyMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafetyReportCard(
    report: SafetyReport,
    isHostOrAdmin: Boolean,
    onResolve: () -> Unit,
    onDismiss: () -> Unit
) {
    val status = report.statusEnum()
    val statusColor = when (status) {
        ReportStatus.OPEN -> ModAmber
        ReportStatus.RESOLVED -> SafetyEmerald
        ReportStatus.DISMISSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = status.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formatTimestampShort(report.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${report.categoryEnum().label} · Reported ${report.targetName}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Room: ${report.roomTitle} · Filed by ${report.reporterName}",
                style = MaterialTheme.typography.labelMedium,
                color = VoiceCyan
            )
            Text(
                text = report.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (report.resolutionNote.isNotBlank()) {
                Text(
                    text = "Outcome: ${report.resolutionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyEmerald
                )
            }

            if (status == ReportStatus.OPEN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dismiss_report_${report.id}")
                    ) {
                        Text("Dismiss")
                    }
                    Button(
                        onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyEmerald,
                            contentColor = Color(0xFF00261A)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resolve_report_${report.id}")
                    ) {
                        Text(if (isHostOrAdmin) "Resolve" else "Mark Resolved")
                    }
                }
            }
        }
    }
}
