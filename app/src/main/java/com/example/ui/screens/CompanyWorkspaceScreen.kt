package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PhonelinkRing
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.SubAccountLoginPrefs
import com.example.data.CompanyCode
import com.example.data.CompanySubAccount
import com.example.data.CompanySubAccountFileNote
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.EmployeeRequest
import com.example.data.EmployeeRequestCategory
import com.example.data.EmployeeRequestStatus
import com.example.data.ProTier
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.data.WorkOrder
import com.example.data.WorkOrderPriority
import com.example.data.WorkOrderStatus
import com.example.ui.theme.SafetyEmerald
import com.example.data.WorkOrderType
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatShortDateTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
}

@Composable
fun CompanyTeamConsoleScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    staffTier: StaffTier = StaffTier.USER,
    canCreateSubAccounts: Boolean,
    companyThread: DirectThread?,
    companyCode: CompanyCode? = null,
    subAccounts: List<CompanySubAccount>,
    companySubAccountDms: List<DirectThread> = emptyList(),
    teamWorkOrders: List<WorkOrder>,
    teamEmployeeRequests: List<EmployeeRequest>,
    directoryEntries: List<DirectoryEntry>,
    onBack: () -> Unit,
    onOpenCompanyDm: (String) -> Unit,
    onOpenSubAccountDm: (threadId: String, invisible: Boolean) -> Unit = { _, _ -> },
    onSaveCompanyCodeAndPin: (companyName: String, code: String, pin: String) -> Unit = { _, _, _ -> },
    onUpgradeToBusinessPlay: () -> Unit = {},
    onCreateSubAccount: (
        companyName: String,
        subName: String,
        roleTitle: String,
        onCreated: (CompanySubAccount) -> Unit,
        onBusinessUpgradeRequired: () -> Unit
    ) -> Unit,
    onRevokeSubAccount: (CompanySubAccount) -> Unit,
    onBanSubAccount: (CompanySubAccount, reason: String) -> Unit = { _, _ -> },
    onSuspendSubAccount: (CompanySubAccount, durationMillis: Long, reason: String) -> Unit = { _, _, _ -> },
    onUpdateSubAccountCapabilities: (CompanySubAccount, pttEnabled: Boolean, messagingEnabled: Boolean, micEnabled: Boolean, videoEnabled: Boolean) -> Unit = { _, _, _, _, _ -> },
    onBuzzSubAccountPhone: (CompanySubAccount, message: String) -> Unit = { _, _ -> },
    observeSubAccountFileNotes: (subAccountId: String) -> Flow<List<CompanySubAccountFileNote>> = { flowOf(emptyList()) },
    onAddSubAccountFileNote: (CompanySubAccount, category: String, note: String) -> Unit = { _, _, _ -> },
    onDeleteSubAccountFileNote: (subAccountId: String, noteId: String) -> Unit = { _, _ -> },
    onSetSupervisor: (thread: DirectThread, targetUserId: String, targetName: String, makeSupervisor: Boolean) -> Unit,
    onSendWorkOrder: (
        companyOwnerId: String,
        companyThreadId: String,
        supervisorUids: List<String>,
        targetUserId: String,
        targetName: String,
        orderType: WorkOrderType,
        priority: WorkOrderPriority,
        title: String,
        body: String
    ) -> Unit,
    onReviewRequest: (requestId: String, status: EmployeeRequestStatus, note: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val directoryByUid = remember(directoryEntries) { directoryEntries.associateBy { it.userId } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBusinessUpgradeDialog by remember { mutableStateOf(false) }
    var showCodePinDialog by remember { mutableStateOf(false) }
    var showPinPlaintext by remember { mutableStateOf(false) }
    var newlyCreatedSub by remember { mutableStateOf<CompanySubAccount?>(null) }
    var workOrderTarget by remember { mutableStateOf<Pair<CompanySubAccount, WorkOrderPriority>?>(null) }
    var controlsTarget by remember { mutableStateOf<CompanySubAccount?>(null) }
    var fileNotesTarget by remember { mutableStateOf<CompanySubAccount?>(null) }
    var revokeTarget by remember { mutableStateOf<CompanySubAccount?>(null) }
    var reviewTarget by remember { mutableStateOf<Pair<EmployeeRequest, EmployeeRequestStatus>?>(null) }

    val defaultCompanyName = remember(companyCode, companyThread, subAccounts, userProfile) {
        companyCode?.companyName?.takeIf { it.isNotBlank() }
            ?: companyThread?.groupName?.takeIf { it.isNotBlank() }
            ?: subAccounts.firstOrNull()?.companyName?.takeIf { it.isNotBlank() }
            ?: "${userProfile?.effectiveDisplayName()?.ifBlank { "My" } ?: "My"} Company"
    }

    val activeSubs = remember(subAccounts) { subAccounts.filter { it.active && !it.banned } }
    val allManagedSubs = remember(subAccounts) {
        subAccounts.sortedWith(compareByDescending<CompanySubAccount> { it.active && !it.banned }.thenByDescending { it.createdAt?.toDate()?.time ?: 0L })
    }
    val maxSubAccounts = remember(userProfile, staffTier) {
        userProfile?.maxCompanySubAccounts(staffTier) ?: 10
    }
    val effectiveTierLabel = remember(userProfile, staffTier) {
        val t = userProfile?.effectiveProTier(staffTier) ?: ProTier.VIP_PRO
        if (t == ProTier.BUSINESS) "Business Account" else t.label
    }
    val pendingReqCount = remember(teamEmployeeRequests) {
        teamEmployeeRequests.count { it.statusEnum() == EmployeeRequestStatus.PENDING }
    }
    val openOrderCount = remember(teamWorkOrders) {
        teamWorkOrders.count { it.statusEnum() != WorkOrderStatus.COMPLETED }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("company_console_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = defaultCompanyName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeSubs.size}/$maxSubAccounts active sub-accounts · $effectiveTierLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onOpenCompanyDm(defaultCompanyName) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("open_company_dm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = "Open Company Group DM",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Team DM", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("company_tab_roster"),
                            text = { Text("Team (${activeSubs.size})", maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("company_tab_orders"),
                            text = { Text("Orders ($openOrderCount)", maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.testTag("company_tab_requests"),
                            text = { Text("Requests ($pendingReqCount)", maxLines = 1) }
                        )
                        if (canCreateSubAccounts) {
                            Tab(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                modifier = Modifier.testTag("company_tab_sub_dms"),
                                text = { Text("Sub DMs (${companySubAccountDms.size})", maxLines = 1) }
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (canCreateSubAccounts) {
                            item(key = "company_code_pin_card") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.2.dp, ModAmber.copy(alpha = 0.55f)),
                                    modifier = Modifier.fillMaxWidth()
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
                                                imageVector = Icons.Rounded.Key,
                                                contentDescription = "Company Code and PIN",
                                                tint = ModAmber
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Company Code + Employee PIN Generator",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = ModAmber.copy(alpha = 0.16f),
                                                    modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (maxSubAccounts >= 50) {
                                                            "Business Account · Up to 50 sub-accounts"
                                                        } else {
                                                            "VIP Pro · First $maxSubAccounts sub-accounts included"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = ModAmber,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Generate your Company Code & Master PIN, then every sub-account you create receives its own unique 6-digit Employee PIN. Removing an employee permanently revokes their PIN.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (companyCode != null) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            text = "Code: ${companyCode.companyCode}",
                                                            style = MaterialTheme.typography.titleSmall.copy(
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontFamily = FontFamily.Monospace
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = "PIN: ${if (showPinPlaintext) companyCode.pin else "••••"}",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        )
                                                        if (companyCode.ownerEmail.isNotBlank()) {
                                                            Text(
                                                                text = "Account Email: ${companyCode.ownerEmail}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Row {
                                                        IconButton(
                                                            onClick = { showPinPlaintext = !showPinPlaintext },
                                                            modifier = Modifier.testTag("toggle_company_pin_visibility")
                                                        ) {
                                                            Icon(
                                                                imageVector = if (showPinPlaintext) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                                contentDescription = if (showPinPlaintext) "Hide PIN" else "Show PIN"
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                clipboard.setText(
                                                                    AnnotatedString(
                                                                        "VOX Business Employee Login\nCompany: ${companyCode.companyName}\nCompany Code: ${companyCode.companyCode}\nPIN: ${companyCode.pin}"
                                                                    )
                                                                )
                                                                Toast.makeText(context, "Copied Company Code & PIN", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.testTag("copy_company_code_pin_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.ContentCopy,
                                                                contentDescription = "Copy Company Code and PIN",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (companyCode == null) {
                                                Button(
                                                    onClick = {
                                                        val prefix = defaultCompanyName.uppercase()
                                                            .filter { it in 'A'..'Z' || it in '0'..'9' }
                                                            .take(6)
                                                            .ifBlank { "TEAM" }
                                                        val generatedCode = "${prefix}${(100..999).random()}"
                                                        val generatedPin = (100000..999999).random().toString()
                                                        onSaveCompanyCodeAndPin(defaultCompanyName, generatedCode, generatedPin)
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ModAmber,
                                                        contentColor = Color.Black
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                        .testTag("auto_generate_company_code_button")
                                                ) {
                                                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Generate Code & PIN", fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { showCodePinDialog = true },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("edit_company_code_pin_button")
                                            ) {
                                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (companyCode == null) "Custom Code" else "Update Code & PIN",
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "create_sub_cta") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
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
                                                imageVector = Icons.Rounded.Business,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Employee Sub-Accounts (${activeSubs.size}/$maxSubAccounts)",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Text(
                                            text = "Create sub-names for employees or let them join with your Company Code + PIN. Full owner controls include Remove, Ban, Suspend, Urgent Alerts, Private File Notes, Buzz Phone, and Invisible/Visible DM monitoring.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                // ONLY show Business Account ($99.99/mo) upgrade modal when trying to add a 10th sub-account!
                                                if (activeSubs.size >= maxSubAccounts && maxSubAccounts < 50) {
                                                    showBusinessUpgradeDialog = true
                                                } else {
                                                    showCreateDialog = true
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("create_sub_account_button")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Add Employee Sub-Account")
                                        }
                                    }
                                }
                            }
                        }

                        if (allManagedSubs.isEmpty()) {
                            item(key = "empty_roster") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Badge,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "No Employee Sub-Accounts Yet",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Share your Company Code + PIN above or create an employee sub-name to get started.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(allManagedSubs, key = { it.id }) { sub ->
                                val isOwner = sub.ownerId == currentUserId
                                val dirEntry = if (sub.claimedUserId.isNotBlank()) directoryByUid[sub.claimedUserId] else null
                                val isDnd = sub.doNotDisturb || (dirEntry?.doNotDisturb == true)
                                val isSupervisor = sub.supervisor ||
                                    (sub.claimedUserId.isNotBlank() && companyThread?.isGroupSupervisor(sub.claimedUserId) == true)
                                val isSuspended = sub.isCurrentlySuspended()

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            sub.banned -> LiveCoral.copy(alpha = 0.7f)
                                            isSuspended -> ModAmber.copy(alpha = 0.7f)
                                            isSupervisor -> ModAmber.copy(alpha = 0.55f)
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            SpeakingAvatarCircle(
                                                displayName = sub.subName,
                                                colorHex = dirEntry?.avatarColorHex ?: "#0088FF",
                                                speakingLevel = 0f,
                                                micActive = false,
                                                size = 44.dp,
                                                photoBase64 = dirEntry?.photoBase64.orEmpty()
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = sub.subName,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (sub.banned) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = LiveCoral.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "BANNED",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                                color = LiveCoral,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else if (isSuspended) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = ModAmber.copy(alpha = 0.22f)
                                                        ) {
                                                            Text(
                                                                text = "SUSPENDED",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                                color = ModAmber,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else if (!sub.active) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.surfaceVariant
                                                        ) {
                                                            Text(
                                                                text = "REMOVED",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (isSupervisor && sub.active && !sub.banned) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = ModAmber.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "SUPERVISOR",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                                color = ModAmber,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (isDnd && sub.active && !sub.banned) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = LiveCoral.copy(alpha = 0.16f)
                                                        ) {
                                                            Text(
                                                                text = "DND",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = LiveCoral,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = buildString {
                                                        if (sub.roleTitle.isNotBlank()) append("${sub.roleTitle} · ")
                                                        if (sub.isClaimed()) append(sub.claimedEmail.ifBlank { "Active Employee" })
                                                        else append("Waiting for employee login")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (sub.hasAnyCapabilityRestriction() && sub.active && !sub.banned) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val restrictedLabels = buildList {
                                                            if (!sub.pttEnabled) add("No PTT")
                                                            if (!sub.messagingEnabled) add("No Msg")
                                                            if (!sub.micEnabled) add("Mic Off")
                                                            if (!sub.videoEnabled) add("Video Off")
                                                        }
                                                        restrictedLabels.forEach { badge ->
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = LiveCoral.copy(alpha = 0.14f)
                                                            ) {
                                                                Text(
                                                                    text = badge,
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = LiveCoral,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (isOwner && sub.joinPin.isNotBlank() && sub.active && !sub.banned) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Code: ${sub.companyCode.ifBlank { companyCode?.companyCode.orEmpty() }}  ·  Unique PIN: ${sub.joinPin}",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val codeText = sub.companyCode.ifBlank { companyCode?.companyCode.orEmpty() }
                                                            clipboard.setText(
                                                                AnnotatedString(
                                                                    "VOX Employee Login\nCompany: ${sub.companyName}\nCompany Code: $codeText\nUnique Employee PIN: ${sub.joinPin}"
                                                                )
                                                            )
                                                            Toast.makeText(context, "Copied unique login PIN for ${sub.subName}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("copy_sub_unique_pin_${sub.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ContentCopy,
                                                            contentDescription = "Copy unique employee PIN",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (sub.isClaimed() && sub.active && !sub.banned) {
                                                Button(
                                                    onClick = { workOrderTarget = sub to WorkOrderPriority.NORMAL },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("send_work_order_btn_${sub.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Order", maxLines = 1)
                                                }
                                            }
                                            if (isOwner) {
                                                OutlinedButton(
                                                    onClick = { controlsTarget = sub },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("owner_sub_controls_btn_${sub.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Tune,
                                                        contentDescription = "Owner Sub-Account Controls",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Controls", maxLines = 1)
                                                }
                                                if (sub.isClaimed() && companyThread != null && sub.active && !sub.banned) {
                                                    IconButton(
                                                        onClick = {
                                                            onSetSupervisor(
                                                                companyThread,
                                                                sub.claimedUserId,
                                                                sub.subName,
                                                                !isSupervisor
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("toggle_supervisor_btn_${sub.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.AdminPanelSettings,
                                                            contentDescription = if (isSupervisor) "Remove Supervisor" else "Make Supervisor",
                                                            tint = if (isSupervisor) ModAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
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

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (teamWorkOrders.isEmpty()) {
                            item(key = "empty_orders") {
                                EmptyWorkspaceCard(
                                    title = "No Work Orders or Notes Sent",
                                    subtitle = "Select any claimed employee in the Team tab or in your Company Group DM to dispatch a Work Order or Note."
                                )
                            }
                        } else {
                            items(teamWorkOrders, key = { it.id }) { order ->
                                WorkOrderCard(
                                    order = order,
                                    isRecipient = false,
                                    onAcknowledge = {},
                                    onComplete = {}
                                )
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (teamEmployeeRequests.isEmpty()) {
                            item(key = "empty_requests") {
                                EmptyWorkspaceCard(
                                    title = "No Employee Requests",
                                    subtitle = "When employees submit schedule, supplies, or dispatch requests, they will appear here for Owner & Supervisor review."
                                )
                            }
                        } else {
                            items(teamEmployeeRequests, key = { it.id }) { req ->
                                EmployeeRequestCard(
                                    request = req,
                                    canReview = true,
                                    onAction = { nextStatus -> reviewTarget = req to nextStatus }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Tab 3: Sub-Account 1-on-1 DMs (Owner Invisible / Visible Access)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (companySubAccountDms.isEmpty()) {
                            item(key = "empty_sub_dms") {
                                EmptyWorkspaceCard(
                                    title = "No Sub-Account 1-on-1 DMs Yet",
                                    subtitle = "When your employees start private 1-on-1 messages with each other, you can open and monitor them here in Invisible or Visible mode."
                                )
                            }
                        } else {
                            items(companySubAccountDms, key = { it.id }) { dm ->
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
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${dm.userAName.ifBlank { "Employee" }} ↔ ${dm.userBName.ifBlank { "Employee" }}",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatShortDateTime(dm.updatedAt?.toDate()?.time ?: 0L),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = dm.lastMessageText.ifBlank { "Private 1-on-1 conversation started" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onOpenSubAccountDm(dm.id, true) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("open_sub_dm_invisible_${dm.id}")
                                            ) {
                                                Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Invisible", maxLines = 1)
                                            }
                                            Button(
                                                onClick = { onOpenSubAccountDm(dm.id, false) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("open_sub_dm_visible_${dm.id}")
                                            ) {
                                                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Visible", maxLines = 1)
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
    }

    if (showCreateDialog) {
        CreateSubAccountDialog(
            initialCompanyName = defaultCompanyName,
            onDismiss = { showCreateDialog = false },
            onCreate = { coName, subName, role ->
                showCreateDialog = false
                onCreateSubAccount(
                    coName,
                    subName,
                    role,
                    { created -> newlyCreatedSub = created },
                    { showBusinessUpgradeDialog = true }
                )
            }
        )
    }

    if (showBusinessUpgradeDialog) {
        BusinessAccountUpgradeDialog(
            onDismiss = { showBusinessUpgradeDialog = false },
            onUpgrade = {
                showBusinessUpgradeDialog = false
                onUpgradeToBusinessPlay()
            }
        )
    }

    if (showCodePinDialog) {
        CompanyCodePinDialog(
            initialCompanyName = companyCode?.companyName?.ifBlank { defaultCompanyName } ?: defaultCompanyName,
            initialCode = companyCode?.companyCode.orEmpty(),
            initialPin = companyCode?.pin.orEmpty(),
            onDismiss = { showCodePinDialog = false },
            onSave = { coName, code, pin ->
                showCodePinDialog = false
                onSaveCompanyCodeAndPin(coName, code, pin)
            }
        )
    }

    controlsTarget?.let { target ->
        val liveTarget = subAccounts.firstOrNull { it.id == target.id } ?: target
        OwnerSubAccountControlsDialog(
            subAccount = liveTarget,
            onDismiss = { controlsTarget = null },
            onUpdateCapabilities = { ptt, msg, mic, video ->
                onUpdateSubAccountCapabilities(liveTarget, ptt, msg, mic, video)
            },
            onBuzzPhone = { msg ->
                controlsTarget = null
                onBuzzSubAccountPhone(liveTarget, msg)
            },
            onSendUrgentAlert = {
                controlsTarget = null
                workOrderTarget = liveTarget to WorkOrderPriority.URGENT
            },
            onOpenFileNotes = {
                controlsTarget = null
                fileNotesTarget = liveTarget
            },
            onSuspend = { durationMillis, reason ->
                controlsTarget = null
                onSuspendSubAccount(liveTarget, durationMillis, reason)
            },
            onBan = { reason ->
                controlsTarget = null
                onBanSubAccount(liveTarget, reason)
            },
            onRemove = {
                controlsTarget = null
                revokeTarget = liveTarget
            }
        )
    }

    fileNotesTarget?.let { target ->
        SubAccountFileNotesDialog(
            subAccount = target,
            notesFlow = remember(target.id) { observeSubAccountFileNotes(target.id) },
            onAddNote = { category, note -> onAddSubAccountFileNote(target, category, note) },
            onDeleteNote = { noteId -> onDeleteSubAccountFileNote(target.id, noteId) },
            onDismiss = { fileNotesTarget = null }
        )
    }

    newlyCreatedSub?.let { created ->
        val displayCode = created.companyCode.ifBlank { companyCode?.companyCode.orEmpty() }
        val displayPin = created.joinPin.ifBlank { created.accessCode }
        AlertDialog(
            onDismissRequest = { newlyCreatedSub = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = PresenceOnlineGreen
                )
            },
            title = { Text("Sub-Account Created!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Give this unique PIN to your employee. They select Business on the VOX login screen and enter the Company Code, their Unique Employee PIN, and their First & Last Name. If you remove them later, this PIN is permanently revoked so they cannot log back in under another name.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Sub-Account: ${created.subName}", fontWeight = FontWeight.Bold)
                            if (displayCode.isNotBlank()) {
                                Text(
                                    text = "Company Code: $displayCode",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Unique Employee PIN: $displayPin",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = ModAmber
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                "VOX Employee Login\nCompany: ${created.companyName}\nCompany Code: $displayCode\nUnique Employee PIN: $displayPin"
                            )
                        )
                        Toast.makeText(context, "Unique employee credentials copied!", Toast.LENGTH_SHORT).show()
                        newlyCreatedSub = null
                    },
                    modifier = Modifier.testTag("copy_created_sub_button")
                ) {
                    Text("Copy & Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { newlyCreatedSub = null }) {
                    Text("Close")
                }
            }
        )
    }

    workOrderTarget?.let { (target, initPriority) ->
        SendWorkOrderDialog(
            targetName = target.subName,
            initialPriority = initPriority,
            onDismiss = { workOrderTarget = null },
            onSend = { orderType, priority, title, body ->
                workOrderTarget = null
                onSendWorkOrder(
                    target.ownerId,
                    target.companyThreadId,
                    companyThread?.groupSupervisorIds ?: target.supervisorUids,
                    target.claimedUserId,
                    target.subName,
                    orderType,
                    priority,
                    title,
                    body
                )
            }
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke Sub-Account?") },
            text = {
                Text("Remove \"${target.subName}\" from your Company Workspace and Company Group DM? They will no longer be able to access company messages.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        revokeTarget = null
                        onRevokeSubAccount(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral)
                ) {
                    Text("Revoke")
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    reviewTarget?.let { (req, nextStatus) ->
        var note by remember(req.id, nextStatus) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("${nextStatus.label} Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${req.requesterName}: ${req.subject}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        label = { Text("Response Note (optional)") },
                        placeholder = { Text("Add instructions or confirmation...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_request_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        reviewTarget = null
                        onReviewRequest(req.id, nextStatus, note)
                    },
                    modifier = Modifier.testTag("confirm_review_request_button")
                ) {
                    Text("Confirm ${nextStatus.label}")
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmployeeWorkHubScreen(
    userProfile: UserProfile,
    companyThread: DirectThread?,
    incomingWorkOrders: List<WorkOrder>,
    myRequests: List<EmployeeRequest>,
    onToggleDnd: (Boolean) -> Unit,
    onOpenCompanyDm: () -> Unit,
    onUpdateWorkOrderStatus: (orderId: String, status: WorkOrderStatus) -> Unit,
    onSubmitRequest: (
        companyOwnerId: String,
        companyThreadId: String,
        supervisorUids: List<String>,
        category: EmployeeRequestCategory,
        subject: String,
        details: String,
        urgent: Boolean
    ) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSubmitRequestDialog by remember { mutableStateOf(false) }

    val unackCount = remember(incomingWorkOrders) {
        incomingWorkOrders.count { it.statusEnum() == WorkOrderStatus.OPEN }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Header + DND control
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Work Orders & Requests",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = "${userProfile.companyName.ifBlank { "Company Workspace" }} · ${userProfile.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (userProfile.companyThreadId.isNotBlank()) {
                            OutlinedButton(
                                onClick = onOpenCompanyDm,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("work_hub_open_dm_button")
                            ) {
                                Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Team DM")
                            }
                        }
                    }

                    // Do Not Disturb banner card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (userProfile.doNotDisturb) LiveCoral.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (userProfile.doNotDisturb) LiveCoral.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (userProfile.doNotDisturb) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                contentDescription = "Do Not Disturb",
                                tint = if (userProfile.doNotDisturb) LiveCoral else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userProfile.doNotDisturb) "Do Not Disturb is ON" else "Do Not Disturb",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (userProfile.doNotDisturb) LiveCoral else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (userProfile.doNotDisturb)
                                        "Normal alerts are silenced. URGENT work alerts still come through."
                                    else
                                        "Silence routine alerts while keeping URGENT work alerts active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userProfile.doNotDisturb,
                                onCheckedChange = onToggleDnd,
                                modifier = Modifier.testTag("work_hub_dnd_switch")
                            )
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("work_hub_tab_orders"),
                    text = {
                        Text(
                            if (unackCount > 0) "Work Orders ($unackCount new)" else "Work Orders (${incomingWorkOrders.size})",
                            maxLines = 1
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("work_hub_tab_requests"),
                    text = { Text("My Requests (${myRequests.size})", maxLines = 1) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (incomingWorkOrders.isEmpty()) {
                        item(key = "no_incoming_orders") {
                            EmptyWorkspaceCard(
                                title = "No Work Orders or Notes Yet",
                                subtitle = "Tasks and dispatch notes sent by your Company Owner or Supervisors will show up here and alert you immediately."
                            )
                        }
                    } else {
                        items(incomingWorkOrders, key = { it.id }) { order ->
                            WorkOrderCard(
                                order = order,
                                isRecipient = true,
                                onAcknowledge = { onUpdateWorkOrderStatus(order.id, WorkOrderStatus.ACKNOWLEDGED) },
                                onComplete = { onUpdateWorkOrderStatus(order.id, WorkOrderStatus.COMPLETED) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "new_request_cta") {
                        Button(
                            onClick = { showSubmitRequestDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_employee_request_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit a Request to Owner / Supervisors")
                        }
                    }

                    if (myRequests.isEmpty()) {
                        item(key = "no_my_requests") {
                            EmptyWorkspaceCard(
                                title = "No Requests Submitted",
                                subtitle = "Need schedule adjustments, supplies, or dispatch support? Submit a request above."
                            )
                        }
                    } else {
                        items(myRequests, key = { it.id }) { req ->
                            EmployeeRequestCard(
                                request = req,
                                canReview = false,
                                onAction = {}
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSubmitRequestDialog) {
        SubmitEmployeeRequestDialog(
            onDismiss = { showSubmitRequestDialog = false },
            onSubmit = { category, subject, details, urgent ->
                showSubmitRequestDialog = false
                onSubmitRequest(
                    userProfile.parentCompanyOwnerId,
                    userProfile.companyThreadId,
                    companyThread?.groupSupervisorIds.orEmpty(),
                    category,
                    subject,
                    details,
                    urgent
                )
            }
        )
    }
}

@Composable
fun SubAccountBasicProfileScreen(
    userProfile: UserProfile,
    onSaveDisplayName: (String) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var nameInput by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    val hasChanges = nameInput.trim().isNotEmpty() && nameInput.trim() != userProfile.displayName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Basic Profile Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Company Sub-Account Identity Summary
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SpeakingAvatarCircle(
                            displayName = userProfile.displayName,
                            colorHex = userProfile.avatarColorHex,
                            speakingLevel = 0f,
                            micActive = false,
                            size = 60.dp,
                            photoBase64 = userProfile.photoBase64
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.displayName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = userProfile.companyName.ifBlank { "Company Sub-Account" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (userProfile.subAccountId.isNotBlank()) {
                                Text(
                                    text = "Sub-Account ID: ${userProfile.subAccountId}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Company Sub-Account · 18+ Verified Under Parent · Push-to-Talk & 60s Clips Enabled",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = SafetyEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("sub_account_18_plus_verified_badge")
                    ) {
                        Text(
                            text = "18+ VERIFIED (PARENT B2B ACCOUNT)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = SafetyEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Change Display Name Card
            Surface(
                shape = RoundedCornerShape(20.dp),
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
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Change Display Name",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(40) },
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sub_account_display_name_input")
                    )
                    Button(
                        onClick = { onSaveDisplayName(nameInput) },
                        enabled = hasChanges,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sub_account_save_name_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Display Name")
                    }
                }
            }

            // Do Not Disturb Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (userProfile.doNotDisturb) LiveCoral.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DoNotDisturbOn,
                        contentDescription = "Do Not Disturb",
                        tint = if (userProfile.doNotDisturb) LiveCoral else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Do Not Disturb",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Silences regular alerts and pop-ups. Urgent work orders and urgent alerts will STILL come through.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userProfile.doNotDisturb,
                        onCheckedChange = onToggleDnd,
                        modifier = Modifier.testTag("sub_account_dnd_switch")
                    )
                }
            }

            // Sign Out
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sub_account_sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyWorkspaceCard(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkOrderCard(
    order: WorkOrder,
    isRecipient: Boolean,
    onAcknowledge: () -> Unit,
    onComplete: () -> Unit
) {
    val isUrgent = order.isUrgent()
    val status = order.statusEnum()
    val borderColor = when {
        status == WorkOrderStatus.COMPLETED -> PresenceOnlineGreen.copy(alpha = 0.5f)
        isUrgent -> LiveCoral.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isUrgent && status != WorkOrderStatus.COMPLETED)
            LiveCoral.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isUrgent) 1.5.dp else 1.dp, borderColor),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isUrgent) LiveCoral else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isUrgent) "URGENT ${order.orderTypeEnum().label.uppercase()}" else order.orderTypeEnum().label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (isUrgent) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (status) {
                            WorkOrderStatus.COMPLETED -> PresenceOnlineGreen.copy(alpha = 0.2f)
                            WorkOrderStatus.ACKNOWLEDGED -> MaterialTheme.colorScheme.secondaryContainer
                            WorkOrderStatus.OPEN -> ModAmber.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (status) {
                                WorkOrderStatus.COMPLETED -> PresenceOnlineGreen
                                WorkOrderStatus.ACKNOWLEDGED -> MaterialTheme.colorScheme.onSecondaryContainer
                                WorkOrderStatus.OPEN -> ModAmber
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = formatShortDateTime(order.createdAt?.toDate()?.time ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = order.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = order.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isRecipient) "From ${order.senderName} (${order.senderRole})"
                else "Assigned to ${order.targetName} · Sent by ${order.senderName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isRecipient && status != WorkOrderStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status == WorkOrderStatus.OPEN) {
                        OutlinedButton(
                            onClick = onAcknowledge,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ack_work_order_${order.id}")
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Acknowledge")
                        }
                    }
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("complete_work_order_${order.id}")
                    ) {
                        Icon(Icons.Rounded.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Completed")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeRequestCard(
    request: EmployeeRequest,
    canReview: Boolean,
    onAction: (EmployeeRequestStatus) -> Unit
) {
    val status = request.statusEnum()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (request.urgent) 1.5.dp else 1.dp,
            if (request.urgent && status == EmployeeRequestStatus.PENDING) LiveCoral.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = request.categoryEnum().label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    if (request.urgent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LiveCoral
                        ) {
                            Text(
                                text = "URGENT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (status) {
                            EmployeeRequestStatus.APPROVED, EmployeeRequestStatus.COMPLETED -> PresenceOnlineGreen.copy(alpha = 0.2f)
                            EmployeeRequestStatus.DENIED -> LiveCoral.copy(alpha = 0.18f)
                            EmployeeRequestStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                            EmployeeRequestStatus.PENDING -> ModAmber.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (status) {
                                EmployeeRequestStatus.APPROVED, EmployeeRequestStatus.COMPLETED -> PresenceOnlineGreen
                                EmployeeRequestStatus.DENIED -> LiveCoral
                                EmployeeRequestStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                EmployeeRequestStatus.PENDING -> ModAmber
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = formatShortDateTime(request.createdAt?.toDate()?.time ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = request.subject,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = request.details,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Requested by ${request.requesterName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (request.reviewerNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${request.reviewedByName.ifBlank { "Management" }}: ${request.reviewerNote}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (canReview && status == EmployeeRequestStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAction(EmployeeRequestStatus.APPROVED) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_request_${request.id}")
                    ) {
                        Text("Approve", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onAction(EmployeeRequestStatus.DENIED) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("deny_request_${request.id}")
                    ) {
                        Text("Deny", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateSubAccountDialog(
    initialCompanyName: String,
    onDismiss: () -> Unit,
    onCreate: (companyName: String, subName: String, roleTitle: String) -> Unit
) {
    var companyName by remember { mutableStateOf(initialCompanyName) }
    var subName by remember { mutableStateOf("") }
    var roleTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Employee Sub-Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it.take(60) },
                    label = { Text("Company / Team Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_company_name_input")
                )
                OutlinedTextField(
                    value = subName,
                    onValueChange = { subName = it.take(40) },
                    label = { Text("Employee Sub-Name") },
                    placeholder = { Text("e.g., Truck 12 - Mike") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_name_input")
                )
                OutlinedTextField(
                    value = roleTitle,
                    onValueChange = { roleTitle = it.take(40) },
                    label = { Text("Role / Position (optional)") },
                    placeholder = { Text("e.g., Field Tech, Driver, Dispatch") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_sub_role_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(companyName, subName, roleTitle) },
                enabled = subName.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_sub_button")
            ) {
                Text("Create Sub-Name")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SendWorkOrderDialog(
    targetName: String,
    initialPriority: WorkOrderPriority = WorkOrderPriority.NORMAL,
    onDismiss: () -> Unit,
    onSend: (orderType: WorkOrderType, priority: WorkOrderPriority, title: String, body: String) -> Unit
) {
    var orderType by remember { mutableStateOf(WorkOrderType.WORK_ORDER) }
    var priority by remember(initialPriority) { mutableStateOf(initialPriority) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Work Order / Note to $targetName") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkOrderType.entries.forEach { t ->
                        FilterChip(
                            selected = orderType == t,
                            onClick = { orderType = t },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("work_order_type_${t.name.lowercase()}")
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = priority == WorkOrderPriority.NORMAL,
                        onClick = { priority = WorkOrderPriority.NORMAL },
                        label = { Text("Normal") },
                        modifier = Modifier.testTag("work_order_priority_normal")
                    )
                    FilterChip(
                        selected = priority == WorkOrderPriority.URGENT,
                        onClick = { priority = WorkOrderPriority.URGENT },
                        label = { Text("URGENT (Breaks DND)") },
                        modifier = Modifier.testTag("work_order_priority_urgent")
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Title / Job Number") },
                    placeholder = { Text("e.g., Dock 4 Urgent Delivery") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("work_order_title_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(600) },
                    label = { Text("Instructions / Note") },
                    placeholder = { Text("Describe the work task or note that needs attention...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("work_order_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(orderType, priority, title, body) },
                enabled = title.trim().length >= 2 && body.trim().length >= 2,
                colors = if (priority == WorkOrderPriority.URGENT)
                    ButtonDefaults.buttonColors(containerColor = LiveCoral)
                else ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("confirm_send_work_order_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (priority == WorkOrderPriority.URGENT) "Send Urgent Alert" else "Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SubmitEmployeeRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (category: EmployeeRequestCategory, subject: String, details: String, urgent: Boolean) -> Unit
) {
    var category by remember { mutableStateOf(EmployeeRequestCategory.DISPATCH) }
    var subject by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var urgent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Request") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EmployeeRequestCategory.entries.take(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EmployeeRequestCategory.entries.drop(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(80) },
                    label = { Text("Subject") },
                    placeholder = { Text("e.g., Need extra pallets at Site B") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_request_subject_input")
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(600) },
                    label = { Text("Details") },
                    placeholder = { Text("Provide details for your Owner or Supervisor...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_request_details_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mark as Urgent",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (urgent) LiveCoral else MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = urgent,
                        onCheckedChange = { urgent = it },
                        modifier = Modifier.testTag("employee_request_urgent_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(category, subject, details, urgent) },
                enabled = subject.trim().length >= 2 && details.trim().length >= 2,
                modifier = Modifier.testTag("confirm_submit_request_button")
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WorkOrderAlertModalDialog(
    order: WorkOrder,
    onAcknowledge: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUrgent = order.isUrgent()
    val accent = if (isUrgent) LiveCoral else ModAmber

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUrgent) Icons.Rounded.PriorityHigh else Icons.AutoMirrored.Rounded.Assignment,
                    contentDescription = "Work Order Alert",
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isUrgent) "URGENT ${order.orderTypeEnum().label.uppercase()}" else "New ${order.orderTypeEnum().label}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = accent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = order.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = order.body,
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "From: ${order.senderName} (${order.senderRole})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUrgent) {
                    Text(
                        text = "Urgent alert — breaks through Do Not Disturb.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = LiveCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.testTag("work_order_alert_acknowledge_button")
            ) {
                Text("Acknowledge")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("work_order_alert_complete_button")
                ) {
                    Text("Mark Done")
                }
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
fun JoinCompanySubAccountDialog(
    onDismiss: () -> Unit,
    onClaim: (subName: String, accessCode: String) -> Unit
) {
    var subName by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Business,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Join Company Sub-Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the Sub-Name and Access Code provided by your VIP Pro Company Owner. Your account will switch to Company Employee mode (Company Group DM, 60s PTT, Work Orders & Requests only).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = subName,
                    onValueChange = { subName = it.take(40) },
                    label = { Text("Assigned Sub-Name") },
                    placeholder = { Text("e.g., Truck 12 - Mike") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_company_sub_name_input")
                )
                OutlinedTextField(
                    value = accessCode,
                    onValueChange = { accessCode = it.uppercase().take(20) },
                    label = { Text("Access Code") },
                    placeholder = { Text("e.g., EMP_X9K2M4") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_company_access_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onClaim(subName, accessCode) },
                enabled = subName.trim().isNotEmpty() && accessCode.trim().length >= 4,
                modifier = Modifier.testTag("confirm_join_company_button")
            ) {
                Text("Link Sub-Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BusinessAccountUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Business,
                contentDescription = "Business Account Upgrade",
                tint = ModAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Upgrade to Business Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You've reached the 10 sub-account limit on VIP Pro. To add 11–50 employee sub-accounts, upgrade to a Business Account.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ModAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Business Account · $99.99 / month",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = ModAmber
                        )
                        Text(
                            text = "• Up to 50 active employee sub-accounts\n" +
                                "• Company Code + PIN employee logins\n" +
                                "• Invisible & Visible access to sub-account 1-on-1 DMs\n" +
                                "• Full owner controls: Remove, Ban, Suspend, Urgent Alerts, File Notes & Buzz Phone",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ModAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("business_upgrade_subscribe_button")
            ) {
                Text("Upgrade · $99.99/mo", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
private fun CompanyCodePinDialog(
    initialCompanyName: String,
    initialCode: String,
    initialPin: String,
    onDismiss: () -> Unit,
    onSave: (companyName: String, code: String, pin: String) -> Unit
) {
    var coName by remember { mutableStateOf(initialCompanyName) }
    var code by remember { mutableStateOf(initialCode) }
    var pin by remember { mutableStateOf(initialPin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = ModAmber) },
        title = { Text("Company Code & PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Employees select Business on the VOX login screen and sign in using this Company Code, PIN, and their First & Last Name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = coName,
                    onValueChange = { coName = it.take(60) },
                    label = { Text("Company Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("company_code_dialog_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = SubAccountLoginPrefs.normalizeCompanyCode(it) },
                    label = { Text("Company Code (4-24 chars)") },
                    placeholder = { Text("e.g., ACME2026") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("company_code_dialog_code_input")
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = SubAccountLoginPrefs.normalizePin(it) },
                    label = { Text("Company PIN (4-12 chars)") },
                    placeholder = { Text("e.g., 4829") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("company_code_dialog_pin_input")
                )
                OutlinedButton(
                    onClick = {
                        val prefix = coName.uppercase()
                            .filter { it in 'A'..'Z' || it in '0'..'9' }
                            .take(6)
                            .ifBlank { "TEAM" }
                        code = "${prefix}${(100..999).random()}"
                        pin = (100000..999999).random().toString()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_generate_code_pin_button")
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Generate Code & 6-Digit PIN")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(coName, code, pin) },
                enabled = coName.trim().length >= 2 && code.length >= 4 && pin.length >= 4,
                modifier = Modifier.testTag("confirm_save_company_code_pin_button")
            ) {
                Text("Save Code & PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OwnerSubAccountControlsDialog(
    subAccount: CompanySubAccount,
    onDismiss: () -> Unit,
    onUpdateCapabilities: (pttEnabled: Boolean, messagingEnabled: Boolean, micEnabled: Boolean, videoEnabled: Boolean) -> Unit,
    onBuzzPhone: (message: String) -> Unit,
    onSendUrgentAlert: () -> Unit,
    onOpenFileNotes: () -> Unit,
    onSuspend: (durationMillis: Long, reason: String) -> Unit,
    onBan: (reason: String) -> Unit,
    onRemove: () -> Unit
) {
    var buzzMessage by remember { mutableStateOf("Management needs your immediate attention.") }
    var moderationReason by remember { mutableStateOf("") }
    var selectedSuspendHours by remember { mutableIntStateOf(4) } // 1, 4, 24, 168, -1 = Until Lifted
    val isCurrentlySuspended = subAccount.isCurrentlySuspended()

    var pttEnabled by remember(subAccount.id, subAccount.pttEnabled) { mutableStateOf(subAccount.pttEnabled) }
    var messagingEnabled by remember(subAccount.id, subAccount.messagingEnabled) { mutableStateOf(subAccount.messagingEnabled) }
    var micEnabled by remember(subAccount.id, subAccount.micEnabled) { mutableStateOf(subAccount.micEnabled) }
    var videoEnabled by remember(subAccount.id, subAccount.videoEnabled) { mutableStateOf(subAccount.videoEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Owner Controls · ${subAccount.subName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = "Full company owner controls for this sub-account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 0. Capabilities & Hardware Controls (PTT, Messaging, Microphone, Video)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Permissions & Hardware",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        SubCapabilityToggleRow(
                            title = "Push-to-Talk (PTT)",
                            subtitle = "Allow walkie-talkie PTT & 60s clips",
                            checked = pttEnabled,
                            enabled = subAccount.active && !subAccount.banned,
                            testTag = "sub_control_toggle_ptt",
                            onCheckedChange = { next ->
                                pttEnabled = next
                                onUpdateCapabilities(next, messagingEnabled, micEnabled, videoEnabled)
                            }
                        )
                        SubCapabilityToggleRow(
                            title = "Messaging",
                            subtitle = "Allow sending text & photo messages",
                            checked = messagingEnabled,
                            enabled = subAccount.active && !subAccount.banned,
                            testTag = "sub_control_toggle_messaging",
                            onCheckedChange = { next ->
                                messagingEnabled = next
                                onUpdateCapabilities(pttEnabled, next, micEnabled, videoEnabled)
                            }
                        )
                        SubCapabilityToggleRow(
                            title = "Microphone",
                            subtitle = "Allow live mic audio in calls & PTT",
                            checked = micEnabled,
                            enabled = subAccount.active && !subAccount.banned,
                            testTag = "sub_control_toggle_mic",
                            onCheckedChange = { next ->
                                micEnabled = next
                                onUpdateCapabilities(pttEnabled, messagingEnabled, next, videoEnabled)
                            }
                        )
                        SubCapabilityToggleRow(
                            title = "Video / Camera",
                            subtitle = "Allow turning on camera in video calls",
                            checked = videoEnabled,
                            enabled = subAccount.active && !subAccount.banned,
                            testTag = "sub_control_toggle_video",
                            onCheckedChange = { next ->
                                videoEnabled = next
                                onUpdateCapabilities(pttEnabled, messagingEnabled, micEnabled, next)
                            }
                        )
                    }
                }

                // 1. Buzz Phone
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Buzz Employee's Phone (Breaks DND)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        OutlinedTextField(
                            value = buzzMessage,
                            onValueChange = { buzzMessage = it.take(180) },
                            label = { Text("Buzz Message") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("owner_buzz_message_input")
                        )
                        Button(
                            onClick = { onBuzzPhone(buzzMessage) },
                            enabled = subAccount.active && !subAccount.banned,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("owner_buzz_phone_button")
                        ) {
                            Icon(Icons.Rounded.PhonelinkRing, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buzz Phone Now")
                        }
                    }
                }

                // 2. Urgent Alert + 3. Private File Notes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendUrgentAlert,
                        enabled = subAccount.isClaimed() && subAccount.active && !subAccount.banned,
                        colors = ButtonDefaults.buttonColors(containerColor = LiveCoral),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("owner_urgent_alert_button")
                    ) {
                        Icon(Icons.Rounded.PriorityHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Urgent Alert", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenFileNotes,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("owner_file_notes_button")
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("File Notes", maxLines = 1)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 4. Suspend / Unsuspend
                OutlinedTextField(
                    value = moderationReason,
                    onValueChange = { moderationReason = it.take(200) },
                    label = { Text("Reason (for Suspend / Ban)") },
                    placeholder = { Text("Optional note for your records...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_mod_reason_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(1 to "1h", 4 to "4h", 24 to "24h", 168 to "7d").forEach { (hrs, lbl) ->
                        FilterChip(
                            selected = selectedSuspendHours == hrs,
                            onClick = { selectedSuspendHours = hrs },
                            label = { Text(lbl) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val durMs = selectedSuspendHours.toLong() * 60L * 60L * 1000L
                            onSuspend(durMs, moderationReason)
                        },
                        enabled = !subAccount.banned,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("owner_suspend_sub_button")
                    ) {
                        Icon(Icons.Rounded.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Suspend", maxLines = 1)
                    }
                    if (isCurrentlySuspended) {
                        Button(
                            onClick = { onSuspend(0L, "") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("owner_unsuspend_sub_button")
                        ) {
                            Text("Unsuspend", maxLines = 1)
                        }
                    }
                }

                // 5. Ban + 6. Remove
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onBan(moderationReason) },
                        enabled = !subAccount.banned,
                        colors = ButtonDefaults.buttonColors(containerColor = LiveCoral),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("owner_ban_sub_button")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (subAccount.banned) "Banned" else "Ban", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("owner_remove_sub_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SubCapabilityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (checked) MaterialTheme.colorScheme.onSurface else LiveCoral
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SubAccountFileNotesDialog(
    subAccount: CompanySubAccount,
    notesFlow: Flow<List<CompanySubAccountFileNote>>,
    onAddNote: (category: String, note: String) -> Unit,
    onDeleteNote: (noteId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var category by remember { mutableStateOf("NOTE") }
    var draftNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private File Notes · ${subAccount.subName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Only you (Company Owner) can see these file notes. Sub-accounts cannot read them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("NOTE", "WARNING", "INCIDENT", "PERF").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = draftNote,
                    onValueChange = { draftNote = it.take(450) },
                    label = { Text("Add Private Note") },
                    placeholder = { Text("Enter record-keeping note...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_file_note_input")
                )
                Button(
                    onClick = {
                        if (draftNote.trim().isNotEmpty()) {
                            onAddNote(category, draftNote)
                            draftNote = ""
                        }
                    },
                    enabled = draftNote.trim().isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_sub_file_note_button")
                ) {
                    Text("Save File Note")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (notes.isEmpty()) {
                    Text(
                        text = "No file notes recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    notes.forEach { n ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${n.category} · ${formatShortDateTime(n.createdAt?.toDate()?.time ?: 0L)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = n.note,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteNote(n.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete File Note",
                                        tint = LiveCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
