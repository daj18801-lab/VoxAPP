package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.DirectMessage
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.showTopMessage
import com.example.ui.components.usernameColor
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.VoxStageNotificationState
import com.example.ui.theme.SnapBlue
import com.example.viewmodel.DirectChatViewModel

@Composable
fun DirectChatScreen(
    viewModel: DirectChatViewModel,
    userProfile: UserProfile,
    myStaffTier: StaffTier,
    otherPhotoBase64: String = "",
    onOpenUserProfile: (String) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val threadState by viewModel.threadState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf("") }

    DisposableEffect(viewModel.threadId) {
        VoxStageNotificationState.activeDmThreadId = viewModel.threadId
        DirectMessageNotificationHelper.cancelThreadNotification(context, viewModel.threadId)
        onDispose {
            if (VoxStageNotificationState.activeDmThreadId == viewModel.threadId) {
                VoxStageNotificationState.activeDmThreadId = null
            }
        }
    }

    val thread = (threadState as? UiState.Success)?.data
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(thread?.id, thread?.unreadByUserIds) {
        viewModel.markReadIfNeeded(thread)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    val otherName = thread?.otherName(viewModel.currentUserId) ?: "Private Chat"
    val otherColorHex = thread?.otherColorHex(viewModel.currentUserId) ?: "#0088FF"
    val otherTier = thread?.otherTier(viewModel.currentUserId) ?: StaffTier.USER

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dm_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Chats",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        val otherUid = thread?.otherUserId(viewModel.currentUserId).orEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = otherUid.isNotBlank()) {
                                    onOpenUserProfile(otherUid)
                                }
                                .testTag("dm_header_profile_link")
                        ) {
                            SpeakingAvatarCircle(
                                displayName = otherName,
                                colorHex = otherColorHex,
                                speakingLevel = 0f,
                                micActive = false,
                                size = 42.dp,
                                photoBase64 = otherPhotoBase64
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = otherName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(otherTier),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    StaffTierBadge(tier = otherTier)
                                }
                                Text(
                                    text = "Private Message · Tap to view profile",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = composerText,
                            onValueChange = { composerText = it.take(500) },
                            placeholder = { Text(stringResource(R.string.hint_send_dm)) },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(999.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dm_message_input")
                        )

                        Surface(
                            shape = CircleShape,
                            color = if (composerText.isNotBlank() && !isSending) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val toSend = composerText.trim()
                                    if (toSend.isNotEmpty()) {
                                        composerText = ""
                                        viewModel.sendMessage(
                                            senderName = userProfile.displayName,
                                            senderTier = myStaffTier,
                                            text = toSend
                                        )
                                    }
                                },
                                enabled = composerText.isNotBlank() && !isSending,
                                modifier = Modifier.testTag("dm_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send private message",
                                    tint = if (composerText.isNotBlank() && !isSending) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.TopCenter
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = (messagesState as UiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            SpeakingAvatarCircle(
                                displayName = otherName,
                                colorHex = otherColorHex,
                                speakingLevel = 0f,
                                micActive = false,
                                size = 72.dp,
                                photoBase64 = otherPhotoBase64
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = otherName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = usernameColor(otherTier)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send your first message to start a private conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                SnapChatMessageBlock(
                                    message = msg,
                                    isMe = msg.senderId == viewModel.currentUserId
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        TopSnackbarOverlay(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Snapchat-signature vertical bar chat block:
 * - Sender's uppercase name above
 * - Crisp 3.dp vertical color bar on the left (Royal Blue for ME, role-coded color for them)
 */
@Composable
private fun SnapChatMessageBlock(
    message: DirectMessage,
    isMe: Boolean
) {
    val tier = message.senderTierEnum()
    val accentBarColor = if (isMe) SnapBlue else usernameColor(tier)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isMe) "ME" else message.senderName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = accentBarColor
            )
            if (!isMe) {
                StaffTierBadge(tier = tier)
            }
            Text(
                text = formatTimestampShort(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBarColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
