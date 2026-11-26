package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.DirectThread
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Play-Policy-compliant background WorkManager worker that periodically checks for unread
 * private messages so notifications still arrive even if Android freezes or restarts
 * the app process while in the background.
 */
class DirectMessageSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!DirectMessageNotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }
        val dmNotifsEnabled = DirectMessageNotificationPrefs.isDmNotificationsEnabled(applicationContext)

        val currentUid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        } ?: return Result.success()

        return try {
            val databaseId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val snapshot = db.collection("directThreads")
                .whereArrayContains("participantIds", currentUid)
                .get()
                .await()

            val baselineSeeded = DirectMessageNotificationPrefs.isBaselineSeeded(applicationContext, currentUid)
            for (doc in snapshot.documents) {
                val thread = doc.toObject(DirectThread::class.java) ?: continue
                if (thread.isGroup && thread.lastGroupAlertAtMillis > 0L && thread.lastGroupAlertSenderId != currentUid) {
                    if (baselineSeeded) {
                        DirectMessageNotificationHelper.notifyGroupUrgentAlertIfNew(
                            context = applicationContext,
                            thread = thread,
                            currentUserId = currentUid
                        )
                    } else {
                        DirectMessageNotificationPrefs.setLastGroupAlertMillis(
                            context = applicationContext,
                            uid = currentUid,
                            threadId = thread.id,
                            alertMillis = thread.lastGroupAlertAtMillis
                        )
                    }
                }
                if (!dmNotifsEnabled) continue
                if (!thread.isUnreadFor(currentUid) ||
                    thread.lastMessageSenderId.isBlank() ||
                    thread.lastMessageSenderId == currentUid
                ) {
                    continue
                }
                if (baselineSeeded) {
                    DirectMessageNotificationHelper.notifyPrivateMessageIfNew(
                        context = applicationContext,
                        thread = thread,
                        currentUserId = currentUid
                    )
                } else {
                    DirectMessageNotificationPrefs.setLastNotifiedKey(
                        context = applicationContext,
                        uid = currentUid,
                        threadId = thread.id,
                        key = DirectMessageNotificationPrefs.buildMessageKey(thread)
                    )
                }
            }
            if (!baselineSeeded) {
                DirectMessageNotificationPrefs.markBaselineSeeded(applicationContext, currentUid)
            }
            Result.success()
        } catch (err: Exception) {
            Log.w(TAG, "Background DM sync failed; will retry", err)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DirectMessageSyncWorker"
        private const val UNIQUE_WORK_NAME = "voxstage_dm_background_sync"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = PeriodicWorkRequestBuilder<DirectMessageSyncWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
            } catch (err: Exception) {
                Log.w(TAG, "Failed to schedule DirectMessageSyncWorker", err)
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_WORK_NAME)
            } catch (err: Exception) {
                Log.w(TAG, "Failed to cancel DirectMessageSyncWorker", err)
            }
        }
    }
}
