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
import com.example.data.CompanySubAccount
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.EmployeeRequest
import com.example.data.EmployeeRequestCategory
import com.example.data.EmployeeRequestStatus
import com.example.data.UserProfile
import com.example.data.WorkOrder
import com.example.data.WorkOrderPriority
import com.example.data.WorkOrderStatus
import com.example.data.WorkOrderType
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
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
    canCreateSubAccounts: Boolean,
    companyThread: DirectThread?,
    subAccounts: List<CompanySubAccount>,
    teamWorkOrders: List<WorkOrder>,
    teamEmployeeRequests: List<EmployeeRequest>,
    directoryEntries: List<DirectoryEntry>,
    onBack: () -> Unit,
    onOpenCompanyDm: (String) -> Unit,
    onCreateSubAccount: (companyName: String, subName: String, roleTitle: String, onCreated: (CompanySubAccount) -> Unit) -> Unit,
    onRevokeSubAccount: (CompanySubAccount) -> Unit,
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
    var newlyCreatedSub by remember { mutableStateOf<CompanySubAccount?>(null) }
    var workOrderTarget by remember { mutableStateOf<CompanySubAccount?>(null) }
    var revokeTarget by remember { mutableStateOf<CompanySubAccount?>(null) }
    var reviewTarget by remember { mutableStateOf<Pair<EmployeeRequest, EmployeeRequestStatus>?>(null) }

    val defaultCompanyName = remember(companyThread, subAccounts, userProfile) {
        companyThread?.groupName?.takeIf { it.isNotBlank() }
            ?: subAccounts.firstOrNull()?.companyName?.takeIf { it.isNotBlank() }
            ?: "${userProfile?.effectiveDisplayName()?.ifBlank { "My" } ?: "My"} Company"
    }

    val activeSubs = remember(subAccounts) { subAccounts.filter { it.active } }
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
                                text = "${activeSubs.size} sub-accounts · VIP Pro Company Console",
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
                            text = { Text("Work Orders ($openOrderCount)", maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.testTag("company_tab_requests"),
                            text = { Text("Requests ($pendingReqCount)", maxLines = 1) }
                        )
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
                                                text = "Employee Sub-Accounts",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Text(
                                            text = "Create sub-names for employees. Sub-accounts get 60s Push-to-Talk and access ONLY to your private Company Group DM, Work Orders, Requests, and DND — no Discovery, no Chat Rooms, no extra perks.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { showCreateDialog = true },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("create_sub_account_button")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Create Employee Sub-Name")
                                        }
                                    }
                                }
                            }
                        }

                        if (activeSubs.isEmpty()) {
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
                                            text = "No Employee Sub-Names Yet",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Create your first sub-name above and share its Access Code with your employee to sign in.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(activeSubs, key = { it.id }) { sub ->
                                val isOwner = sub.ownerId == currentUserId
                                val dirEntry = if (sub.claimedUserId.isNotBlank()) directoryByUid[sub.claimedUserId] else null
                                val isDnd = sub.doNotDisturb || (dirEntry?.doNotDisturb == true)
                                val isSupervisor = sub.supervisor ||
                                    (sub.claimedUserId.isNotBlank() && companyThread?.isGroupSupervisor(sub.claimedUserId) == true)

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSupervisor) ModAmber.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.outlineVariant
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
                                                    if (isSupervisor) {
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
                                                    if (isDnd) {
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
                                                        if (sub.isClaimed()) append("Active (${sub.claimedEmail.ifBlank { "Claimed" }})")
                                                        else append("Waiting for employee login")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Access code copy row
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Employee Access Code",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = sub.accessCode,
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboard.setText(AnnotatedString("${sub.subName} / ${sub.accessCode}"))
                                                        Toast.makeText(context, "Copied sub-name & access code", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.testTag("copy_sub_code_${sub.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ContentCopy,
                                                        contentDescription = "Copy Access Code",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        // Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (sub.isClaimed()) {
                                                Button(
                                                    onClick = { workOrderTarget = sub },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("send_work_order_btn_${sub.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Work Order", maxLines = 1)
                                                }
                                                if (isOwner && companyThread != null) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            onSetSupervisor(
                                                                companyThread,
                                                                sub.claimedUserId,
                                                                sub.subName,
                                                                !isSupervisor
                                                            )
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                        modifier = Modifier.testTag("toggle_supervisor_btn_${sub.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.AdminPanelSettings,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            if (isSupervisor) "Un-Supervise" else "Supervisor",
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                            if (isOwner) {
                                                IconButton(
                                                    onClick = { revokeTarget = sub },
                                                    modifier = Modifier.testTag("revoke_sub_btn_${sub.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Revoke Sub-Account",
                                                        tint = LiveCoral
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

                else -> {
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
            }
        }
    }

    if (showCreateDialog) {
        CreateSubAccountDialog(
            initialCompanyName = defaultCompanyName,
            onDismiss = { showCreateDialog = false },
            onCreate = { coName, subName, role ->
                showCreateDialog = false
                onCreateSubAccount(coName, subName, role) { created ->
                    newlyCreatedSub = created
                }
            }
        )
    }

    newlyCreatedSub?.let { created ->
        AlertDialog(
            onDismissRequest = { newlyCreatedSub = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = PresenceOnlineGreen
                )
            },
            title = { Text("Sub-Name Created!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Share these credentials with your employee. They sign in via \"Company Employee Login\" on the VOX sign-in screen (or Profile -> Join Company).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Sub-Name: ${created.subName}", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Access Code: ${created.accessCode}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString("VOX Company Login\nSub-Name: ${created.subName}\nAccess Code: ${created.accessCode}"))
                        Toast.makeText(context, "Login credentials copied!", Toast.LENGTH_SHORT).show()
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

    workOrderTarget?.let { target ->
        SendWorkOrderDialog(
            targetName = target.subName,
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
                            text = "Company Sub-Account · Push-to-Talk & 60s Voice Clips Enabled · No Extra Perks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onDismiss: () -> Unit,
    onSend: (orderType: WorkOrderType, priority: WorkOrderPriority, title: String, body: String) -> Unit
) {
    var orderType by remember { mutableStateOf(WorkOrderType.WORK_ORDER) }
    var priority by remember { mutableStateOf(WorkOrderPriority.NORMAL) }
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
