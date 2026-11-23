package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.TrialSession
import com.example.auth.TrialSessionManager
import com.example.data.AppConfig
import com.example.data.ChatRoom
import com.example.data.ProfilePost
import com.example.data.VoxStageRepository
import com.example.ui.components.OnlinePresenceDot
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.showTopMessage
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 1-Day Trial experience allowing prospective users to explore public live rooms,
 * unlocked discovery posts, and the public member directory for 24 hours.
 * Trial users cannot buy points or view locked photos and must sign in with Google
 * after 1 day (or immediately if an Admin disables trial logins).
 */
@Composable
fun TrialVoxStageApp(
    trialSession: TrialSession,
    appConfig: AppConfig,
    onExitToGoogleSignIn: (reasonMessage: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) {
        val dbId = context.getString(com.example.R.string.firestore_database_id)
        VoxStageRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(dbId))
    }

    val publicRooms by repository.observePublicRoomsForTrial().collectAsState(initial = emptyList())
    val unlockedPosts by repository.observeUnlockedPostsForTrial().collectAsState(initial = emptyList())
    val directoryEntries by repository.observeUserDirectoryForTrial().collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Discover, 1=Rooms, 2=Trial Account
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val presenceClock = rememberPresenceClockMillis()

    // Enforce live 24-hour expiration countdown
    LaunchedEffect(trialSession.expiresAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (trialSession.isExpired(nowMillis)) {
                TrialSessionManager.deactivateTrial(context)
                onExitToGoogleSignIn("Your 1-day trial has ended. Sign in with your Google email to continue.")
                break
            }
            delay(15_000L)
        }
    }

    // Immediately honor Admin disabling trial logins in real time
    LaunchedEffect(appConfig.trialLoginsEnabled) {
        if (!appConfig.trialLoginsEnabled) {
            TrialSessionManager.deactivateTrial(context)
            onExitToGoogleSignIn("Trial logins were disabled by the Administrator. Please sign in with your Google email.")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ModAmber.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassBottom,
                                    contentDescription = "1-Day Trial active",
                                    tint = ModAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Trial · ${trialSession.displayName}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${trialSession.remainingLabel(nowMillis)} · Limited guest access",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ModAmber
                                )
                            }
                            Button(
                                onClick = {
                                    TrialSessionManager.deactivateTrial(context)
                                    onExitToGoogleSignIn(null)
                                },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("trial_upgrade_google_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Sign-In", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Explore, contentDescription = "Discover") },
                        label = { Text("Discover") },
                        modifier = Modifier.testTag("trial_tab_discover")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = "Rooms") },
                        label = { Text("Rooms") },
                        modifier = Modifier.testTag("trial_tab_rooms")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Person, contentDescription = "Trial Account") },
                        label = { Text("Trial") },
                        modifier = Modifier.testTag("trial_tab_account")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (appConfig.hasVisibleBanner()) {
                        item(key = "trial_news_banner") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Campaign,
                                        contentDescription = "News banner",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = appConfig.bannerTitle.ifBlank { "VoxStage News" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (appConfig.bannerMessage.isNotBlank()) {
                                            Text(
                                                text = appConfig.bannerMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            item(key = "trial_discover_header") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Locked photos unavailable in trial",
                                            tint = ModAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Showing unlocked public posts only. Trial users cannot view locked photos or buy points.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (unlockedPosts.isEmpty()) {
                                item(key = "trial_no_posts") {
                                    Text(
                                        text = "No public unlocked posts yet. Sign in with Google to share posts and connect with friends!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                }
                            } else {
                                items(unlockedPosts, key = { it.id }) { post ->
                                    TrialUnlockedPostCard(post = post)
                                }
                            }
                        }

                        1 -> {
                            item(key = "trial_rooms_header") {
                                Text(
                                    text = "Live Public Rooms (${publicRooms.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            if (publicRooms.isEmpty()) {
                                item(key = "trial_no_rooms") {
                                    Text(
                                        text = "No public rooms are live right now. Sign in with Google to host your own live room!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                }
                            } else {
                                items(publicRooms, key = { it.id }) { room ->
                                    TrialPublicRoomCard(
                                        room = room,
                                        onTap = {
                                            scope.launch {
                                                snackbarHostState.showTopMessage(
                                                    "Sign in with your Google email to join voice & chat inside \"${room.displayTitle()}\"."
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            if (directoryEntries.isNotEmpty()) {
                                item(key = "trial_people_header") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Community Members (${directoryEntries.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                items(directoryEntries.take(20), key = { "dir_${it.userId}" }) { member ->
                                    val isOnline = member.isCurrentlyOnline(presenceClock)
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            SpeakingAvatarCircle(
                                                displayName = member.displayName,
                                                colorHex = member.avatarColorHex,
                                                speakingLevel = 0f,
                                                micActive = false,
                                                size = 40.dp,
                                                photoBase64 = member.photoBase64
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                val memberTier = member.tierEnum()
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OnlinePresenceDot(isOnline = isOnline)
                                                    Text(
                                                        text = member.displayName,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = if (memberTier == com.example.data.StaffTier.ADMIN) {
                                                        if (isOnline) "Online" else "Offline"
                                                    } else {
                                                        "@${member.handle}"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            item(key = "trial_account_card") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "1-Day Trial Session",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Signed in as trial guest \"${trialSession.displayName}\". Remaining trial time: ${trialSession.remainingLabel(nowMillis)}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                        // Explicit restriction rows so trial users clearly see blocked features
                                        TrialRestrictedFeatureRow(
                                            icon = Icons.Rounded.ShoppingCart,
                                            title = "Buying Points (Google Play)",
                                            subtitle = "Trial users cannot buy points. Sign in with Google to purchase point packs."
                                        )
                                        TrialRestrictedFeatureRow(
                                            icon = Icons.Rounded.Lock,
                                            title = "Viewing Locked 18+ Photos",
                                            subtitle = "Trial users cannot view or unlock locked photos. Sign in with Google to verify 18+."
                                        )

                                        Button(
                                            onClick = {
                                                TrialSessionManager.deactivateTrial(context)
                                                onExitToGoogleSignIn(null)
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("trial_account_google_signin_button")
                                        ) {
                                            Text("Sign In with Google Email")
                                        }
                                    }
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

@Composable
private fun TrialRestrictedFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LiveCoral.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = LiveCoral,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = "Not available in trial",
                tint = LiveCoral,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TrialUnlockedPostCard(post: ProfilePost) {
    val bmp = rememberBase64ImageBitmap(post.imageBase64)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpeakingAvatarCircle(
                    displayName = post.authorName,
                    colorHex = post.authorColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    size = 38.dp,
                    photoBase64 = post.authorPhotoBase64
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (post.authorTierEnum() == com.example.data.StaffTier.ADMIN) {
                            formatTimestampShort(post.createdAt)
                        } else {
                            "@${post.authorHandle} · ${formatTimestampShort(post.createdAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Post image from ${post.authorName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

@Composable
private fun TrialPublicRoomCard(
    room: ChatRoom,
    onTap: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SafetyEmerald.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Live room",
                    tint = SafetyEmerald
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.displayTitle(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hosted by ${room.hostName} · ${room.topic.ifBlank { room.categoryEnum().label }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.People,
                    contentDescription = "Participants",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${room.participantCount}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
