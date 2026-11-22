package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Replaces any currently visible popup message immediately so rapid actions
 * (like toggling the mic on/off) never queue stale popups.
 */
suspend fun SnackbarHostState.showTopMessage(
    message: String,
    duration: SnackbarDuration = SnackbarDuration.Short
) {
    currentSnackbarData?.dismiss()
    showSnackbar(message = message, duration = duration)
}

/**
 * Floating top-of-screen notification banner backed by [SnackbarHostState].
 * Always sits cleanly below the status bar so bottom controls (mic, chat composer,
 * navigation bar) are never obstructed.
 */
@Composable
fun TopSnackbarOverlay(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
    ) { snackbarData ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("top_snackbar_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = snackbarData.visuals.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                )
                snackbarData.visuals.actionLabel?.let { actionLabel ->
                    TextButton(onClick = { snackbarData.performAction() }) {
                        Text(
                            text = actionLabel,
                            color = MaterialTheme.colorScheme.inversePrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                IconButton(
                    onClick = { snackbarData.dismiss() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_snackbar_dismiss_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss notification",
                        tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
