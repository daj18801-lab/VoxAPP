package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.PostComment
import com.example.data.ProfilePost
import com.example.data.StaffTier
import kotlinx.coroutines.flow.Flow

fun launchSystemPostShare(context: Context, post: ProfilePost) {
    if (post.locked) return
    val body = buildString {
        if (post.authorTierEnum() == StaffTier.ADMIN) {
            append("${post.authorName} on VoxStage")
        } else {
            append("${post.authorName} (@${post.authorHandle}) on VoxStage")
        }
        if (post.text.isNotBlank()) {
            append(":\n\n")
            append(post.text)
        } else if (post.imageBase64.isNotBlank()) {
            append(" shared a photo on VoxStage!")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Post by ${post.authorName} on VoxStage")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}

@Composable
fun PostCommentsDialog(
    post: ProfilePost,
    commentsFlow: Flow<List<PostComment>>,
    currentUserId: String,
    isStaff: Boolean,
    onAuthorClick: ((String) -> Unit)? = null,
    onAddComment: (text: String, onSent: () -> Unit) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val comments by commentsFlow.collectAsState(initial = emptyList())
    var draftText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 300.dp, max = 560.dp)
                .testTag("post_comments_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Comments (${comments.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Post by ${post.authorName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_comments_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close comments",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No comments yet. Be the first to reply!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            val tier = comment.authorTierEnum()
                            val canDeleteComment = comment.authorId == currentUserId ||
                                post.authorId == currentUserId ||
                                isStaff
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("comment_item_${comment.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = comment.authorName,
                                        colorHex = comment.authorColorHex,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 34.dp,
                                        photoBase64 = comment.authorPhotoBase64,
                                        modifier = Modifier.then(
                                            if (onAuthorClick != null) {
                                                Modifier.clickable {
                                                    onDismiss()
                                                    onAuthorClick(comment.authorId)
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = comment.authorName,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = usernameColor(tier),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            StaffTierBadge(tier = tier)
                                        }
                                        Text(
                                            text = comment.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatPostDateTime(comment.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (canDeleteComment) {
                                        IconButton(
                                            onClick = { onDeleteComment(comment.id) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("delete_comment_${comment.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete comment",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it.take(500) },
                        placeholder = { Text("Write a comment…") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input_field")
                    )
                    Button(
                        onClick = {
                            val clean = draftText.trim()
                            if (clean.isNotEmpty() && !isSending) {
                                isSending = true
                                onAddComment(clean) {
                                    draftText = ""
                                    isSending = false
                                }
                            }
                        },
                        enabled = draftText.isNotBlank() && !isSending,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("send_comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send comment",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharePostDialog(
    post: ProfilePost,
    onRepostToFeed: (note: String, onShared: () -> Unit) -> Unit,
    onRecordExternalShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var shareNote by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }

    if (post.locked) {
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share ${post.authorName}'s Post",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Repost this update to your Profile & Discovery feed or share it to another app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = shareNote,
                    onValueChange = { shareNote = it.take(280) },
                    placeholder = { Text("Add an optional thought…") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_post_note_input")
                )
                Button(
                    onClick = {
                        if (!isSharing) {
                            isSharing = true
                            onRepostToFeed(shareNote) {
                                isSharing = false
                                onDismiss()
                            }
                        }
                    },
                    enabled = !isSharing,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_repost_to_feed_button")
                ) {
                    Icon(Icons.Rounded.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share to My Profile & Discovery")
                }
                OutlinedButton(
                    onClick = {
                        onRecordExternalShare()
                        launchSystemPostShare(context, post)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_external_apps_button")
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Outside VoxStage")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
