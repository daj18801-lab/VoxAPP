package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.R
import com.example.data.DirectThread
import com.example.data.StaffTier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles inline Quick Reply actions submitted directly from a background Private Message
 * pop-up notification without needing to open the app.
 */
class DirectMessageQuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(DirectMessageNotificationHelper.KEY_QUICK_REPLY_TEXT)
            ?.toString()
            ?.trim()
            ?.take(500)
            .orEmpty()
        val threadId = intent.getStringExtra(DirectMessageNotificationHelper.EXTRA_QUICK_REPLY_THREAD_ID).orEmpty()
        val peerName = intent.getStringExtra(DirectMessageNotificationHelper.EXTRA_QUICK_REPLY_SENDER_NAME)
            .orEmpty()
            .ifBlank { "Private Chat" }

        if (replyText.isBlank() || threadId.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = Firebase.auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    pendingResult.finish()
                    return@launch
                }
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val threadRef = db.collection("directThreads").document(threadId)
                val threadSnap = threadRef.get().await()
                val thread = threadSnap.toObject(DirectThread::class.java)
                if (thread == null || thread.voxOfficial || !thread.participantIds.contains(uid) || thread.isUserKicked(uid) || thread.hasAnyBlock()) {
                    DirectMessageNotificationHelper.cancelThreadNotification(appContext, threadId)
                    pendingResult.finish()
                    return@launch
                }

                val profileSnap = runCatching { db.collection("users").document(uid).get().await() }.getOrNull()
                val staffSnap = runCatching { db.collection("staffRoles").document(uid).get().await() }.getOrNull()
                val senderName = profileSnap?.getString("customDisplayName")?.trim()?.ifBlank { null }
                    ?: profileSnap?.getString("displayName")?.trim()?.ifBlank { null }
                    ?: Firebase.auth.currentUser?.displayName?.trim()?.ifBlank { null }
                    ?: "Member"
                val realTier = StaffTier.fromRaw(staffSnap?.getString("staffTier"))
                val senderTier = when (profileSnap?.getString("customMembershipTier")?.trim()?.uppercase()) {
                    "ADMIN" -> StaffTier.ADMIN
                    "SUPPORT" -> StaffTier.SUPPORT
                    "FREE", "PRO", "VIP_PRO", "BUSINESS" -> StaffTier.USER
                    else -> realTier
                }

                val msgRef = threadRef.collection("messages").document()
                val msgPayload = mapOf(
                    "threadId" to threadId,
                    "participantIds" to thread.participantIds,
                    "senderId" to uid,
                    "senderName" to senderName.take(40),
                    "senderTier" to senderTier.name,
                    "text" to replyText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val unreadRecipients = thread.participantIds.filter { it != uid }
                val batch = db.batch()
                batch.set(msgRef, msgPayload)
                batch.update(
                    threadRef,
                    mapOf(
                        "lastMessageText" to replyText.take(300),
                        "lastMessageSenderId" to uid,
                        "unreadByUserIds" to unreadRecipients,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()

                // Update notification to confirm reply sent, then dismiss cleanly
                if (DirectMessageNotificationHelper.hasNotificationPermission(appContext)) {
                    try {
                        val confirmNotif = NotificationCompat.Builder(
                            appContext,
                            DirectMessageNotificationHelper.CHANNEL_ID_STANDARD
                        )
                            .setSmallIcon(R.drawable.ic_stat_message)
                            .setColor(0xFF0088FF.toInt())
                            .setContentTitle(peerName)
                            .setContentText("You replied: $replyText")
                            .setTimeoutAfter(2500L)
                            .setAutoCancel(true)
                            .setSilent(true)
                            .build()
                        NotificationManagerCompat.from(appContext).notify(
                            DirectMessageNotificationHelper.notificationIdForThread(threadId),
                            confirmNotif
                        )
                    } catch (_: SecurityException) {
                        // Ignored
                    }
                }
            } catch (err: Exception) {
                Log.w(TAG, "Failed to send inline quick reply for thread $threadId", err)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DMQuickReplyReceiver"
    }
}
