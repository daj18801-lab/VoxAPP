package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ProfilePost
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.DirectMessageSyncWorker
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.encodeImageUriToBase64Jpeg
import com.example.ui.components.parseHexColor
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.usernameColor
import com.example.ui.theme.AvatarAccentHexPalette
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    userEmail: String,
    staffCapabilities: StaffCapabilities,
    friendsCount: Int,
    myPostsState: UiState<List<ProfilePost>>,
    isPublishingPost: Boolean,
    isUpdatingPhoto: Boolean,
    onUpdateProfilePhoto: (String) -> Unit,
    onCreatePost: (text: String, imageBase64: String, onPosted: () -> Unit) -> Unit,
    onToggleLikePost: (ProfilePost) -> Unit,
    onDeletePost: (ProfilePost) -> Unit,
    onSaveProfile: (
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        darkThemePreferred: Boolean,
        invisibleModePreferred: Boolean
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = My Posts, 1 = Edit & Studio
    var showFullAvatar by remember { mutableStateOf(false) }

    var dmNotificationsEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isDmNotificationsEnabled(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(DirectMessageNotificationHelper.hasNotificationPermission(context))
    }
    val dmNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            dmNotificationsEnabled = true
            DirectMessageNotificationPrefs.setDmNotificationsEnabled(context, true)
            DirectMessageNotificationHelper.ensureChannel(context)
            DirectMessageSyncWorker.schedule(context)
        }
    }

    // Post composer state
    var postText by remember { mutableStateOf("") }
    var draftPostPhotoBase64 by remember { mutableStateOf("") }
    var isEncodingPostPhoto by remember { mutableStateOf(false) }

    // Profile editor state
    var displayName by remember { mutableStateOf(userProfile?.displayName ?: "") }
    var handle by remember { mutableStateOf(userProfile?.handle ?: "") }
    var bio by remember { mutableStateOf(userProfile?.bio ?: "") }
    var avatarColorHex by remember { mutableStateOf(userProfile?.avatarColorHex ?: "#0088FF") }
    var pushToTalkPreferred by remember { mutableStateOf(userProfile?.pushToTalkPreferred ?: true) }
    var nightModeEnabled by remember { mutableStateOf(userProfile?.nightModeEnabled ?: false) }
    var invisibleModePreferred by remember { mutableStateOf(userProfile?.invisibleModePreferred ?: false) }

    LaunchedEffect(userProfile?.userId, userProfile?.updatedAt) {
        if (userProfile != null) {
            displayName = userProfile.displayName
            handle = userProfile.handle
            bio = userProfile.bio
            avatarColorHex = userProfile.avatarColorHex
            pushToTalkPreferred = userProfile.pushToTalkPreferred
            nightModeEnabled = userProfile.nightModeEnabled
            invisibleModePreferred = userProfile.invisibleModePreferred
        }
    }

    // Zero-permission Android Photo Picker for Profile Picture
    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 280,
                    squareCrop = true,
                    maxBase64Chars = 115_000
                )
                if (!encoded.isNullOrBlank()) {
                    onUpdateProfilePhoto(encoded)
                }
            }
        }
    }

    // Zero-permission Android Photo Picker for Post Picture
    val postPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isEncodingPostPhoto = true
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 720,
                    squareCrop = false,
                    maxBase64Chars = 380_000
                )
                isEncodingPostPhoto = false
                if (!encoded.isNullOrBlank()) {
                    draftPostPhotoBase64 = encoded
                }
            }
        }
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var camGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> camGranted = granted }

    val myPosts = (myPostsState as? UiState.Success)?.data.orEmpty()
    val currentPhotoBase64 = userProfile?.photoBase64.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "profile_title") {
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Profile Identity Card with Profile Picture Upload
            item(key = "profile_hero") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clickable {
                                        if (currentPhotoBase64.isNotBlank()) {
                                            showFullAvatar = true
                                        } else {
                                            profilePhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    }
                                    .testTag("profile_avatar_box")
                            ) {
                                SpeakingAvatarCircle(
                                    displayName = displayName.ifBlank { "Host" },
                                    colorHex = avatarColorHex,
                                    speakingLevel = 0.35f,
                                    micActive = false,
                                    size = 80.dp,
                                    photoBase64 = currentPhotoBase64
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .clickable(enabled = !isUpdatingPhoto) {
                                            profilePhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("change_profile_photo_fab")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isUpdatingPhoto) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = "Change profile picture",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = displayName.ifBlank { "Studio Host" },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(staffCapabilities.tier)
                                    )
                                    StaffTierBadge(tier = staffCapabilities.tier)
                                }
                                Text(
                                    text = "@${handle.ifBlank { "studio" }}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (bio.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Quick Stats + Change/Remove Photo Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "${myPosts.size} Posts",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$friendsCount Friends",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        profilePhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isUpdatingPhoto,
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("change_profile_photo_button")
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (currentPhotoBase64.isBlank()) "Add Photo" else "Change Photo",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (currentPhotoBase64.isNotBlank()) {
                                    TextButton(
                                        onClick = { onUpdateProfilePhoto("") },
                                        enabled = !isUpdatingPhoto,
                                        modifier = Modifier.testTag("remove_profile_photo_button")
                                    ) {
                                        Text("Remove", color = LiveCoral, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Segmented Switcher: My Posts vs Edit & Studio
            item(key = "profile_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("My Posts (${myPosts.size})") },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("profile_tab_posts")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Edit & Studio") },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("profile_tab_studio")
                    )
                }
            }

            if (selectedTab == 0) {
                // CREATE POST COMPOSER CARD
                item(key = "create_post_composer") {
                    val draftBitmap = rememberBase64ImageBitmap(draftPostPhotoBase64)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Post an Update",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = postText,
                                onValueChange = { postText = it.take(1000) },
                                placeholder = { Text(stringResource(R.string.hint_post_update)) },
                                minLines = 2,
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("post_composer_input")
                            )

                            if (isEncodingPostPhoto) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Preparing photo…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (draftBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 260.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Image(
                                        bitmap = draftBitmap,
                                        contentDescription = "Selected post photo preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(36.dp)
                                    ) {
                                        IconButton(
                                            onClick = { draftPostPhotoBase64 = "" },
                                            modifier = Modifier.testTag("remove_draft_post_photo")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove attached photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        postPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isPublishingPost && !isEncodingPostPhoto,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.testTag("attach_post_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Add picture to post",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (draftPostPhotoBase64.isBlank()) "Add Picture" else "Change Picture")
                                }

                                Button(
                                    onClick = {
                                        onCreatePost(postText, draftPostPhotoBase64) {
                                            postText = ""
                                            draftPostPhotoBase64 = ""
                                        }
                                    },
                                    enabled = !isPublishingPost && !isEncodingPostPhoto &&
                                        (postText.isNotBlank() || draftPostPhotoBase64.isNotBlank()),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("publish_post_button")
                                ) {
                                    if (isPublishingPost) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Post")
                                }
                            }
                        }
                    }
                }

                // MY POSTS FEED
                when (myPostsState) {
                    is UiState.Loading -> {
                        item(key = "my_posts_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "my_posts_error") {
                            Text(
                                text = myPostsState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (myPosts.isEmpty()) {
                            item(key = "my_posts_empty") {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DynamicFeed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_posts_title),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_posts_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(myPosts, key = { it.id }) { post ->
                                ProfilePostCard(
                                    post = post,
                                    currentUserId = currentUserId,
                                    canDelete = true,
                                    latestAuthorPhotoBase64 = currentPhotoBase64,
                                    onAuthorClick = null,
                                    onToggleLike = { onToggleLikePost(post) },
                                    onDelete = { onDeletePost(post) }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: EDIT PROFILE, RESTRICTIONS, HARDWARE READINESS, PREFERENCES & SIGN OUT
                if (!staffCapabilities.isAdmin && userProfile != null) {
                    val activeRestrictions = buildList {
                        if (userProfile.globallyBanned) add("Account Banned")
                        if (userProfile.currentlySuspended()) add("Suspended (${userProfile.remainingSuspensionLabel()} left)")
                        if (userProfile.disableCreateRoom) add("Creating Rooms Disabled")
                        if (userProfile.disableEnterRoom) add("Entering Rooms Disabled")
                        if (userProfile.disableMic) add("Microphone Disabled")
                        if (userProfile.disableVideo) add("Video Streaming Disabled")
                    }
                    if (activeRestrictions.isNotEmpty()) {
                        item(key = "restrictions_card") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = LiveCoral.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Active Account Restrictions",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = LiveCoral
                                    )
                                    Text(
                                        text = activeRestrictions.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (staffCapabilities.isSupport && !staffCapabilities.canEnterInvisible) {
                    item(key = "support_inv_notice") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = usernameColor(StaffTier.SUPPORT).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, usernameColor(StaffTier.SUPPORT).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Invisible mode status",
                                    tint = usernameColor(StaffTier.SUPPORT)
                                )
                                Text(
                                    text = "Invisible room entry is currently off for your Support account. An Administrator can enable it for you anytime.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item(key = "hardware_readiness") {
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
                            Text(
                                text = "Audio & Video Readiness",
                                style = MaterialTheme.typography.titleMedium
                            )
                            HardwarePermissionRow(
                                icon = Icons.Rounded.Mic,
                                title = "Microphone (Voice Stage)",
                                granted = micGranted,
                                onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            )
                            HardwarePermissionRow(
                                icon = Icons.Rounded.Videocam,
                                title = "Camera (Live Video Stream)",
                                granted = camGranted,
                                onRequest = { camLauncher.launch(Manifest.permission.CAMERA) }
                            )
                        }
                    }
                }

                item(key = "edit_profile_form") {
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
                            Text(
                                text = "Stage Profile & Preferences",
                                style = MaterialTheme.typography.titleMedium
                            )

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it.take(40) },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input")
                            )

                            OutlinedTextField(
                                value = handle,
                                onValueChange = { handle = it.replace(" ", "_").take(30) },
                                label = { Text("Handle") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_handle_input")
                            )

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it.take(200) },
                                label = { Text("Bio") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input")
                            )

                            Text("Avatar Ring Color", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AvatarAccentHexPalette.forEach { hex ->
                                    val swatch = parseHexColor(hex)
                                    val selected = avatarColorHex.equals(hex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(
                                                width = if (selected) 3.dp else 0.dp,
                                                color = if (selected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { avatarColorHex = hex }
                                    )
                                }
                            }

                            if (staffCapabilities.effectiveCanEnterInvisible) {
                                ToggleSettingRow(
                                    title = "Default to Ghost Entry (Invisible Mode)",
                                    subtitle = if (staffCapabilities.isAdmin) {
                                        "Enter rooms unseen — Main Admin can still use the mic while invisible"
                                    } else {
                                        "Enter rooms unseen — switch to Visible inside a room to use the mic"
                                    },
                                    checked = invisibleModePreferred,
                                    onCheckedChange = { invisibleModePreferred = it }
                                )
                            }

                            ToggleSettingRow(
                                title = "Default to Push-to-Talk",
                                subtitle = "Hold button to transmit voice on stage (classic Paltalk style)",
                                checked = pushToTalkPreferred,
                                onCheckedChange = { pushToTalkPreferred = it }
                            )

                            ToggleSettingRow(
                                title = "Night Blue Dark Mode",
                                subtitle = "Off = Crisp Blue & White (default) · On = Deep Navy Night theme",
                                checked = nightModeEnabled,
                                onCheckedChange = { nightModeEnabled = it }
                            )

                            ToggleSettingRow(
                                title = "Private Message Notifications",
                                subtitle = if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    "Tap to grant phone notification permission for background private messages"
                                } else {
                                    "Send phone notifications for incoming private messages even in the background"
                                },
                                checked = dmNotificationsEnabled && hasNotificationPermission,
                                onCheckedChange = { enable ->
                                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !DirectMessageNotificationHelper.hasNotificationPermission(context)
                                    ) {
                                        dmNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        dmNotificationsEnabled = enable
                                        DirectMessageNotificationPrefs.setDmNotificationsEnabled(context, enable)
                                        if (enable) {
                                            DirectMessageNotificationHelper.ensureChannel(context)
                                            DirectMessageSyncWorker.schedule(context)
                                        }
                                    }
                                }
                            )

                            Button(
                                onClick = {
                                    onSaveProfile(
                                        displayName,
                                        handle,
                                        bio,
                                        avatarColorHex,
                                        pushToTalkPreferred,
                                        nightModeEnabled,
                                        invisibleModePreferred
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("save_profile_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Profile Preferences")
                            }
                        }
                    }
                }

                item(key = "sign_out_card") {
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }

            item(key = "profile_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showFullAvatar && currentPhotoBase64.isNotBlank()) {
        FullScreenPhotoDialog(
            photoBase64 = currentPhotoBase64,
            contentDescription = "My profile picture",
            onDismiss = { showFullAvatar = false }
        )
    }
}

@Composable
private fun HardwarePermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) SafetyEmerald else ModAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (granted) "Ready for live stage" else "Permission needed before going live",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = SafetyEmerald,
                modifier = Modifier.size(22.dp)
            )
        } else {
            OutlinedButton(onClick = onRequest) {
                Text("Enable")
            }
        }
    }
}
