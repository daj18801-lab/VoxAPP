package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VoxStageRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "daj18801@gmail.com"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun isBootstrappedAdmin(email: String?, emailVerified: Boolean): Boolean =
        emailVerified && email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)

    // --- STAFF ROLES, INVITES & CAPABILITIES ---

    suspend fun ensureStaffRoleOnSignIn(
        displayName: String,
        email: String,
        emailVerified: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val cleanEmail = email.trim().lowercase()
        val safeName = displayName.trim().ifBlank { "Staff Member" }.take(40)
        return try {
            val roleRef = db.collection("staffRoles").document(uid)
            val existingRole = roleRef.get().await()

            if (isBootstrappedAdmin(cleanEmail, emailVerified)) {
                if (!existingRole.exists()) {
                    roleRef.set(
                        mapOf(
                            "userId" to uid,
                            "email" to cleanEmail,
                            "displayName" to safeName,
                            "staffTier" to StaffTier.ADMIN.name,
                            "canEnterInvisible" to true,
                            "addedByUid" to uid,
                            "addedByName" to safeName,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return Result.success(Unit)
            }

            if (!existingRole.exists() && emailVerified && cleanEmail.isNotBlank()) {
                val inviteQuery = db.collection("staffInvites")
                    .whereEqualTo("email", cleanEmail)
                    .get()
                    .await()
                val inviteDoc = inviteQuery.documents.firstOrNull()
                if (inviteDoc != null && inviteDoc.exists()) {
                    val invitedTier = StaffTier.fromRaw(inviteDoc.getString("staffTier"))
                    val resolvedTier = if (invitedTier == StaffTier.ADMIN) StaffTier.ADMIN else StaffTier.SUPPORT
                    val allowInvisible = resolvedTier == StaffTier.ADMIN || inviteDoc.getBoolean("canEnterInvisible") == true
                    val addedByUid = inviteDoc.getString("addedByUid").orEmpty().ifBlank { uid }
                    val addedByName = inviteDoc.getString("addedByName").orEmpty().ifBlank { "Administrator" }
                    roleRef.set(
                        mapOf(
                            "userId" to uid,
                            "email" to cleanEmail,
                            "displayName" to safeName,
                            "staffTier" to resolvedTier.name,
                            "canEnterInvisible" to allowInvisible,
                            "addedByUid" to addedByUid,
                            "addedByName" to addedByName,
                            "inviteId" to inviteDoc.id,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "staffRoles/$uid")
            Result.failure(e)
        }
    }

    fun observeMyStaffCapabilities(
        userId: String,
        email: String,
        emailVerified: Boolean
    ): Flow<StaffCapabilities> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user staff role observation denied." }
        if (isBootstrappedAdmin(email, emailVerified)) {
            emit(StaffCapabilities(tier = StaffTier.ADMIN, canEnterInvisible = true))
        }
        val path = "staffRoles/$userId"
        emitAll(
            db.collection("staffRoles").document(userId)
                .snapshots()
                .map { snap ->
                    if (isBootstrappedAdmin(email, emailVerified)) {
                        StaffCapabilities(tier = StaffTier.ADMIN, canEnterInvisible = true)
                    } else if (snap.exists()) {
                        val role = snap.toObject(
                            StaffRole::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        val tier = role?.tierEnum() ?: StaffTier.USER
                        StaffCapabilities(
                            tier = tier,
                            canEnterInvisible = tier == StaffTier.ADMIN || (role?.canEnterInvisible == true)
                        )
                    } else {
                        StaffCapabilities(tier = StaffTier.USER, canEnterInvisible = false)
                    }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    emit(
                        if (isBootstrappedAdmin(email, emailVerified)) {
                            StaffCapabilities(StaffTier.ADMIN, true)
                        } else {
                            StaffCapabilities(StaffTier.USER, false)
                        }
                    )
                }
        )
    }

    fun observeAllStaffRoles(): Flow<List<StaffRole>> = flow {
        val uid = requireUserId()
        val path = "staffRoles"
        emitAll(
            db.collection("staffRoles")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        StaffRole::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<StaffRole> { if (it.tierEnum() == StaffTier.ADMIN) 0 else 1 }
                            .thenBy { it.displayName.lowercase() }
                    )
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllStaffInvites(): Flow<List<StaffInvite>> = flow {
        val uid = requireUserId()
        val path = "staffInvites"
        emitAll(
            db.collection("staffInvites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        StaffInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addStaffInviteByEmail(
        email: String,
        displayName: String,
        staffTier: StaffTier,
        canEnterInvisible: Boolean,
        adminName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") }.take(40)
        val docId = cleanEmail.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val resolvedTier = if (staffTier == StaffTier.ADMIN) StaffTier.ADMIN else StaffTier.SUPPORT
        val path = "staffInvites/$docId"
        return try {
            require(cleanEmail.contains("@") && cleanEmail.length in 3..120) {
                "Enter a valid Google email address."
            }
            require(docId.isNotEmpty()) { "Invalid email format." }

            db.collection("staffInvites").document(docId).set(
                mapOf(
                    "email" to cleanEmail,
                    "displayName" to cleanName,
                    "staffTier" to resolvedTier.name,
                    "canEnterInvisible" to (resolvedTier == StaffTier.ADMIN || canEnterInvisible),
                    "addedByUid" to uid,
                    "addedByName" to adminName.trim().ifBlank { "Administrator" }.take(40),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSupportInviteInvisible(inviteId: String, canEnterInvisible: Boolean): Result<Unit> {
        val path = "staffInvites/$inviteId"
        return try {
            requireUserId()
            db.collection("staffInvites").document(inviteId).update(
                mapOf("canEnterInvisible" to canEnterInvisible)
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeSupportInvite(inviteId: String): Result<Unit> {
        val path = "staffInvites/$inviteId"
        return try {
            requireUserId()
            db.collection("staffInvites").document(inviteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserStaffRole(
        targetUserId: String,
        targetName: String,
        targetEmail: String,
        tier: StaffTier,
        canEnterInvisible: Boolean,
        adminName: String,
        roomId: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "staffRoles/$targetUserId"
        return try {
            require(targetUserId.isNotBlank()) { "Target user ID required." }
            val roleRef = db.collection("staffRoles").document(targetUserId)

            if (tier == StaffTier.USER) {
                roleRef.delete().await()
            } else {
                val existing = roleRef.get().await()
                if (existing.exists()) {
                    roleRef.update(
                        mapOf(
                            "displayName" to targetName.trim().ifBlank { "Support" }.take(40),
                            "email" to targetEmail.trim().lowercase().take(120),
                            "staffTier" to tier.name,
                            "canEnterInvisible" to (tier == StaffTier.ADMIN || canEnterInvisible),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    roleRef.set(
                        mapOf(
                            "userId" to targetUserId,
                            "email" to targetEmail.trim().lowercase().take(120),
                            "displayName" to targetName.trim().ifBlank { "Support" }.take(40),
                            "staffTier" to tier.name,
                            "canEnterInvisible" to (tier == StaffTier.ADMIN || canEnterInvisible),
                            "addedByUid" to uid,
                            "addedByName" to adminName.trim().ifBlank { "Administrator" }.take(40),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val dirRef = db.collection("userDirectory").document(targetUserId)
            if (dirRef.get().await().exists()) {
                dirRef.update(
                    mapOf(
                        "staffTier" to tier.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!roomId.isNullOrBlank()) {
                val participantRef = db.collection("rooms").document(roomId)
                    .collection("participants").document(targetUserId)
                if (participantRef.get().await().exists()) {
                    participantRef.update(
                        mapOf(
                            "staffTier" to tier.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                writeModLog(
                    roomId = roomId,
                    actorId = uid,
                    actorName = adminName,
                    targetUserId = targetUserId,
                    targetName = targetName,
                    actionType = ModActionType.STAFF_ROLE,
                    reason = when (tier) {
                        StaffTier.ADMIN -> "Promoted to App Administrator"
                        StaffTier.SUPPORT -> "Added as Support Staff (Invisible: ${if (canEnterInvisible) "Allowed" else "Off"})"
                        StaffTier.USER -> "Removed staff role"
                    }
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSupportInvisiblePermission(
        targetUserId: String,
        canEnterInvisible: Boolean
    ): Result<Unit> {
        val path = "staffRoles/$targetUserId"
        return try {
            requireUserId()
            db.collection("staffRoles").document(targetUserId).update(
                mapOf(
                    "canEnterInvisible" to canEnterInvisible,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- USER ALERTS (SEND, EDIT, DELETE, ACKNOWLEDGE) ---

    fun observeMyUnreadAlerts(userId: String): Flow<List<UserAlert>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user alert observation denied." }
        val path = "userAlerts"
        emitAll(
            db.collection("userAlerts")
                .whereEqualTo("targetUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { !it.acknowledged }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllAlertsForAdmin(): Flow<List<UserAlert>> = flow {
        val uid = requireUserId()
        val path = "userAlerts"
        emitAll(
            db.collection("userAlerts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendUserAlert(
        targetUserId: String,
        targetName: String,
        senderName: String,
        senderTier: StaffTier,
        roomId: String,
        title: String,
        message: String,
        severity: AlertSeverity
    ): Result<Unit> {
        var path = "userAlerts"
        return try {
            val uid = requireUserId()
            require(senderTier.isStaff()) { "Only Admin or Support staff can send alerts." }
            val cleanTitle = title.trim().take(60)
            val cleanMsg = message.trim().take(300)
            require(cleanTitle.isNotEmpty() && cleanMsg.isNotEmpty()) { "Alert title and message are required." }

            val docRef = db.collection("userAlerts").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "targetUserId" to targetUserId.take(128),
                    "targetName" to targetName.trim().ifBlank { "Member" }.take(40),
                    "senderId" to uid,
                    "senderName" to senderName.trim().ifBlank { senderTier.label }.take(40),
                    "senderTier" to senderTier.name,
                    "roomId" to roomId.take(128),
                    "title" to cleanTitle,
                    "message" to cleanMsg,
                    "severity" to severity.name,
                    "acknowledged" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (roomId.isNotBlank()) {
                writeModLog(
                    roomId = roomId,
                    actorId = uid,
                    actorName = senderName,
                    targetUserId = targetUserId,
                    targetName = targetName,
                    actionType = ModActionType.SEND_ALERT,
                    reason = "${severity.label}:$cleanTitle"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminEditAlert(
        alertId: String,
        title: String,
        message: String,
        severity: AlertSeverity,
        reopenForUser: Boolean
    ): Result<Unit> {
        val path = "userAlerts/$alertId"
        return try {
            requireUserId()
            val cleanTitle = title.trim().take(60)
            val cleanMessage = message.trim().take(300)
            require(cleanTitle.isNotEmpty() && cleanMessage.isNotEmpty()) {
                "Alert title and message cannot be empty."
            }
            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "message" to cleanMessage,
                "severity" to severity.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (reopenForUser) {
                updates["acknowledged"] = false
            }
            db.collection("userAlerts").document(alertId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteAlert(alertId: String): Result<Unit> {
        val path = "userAlerts/$alertId"
        return try {
            requireUserId()
            db.collection("userAlerts").document(alertId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(alertId: String): Result<Unit> {
        val path = "userAlerts/$alertId"
        return try {
            requireUserId()
            db.collection("userAlerts").document(alertId).update(
                mapOf(
                    "acknowledged" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- USER PROFILE & ADMIN USER DIRECTORY / RESTRICTIONS / SUSPENSIONS ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user profile observation denied." }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchUserProfileForStaff(targetUserId: String): Result<UserProfile?> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            val snap = db.collection("users").document(targetUserId).get().await()
            Result.success(
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        emailPrefix: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val resolvedName: String
            val resolvedHandle: String
            val resolvedBio: String
            val resolvedColor: String
            val resolvedPhoto: String
            val resolvedDmPolicy: DmPolicy
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifBlank { "Studio Host" }.take(40)
                val rawHandle = emailPrefix.lowercase()
                    .replace(Regex("[^a-z0-9_\\-]"), "")
                    .ifBlank { "vox_${uid.take(6).lowercase()}" }
                    .take(24)
                val safeHandle = if (rawHandle.length < 2) "vox_user" else rawHandle
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "bio" to "Live on VoxStage",
                    "avatarColorHex" to "#0088FF",
                    "photoBase64" to "",
                    "pushToTalkPreferred" to true,
                    "darkThemePreferred" to false,
                    "nightModeEnabled" to false,
                    "invisibleModePreferred" to false,
                    "globallyBanned" to false,
                    "disableCreateRoom" to false,
                    "disableEnterRoom" to false,
                    "disableMic" to false,
                    "disableVideo" to false,
                    "suspendedUntilMillis" to 0L,
                    "suspensionReason" to "",
                    "pointsBalance" to 0L,
                    "creatorEarningsCents" to 0L,
                    "totalSpentCents" to 0L,
                    "dmPolicy" to DmPolicy.OPEN.name,
                    "adultVerified" to false,
                    "birthYear" to 0L,
                    "blockedUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                resolvedName = safeName
                resolvedHandle = safeHandle
                resolvedBio = "Live on VoxStage"
                resolvedColor = "#0088FF"
                resolvedPhoto = ""
                resolvedDmPolicy = DmPolicy.OPEN
            } else {
                resolvedName = snapshot.getString("displayName").orEmpty().ifBlank { displayName.trim().ifBlank { "Studio Host" } }.take(40)
                resolvedHandle = snapshot.getString("handle").orEmpty().ifBlank { "vox_user" }.take(30)
                resolvedBio = snapshot.getString("bio").orEmpty().take(200)
                resolvedColor = snapshot.getString("avatarColorHex").orEmpty().ifBlank { "#0088FF" }
                resolvedPhoto = snapshot.getString("photoBase64").orEmpty()
                resolvedDmPolicy = DmPolicy.fromRaw(snapshot.getString("dmPolicy"))
            }
            syncUserDirectoryEntry(
                userId = uid,
                displayName = resolvedName,
                handle = resolvedHandle,
                bio = resolvedBio,
                avatarColorHex = resolvedColor,
                photoBase64 = resolvedPhoto,
                staffTier = staffTier,
                dmPolicy = resolvedDmPolicy
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncUserDirectoryEntry(
        userId: String,
        displayName: String,
        handle: String,
        bio: String = "",
        avatarColorHex: String,
        photoBase64: String = "",
        staffTier: StaffTier,
        dmPolicy: DmPolicy? = null
    ) {
        val safeName = displayName.trim().ifBlank { "Member" }.take(40)
        val safeHandle = handle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
        val safeBio = bio.trim().take(200)
        val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(avatarColorHex)) avatarColorHex else "#0088FF"
        val safePhoto = if (photoBase64.length <= 140_000) photoBase64 else ""
        runCatching {
            val ref = db.collection("userDirectory").document(userId)
            val existing = ref.get().await()
            val existingPostCount = (existing.get("postCount") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val effectivePolicy = dmPolicy ?: DmPolicy.fromRaw(existing.getString("dmPolicy"))
            val isSelfSync = auth.currentUser?.uid == userId
            val effectiveOnline = if (isSelfSync) true else (existing.getBoolean("online") == true)
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "avatarColorHex" to safeColor,
                "photoBase64" to safePhoto,
                "staffTier" to staffTier.name,
                "postCount" to existingPostCount,
                "dmPolicy" to effectivePolicy.name,
                "online" to effectiveOnline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isSelfSync) {
                payload["lastActiveAt"] = FieldValue.serverTimestamp()
            } else {
                existing.getTimestamp("lastActiveAt")?.let { payload["lastActiveAt"] = it }
            }
            ref.set(payload).await()
        }
    }

    suspend fun updateOnlinePresence(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            val ref = db.collection("userDirectory").document(uid)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "online" to online,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    suspend fun updateDmPolicy(
        policy: DmPolicy,
        currentProfile: UserProfile,
        currentStaffTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "dmPolicy" to policy.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            syncUserDirectoryEntry(
                userId = uid,
                displayName = currentProfile.displayName,
                handle = currentProfile.handle,
                bio = currentProfile.bio,
                avatarColorHex = currentProfile.avatarColorHex,
                photoBase64 = currentProfile.photoBase64,
                staffTier = currentStaffTier,
                dmPolicy = policy
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun verifyUserAge18Plus(birthYear: Int): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            require(birthYear >= 1900 && birthYear <= currentYear) {
                "Please enter a valid birth year."
            }
            require(currentYear - birthYear >= 18) {
                "You must be 18 or older to lock photos or view locked photos."
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "adultVerified" to true,
                    "birthYear" to birthYear.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        nightModeEnabled: Boolean,
        invisibleModePreferred: Boolean? = null,
        currentStaffTier: StaffTier = StaffTier.USER,
        currentPhotoBase64: String = "",
        currentDmPolicy: DmPolicy = DmPolicy.OPEN
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val safeName = displayName.trim().take(40)
            val safeHandle = handle.trim().replace(" ", "_").take(30)
            val safeBio = bio.trim().take(200)
            require(safeName.isNotEmpty()) { "Display name cannot be empty." }
            require(safeHandle.length >= 2) { "Handle must be at least 2 characters." }
            require(Regex("^#[0-9A-Fa-f]{6}$").matches(avatarColorHex)) { "Invalid color hex." }

            val updates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "avatarColorHex" to avatarColorHex,
                "pushToTalkPreferred" to pushToTalkPreferred,
                "darkThemePreferred" to nightModeEnabled,
                "nightModeEnabled" to nightModeEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (invisibleModePreferred != null) {
                updates["invisibleModePreferred"] = invisibleModePreferred
            }
            db.collection("users").document(uid).update(updates).await()
            syncUserDirectoryEntry(
                userId = uid,
                displayName = safeName,
                handle = safeHandle,
                bio = safeBio,
                avatarColorHex = avatarColorHex,
                photoBase64 = currentPhotoBase64,
                staffTier = currentStaffTier,
                dmPolicy = currentDmPolicy
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfilePhoto(
        photoBase64: String,
        currentProfile: UserProfile,
        currentStaffTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(photoBase64.length <= 140_000) { "Selected photo is too large. Please pick another photo." }
            db.collection("users").document(uid).update(
                mapOf(
                    "photoBase64" to photoBase64,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            syncUserDirectoryEntry(
                userId = uid,
                displayName = currentProfile.displayName,
                handle = currentProfile.handle,
                bio = currentProfile.bio,
                avatarColorHex = currentProfile.avatarColorHex,
                photoBase64 = photoBase64,
                staffTier = currentStaffTier,
                dmPolicy = currentProfile.dmPolicyEnum()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setInvisibleModePreferred(enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "invisibleModePreferred" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminEditUserProfileAndRestrictions(
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
        suspensionReason: String,
        actorName: String,
        roomId: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$targetUserId"
        return try {
            val safeName = displayName.trim().take(40)
            val safeHandle = handle.trim().replace(" ", "_").take(30)
            val safeBio = bio.trim().take(200)
            val safeReason = suspensionReason.trim().take(200)
            require(safeName.isNotEmpty()) { "Display name cannot be empty." }
            require(safeHandle.length >= 2) { "Handle must be at least 2 characters." }

            db.collection("users").document(targetUserId).update(
                mapOf(
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "bio" to safeBio,
                    "globallyBanned" to globallyBanned,
                    "disableCreateRoom" to disableCreateRoom,
                    "disableEnterRoom" to disableEnterRoom,
                    "disableMic" to disableMic,
                    "disableVideo" to disableVideo,
                    "suspendedUntilMillis" to suspendedUntilMillis.coerceAtLeast(0L),
                    "suspensionReason" to safeReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            runCatching {
                val dirRef = db.collection("userDirectory").document(targetUserId)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "displayName" to safeName,
                            "handle" to safeHandle,
                            "bio" to safeBio,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            runCatching {
                val roleRef = db.collection("staffRoles").document(targetUserId)
                if (roleRef.get().await().exists()) {
                    roleRef.update(
                        mapOf(
                            "displayName" to safeName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            if (!roomId.isNullOrBlank()) {
                val pRef = db.collection("rooms").document(roomId)
                    .collection("participants").document(targetUserId)
                if (pRef.get().await().exists()) {
                    if (globallyBanned || disableEnterRoom || suspendedUntilMillis > System.currentTimeMillis()) {
                        pRef.delete().await()
                    } else {
                        val mediaPatch = mutableMapOf<String, Any>(
                            "displayName" to safeName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (disableMic) mediaPatch["micActive"] = false
                        if (disableVideo) mediaPatch["camActive"] = false
                        pRef.update(mediaPatch).await()
                    }
                }
                writeModLog(
                    roomId = roomId,
                    actorId = uid,
                    actorName = actorName,
                    targetUserId = targetUserId,
                    targetName = safeName,
                    actionType = ModActionType.EDIT_USER,
                    reason = "Admin updated user profile/name to \"$safeName\""
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun suspendUserForMinutes(
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier,
        durationMinutes: Long,
        reason: String,
        room: ChatRoom? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$targetUserId"
        return try {
            require(actorTier.isStaff()) { "Only Admin or Support staff can suspend users." }
            require(durationMinutes in 1L..43_200L) { "Suspension duration must be between 1 minute and 30 days." }
            val untilMillis = System.currentTimeMillis() + durationMinutes * 60_000L
            val cleanReason = reason.trim().ifBlank { "Suspended for ${durationMinutes}m by $actorName" }.take(200)

            db.collection("users").document(targetUserId).update(
                mapOf(
                    "suspendedUntilMillis" to untilMillis,
                    "suspensionReason" to cleanReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (room != null) {
                val pRef = db.collection("rooms").document(room.id)
                    .collection("participants").document(targetUserId)
                val pSnap = pRef.get().await()
                if (pSnap.exists()) {
                    val wasInvisible = pSnap.getBoolean("invisible") == true
                    pRef.delete().await()
                    if (!wasInvisible && room.participantCount > 0L) {
                        db.collection("rooms").document(room.id).update(
                            mapOf(
                                "participantCount" to FieldValue.increment(-1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                writeModLog(
                    roomId = room.id,
                    actorId = uid,
                    actorName = actorName,
                    targetUserId = targetUserId,
                    targetName = targetName,
                    actionType = ModActionType.SUSPEND,
                    reason = "Suspended ${durationMinutes}m: $cleanReason"
                )
                sendModNotice(
                    roomId = room.id,
                    actorName = actorName,
                    actorTier = actorTier,
                    text = "$targetName was suspended for ${durationMinutes}m by $actorName"
                )
            }

            sendUserAlert(
                targetUserId = targetUserId,
                targetName = targetName,
                senderName = actorName,
                senderTier = actorTier,
                roomId = room?.id ?: "",
                title = "Temporary Suspension (${durationMinutes}m)",
                message = cleanReason,
                severity = AlertSeverity.URGENT
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserBlocked(
        targetUserId: String,
        block: Boolean,
        currentBlocked: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Invalid user to block." }
            val updated = if (block) {
                (currentBlocked + targetUserId).distinct().take(100)
            } else {
                currentBlocked.filterNot { it == targetUserId }
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "blockedUserIds" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- ROOMS DISCOVERY & MANAGEMENT ---

    fun observePublicRooms(): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("visibility", RoomVisibility.PUBLIC.name)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { it.statusEnum() == RoomStatus.LIVE }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user hosted rooms query denied." }
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("hostId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { it.statusEnum() == RoomStatus.LIVE }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<ChatRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            ChatRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        hostName: String,
        hostTier: StaffTier,
        title: String,
        topic: String,
        category: RoomCategory,
        coverKey: RoomCoverKey,
        visibility: RoomVisibility,
        videoEnabled: Boolean,
        autoModEnabled: Boolean,
        autoModBlockMode: Boolean,
        linksBlocked: Boolean,
        slowModeSeconds: Long,
        maxSpeakers: Long
    ): Result<String> {
        var path = "rooms"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(60)
            val cleanTopic = topic.trim().take(160)
            val cleanHost = hostName.trim().ifBlank { "Host" }.take(40)
            require(cleanTitle.length >= 3) { "Room title must be at least 3 characters." }
            require(slowModeSeconds in listOf(0L, 3L, 10L, 30L)) { "Invalid slow mode interval." }
            require(maxSpeakers in 2L..12L) { "Max speakers must be between 2 and 12." }

            val docRef = db.collection("rooms").document()
            path = docRef.path

            val payload = mapOf(
                "hostId" to uid,
                "hostName" to cleanHost,
                "hostTier" to hostTier.name,
                "title" to cleanTitle,
                "topic" to cleanTopic,
                "category" to category.name,
                "coverKey" to coverKey.name,
                "visibility" to visibility.name,
                "status" to RoomStatus.LIVE.name,
                "videoEnabled" to videoEnabled,
                "autoModEnabled" to autoModEnabled,
                "autoModBlockMode" to autoModBlockMode,
                "linksBlocked" to linksBlocked,
                "roomLocked" to false,
                "stageMicLocked" to false,
                "adminMicPriorityActive" to false,
                "slowModeSeconds" to slowModeSeconds,
                "maxSpeakers" to maxSpeakers,
                "participantCount" to 0L,
                "moderatorIds" to listOf(uid),
                "bannedUserIds" to emptyList<String>(),
                "mutedUserIds" to emptyList<String>(),
                "blockedWords" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editRoomDetails(
        room: ChatRoom,
        actorName: String,
        actorTier: StaffTier,
        newTitle: String,
        newTopic: String
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.canEditOrCloseRoom(uid, actorTier)) {
                "Only the Room Administrator or App Administrator can change or delete the room name."
            }
            val cleanTitle = newTitle.trim().take(60)
            val cleanTopic = newTopic.trim().take(160)

            db.collection("rooms").document(room.id).update(
                mapOf(
                    "title" to cleanTitle,
                    "topic" to cleanTopic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = "",
                targetName = cleanTitle.ifBlank { "Untitled Room" }.take(40),
                actionType = ModActionType.EDIT_ROOM,
                reason = if (cleanTitle.isEmpty()) "Deleted/cleared room name" else "Renamed room to \"$cleanTitle\""
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setAdminMicPriority(roomId: String, active: Boolean): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "adminMicPriorityActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomModerationSettings(
        room: ChatRoom,
        actorName: String,
        actorTier: StaffTier,
        autoModEnabled: Boolean = room.autoModEnabled,
        autoModBlockMode: Boolean = room.autoModBlockMode,
        linksBlocked: Boolean = room.linksBlocked,
        roomLocked: Boolean = room.roomLocked,
        stageMicLocked: Boolean = room.stageMicLocked,
        slowModeSeconds: Long = room.slowModeSeconds,
        videoEnabled: Boolean = room.videoEnabled,
        blockedWords: List<String> = room.blockedWords,
        actionType: ModActionType = ModActionType.SETTINGS,
        reason: String = "Updated room safety controls"
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            require(slowModeSeconds in listOf(0L, 3L, 10L, 30L)) { "Invalid slow mode interval." }
            val cleanWords = blockedWords
                .map { it.trim().lowercase() }
                .filter { it.length in 2..30 }
                .distinct()
                .take(50)

            db.collection("rooms").document(room.id).update(
                mapOf(
                    "autoModEnabled" to autoModEnabled,
                    "autoModBlockMode" to autoModBlockMode,
                    "linksBlocked" to linksBlocked,
                    "roomLocked" to roomLocked,
                    "stageMicLocked" to stageMicLocked,
                    "slowModeSeconds" to slowModeSeconds,
                    "videoEnabled" to videoEnabled,
                    "blockedWords" to cleanWords,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = "",
                targetName = room.displayTitle().take(40),
                actionType = actionType,
                reason = reason
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun closeRoom(roomId: String, actorName: String = "Administrator"): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            val uid = requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "status" to RoomStatus.CLOSED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            try {
                writeModLog(
                    roomId = roomId,
                    actorId = uid,
                    actorName = actorName,
                    targetUserId = "",
                    targetName = roomId.take(40),
                    actionType = ModActionType.CLOSE_ROOM,
                    reason = "Closed live room"
                )
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PARTICIPANTS & STEALTH PRESENCE ---

    fun observeParticipants(roomId: String): Flow<List<RoomParticipant>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/participants"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("participants")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RoomParticipant::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<RoomParticipant> {
                            when (it.staffTierEnum()) {
                                StaffTier.ADMIN -> 0
                                StaffTier.SUPPORT -> 1
                                StaffTier.USER -> 2
                            }
                        }.thenBy {
                            when (it.roleEnum()) {
                                ParticipantRole.HOST -> 0
                                ParticipantRole.MODERATOR -> 1
                                ParticipantRole.SPEAKER -> 2
                                ParticipantRole.LISTENER -> 3
                            }
                        }.thenBy { it.joinedAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun joinRoom(
        room: ChatRoom,
        profile: UserProfile,
        staffCapabilities: StaffCapabilities,
        enterInvisible: Boolean
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "rooms/${room.id}/participants/$uid"
        return try {
            if (!staffCapabilities.isAdmin) {
                if (profile.globallyBanned) {
                    return Result.failure(IllegalStateException("Your account is banned from entering rooms."))
                }
                if (profile.currentlySuspended()) {
                    val left = profile.remainingSuspensionLabel()
                    return Result.failure(
                        IllegalStateException("You are suspended for $left. ${profile.suspensionReason}".trim())
                    )
                }
                if (profile.disableEnterRoom) {
                    return Result.failure(IllegalStateException("Entering rooms has been disabled on your account by an Administrator."))
                }
                if (room.bannedUserIds.contains(uid)) {
                    return Result.failure(IllegalStateException("You are banned from this room."))
                }
                if (room.isBouncedOut(uid)) {
                    val left = room.remainingBounceLabel(uid)
                    return Result.failure(
                        IllegalStateException("You were bounced from this room and cannot enter for $left.")
                    )
                }
            }
            if (room.roomLocked && !room.hasModPrivileges(uid, staffCapabilities.tier)) {
                return Result.failure(IllegalStateException("This room is currently locked by moderators."))
            }

            val actuallyInvisible = enterInvisible && staffCapabilities.effectiveCanEnterInvisible
            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(uid)
            val existingSnap = participantRef.get().await()

            val assignedRole = when {
                actuallyInvisible -> ParticipantRole.LISTENER
                room.hostId == uid -> ParticipantRole.HOST
                room.moderatorIds.contains(uid) || staffCapabilities.isStaff -> ParticipantRole.MODERATOR
                else -> ParticipantRole.SPEAKER
            }
            val isMuted = room.mutedUserIds.contains(uid)

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "roomId" to room.id,
                    "displayName" to profile.displayName.trim().ifBlank { "Guest" }.take(40),
                    "avatarColorHex" to profile.avatarColorHex,
                    "role" to assignedRole.name,
                    "staffTier" to staffCapabilities.tier.name,
                    "invisible" to actuallyInvisible,
                    "micActive" to false,
                    "camActive" to false,
                    "handRaised" to false,
                    "mutedByMod" to isMuted,
                    "speakingLevel" to 0.0,
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                participantRef.set(payload).await()

                if (!actuallyInvisible) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    if (!isMuted) {
                        sendMessage(
                            roomId = room.id,
                            senderName = profile.displayName,
                            senderRole = assignedRole,
                            senderTier = staffCapabilities.tier,
                            text = "${profile.displayName} joined the stage",
                            messageType = MessageType.JOIN_LEAVE,
                            flaggedByAutoMod = false
                        )
                    }
                }
            } else {
                val prevInvisible = existingSnap.getBoolean("invisible") == true
                participantRef.update(
                    mapOf(
                        "displayName" to profile.displayName.trim().ifBlank { "Guest" }.take(40),
                        "avatarColorHex" to profile.avatarColorHex,
                        "staffTier" to staffCapabilities.tier.name,
                        "invisible" to actuallyInvisible,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (prevInvisible && !actuallyInvisible) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (!prevInvisible && actuallyInvisible && room.participantCount > 0L) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(actuallyInvisible)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleInvisibleInRoom(
        room: ChatRoom,
        makeInvisible: Boolean,
        staffCapabilities: StaffCapabilities
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "rooms/${room.id}/participants/$uid"
        return try {
            if (makeInvisible) {
                require(staffCapabilities.effectiveCanEnterInvisible) {
                    "Invisible mode is not enabled for your staff account."
                }
            }
            val pRef = db.collection("rooms").document(room.id)
                .collection("participants").document(uid)
            val snap = pRef.get().await()
            if (!snap.exists()) return Result.success(makeInvisible)
            val wasInvisible = snap.getBoolean("invisible") == true
            if (wasInvisible == makeInvisible) return Result.success(makeInvisible)

            val updates = mutableMapOf<String, Any>(
                "invisible" to makeInvisible,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (makeInvisible) {
                if (!staffCapabilities.isAdmin) {
                    updates["micActive"] = false
                    updates["speakingLevel"] = 0.0
                }
                updates["camActive"] = false
                updates["handRaised"] = false
            }
            pRef.update(updates).await()

            if (makeInvisible && room.participantCount > 0L) {
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "participantCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (!makeInvisible) {
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "participantCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(makeInvisible)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String, currentParticipantCount: Long, wasInvisible: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "rooms/$roomId/participants/$uid"
        return try {
            val participantRef = db.collection("rooms").document(roomId)
                .collection("participants").document(uid)
            val snap = participantRef.get().await()
            if (snap.exists()) {
                val docInvisible = snap.getBoolean("invisible") == true || wasInvisible
                participantRef.delete().await()
                if (!docInvisible && currentParticipantCount > 0L) {
                    db.collection("rooms").document(roomId).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSelfMediaState(
        roomId: String,
        micActive: Boolean,
        camActive: Boolean,
        handRaised: Boolean,
        speakingLevel: Double
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "rooms/$roomId/participants/$uid"
        return try {
            val clampedLevel = speakingLevel.coerceIn(0.0, 1.0)
            db.collection("rooms").document(roomId)
                .collection("participants").document(uid)
                .update(
                    mapOf(
                        "micActive" to micActive,
                        "camActive" to camActive,
                        "handRaised" to handRaised,
                        "speakingLevel" to clampedLevel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- MODERATION ACTIONS (BOUNCE, MUTE, KICK, BAN, ROLE) ---

    suspend fun setParticipantMuted(
        room: ChatRoom,
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier,
        mute: Boolean,
        reason: String
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            require(targetUserId != room.hostId || actorTier.isStaff()) { "Cannot mute the room host." }

            val updatedMuted = if (mute) {
                (room.mutedUserIds + targetUserId).distinct().take(100)
            } else {
                room.mutedUserIds.filterNot { it == targetUserId }
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "mutedUserIds" to updatedMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(targetUserId)
            if (participantRef.get().await().exists()) {
                participantRef.update(
                    mapOf(
                        "mutedByMod" to mute,
                        "micActive" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetName,
                actionType = if (mute) ModActionType.MUTE else ModActionType.UNMUTE,
                reason = reason
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = if (mute) "$targetName was muted by $actorName" else "$targetName was unmuted by $actorName"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setParticipantRole(
        room: ChatRoom,
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier,
        newRole: ParticipantRole
    ): Result<Unit> {
        val path = "rooms/${room.id}/participants/$targetUserId"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            require(targetUserId != room.hostId) { "Cannot change host role." }

            if (newRole == ParticipantRole.MODERATOR || room.moderatorIds.contains(targetUserId)) {
                require(room.canManageCoAdmins(uid, actorTier)) {
                    "Only the Room Administrator or App Administrator can add or remove Room Co-Admins."
                }
                val updatedMods = if (newRole == ParticipantRole.MODERATOR) {
                    (room.moderatorIds + targetUserId).distinct().take(20)
                } else {
                    room.moderatorIds.filterNot { it == targetUserId }
                }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "moderatorIds" to updatedMods,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(targetUserId)
            if (participantRef.get().await().exists()) {
                participantRef.update(
                    mapOf(
                        "role" to newRole.name,
                        "handRaised" to false,
                        "micActive" to false,
                        "camActive" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val action = when (newRole) {
                ParticipantRole.MODERATOR -> ModActionType.PROMOTE_MOD
                ParticipantRole.SPEAKER -> ModActionType.SETTINGS
                ParticipantRole.LISTENER -> ModActionType.DEMOTE_MOD
                ParticipantRole.HOST -> ModActionType.SETTINGS
            }
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetName,
                actionType = action,
                reason = "Role changed to ${newRole.label}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun bounceParticipant(
        room: ChatRoom,
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier,
        reason: String
    ): Result<Unit> {
        val path = "rooms/${room.id}/participants/$targetUserId"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            require(targetUserId != room.hostId || actorTier.isStaff()) { "Cannot bounce the room host." }

            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(targetUserId)
            val snap = participantRef.get().await()
            if (snap.exists()) {
                val wasInvisible = snap.getBoolean("invisible") == true
                participantRef.delete().await()
                if (!wasInvisible && room.participantCount > 0L) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Admin & Support bounce imposes a 24-hour lockout on re-entering this room
            val is24hBounce = actorTier.isStaff()
            if (is24hBounce) {
                val nowMs = System.currentTimeMillis()
                val bounceUntilMs = nowMs + 24L * 60L * 60L * 1000L
                val updatedBounces = room.bouncedUntilByUserId
                    .filterValues { it > nowMs }
                    .toMutableMap()
                    .apply { put(targetUserId, bounceUntilMs) }
                    .entries
                    .sortedByDescending { it.value }
                    .take(100)
                    .associate { it.key to it.value }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "bouncedUntilByUserId" to updatedBounces,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetName,
                actionType = ModActionType.BOUNCE,
                reason = if (is24hBounce) {
                    reason.ifBlank { "Bounced for 24 hours by $actorName" }
                } else {
                    reason.ifBlank { "Bounced from room by $actorName" }
                }
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = if (is24hBounce) {
                    "$targetName was bounced from the room for 24 hours"
                } else {
                    "$targetName was bounced from the room"
                }
            )
            if (is24hBounce) {
                sendUserAlert(
                    targetUserId = targetUserId,
                    targetName = targetName,
                    senderName = actorName,
                    senderTier = actorTier,
                    roomId = room.id,
                    title = "Bounced for 24 Hours",
                    message = reason.ifBlank {
                        "You were bounced from ${room.displayTitle().take(36)} by $actorName. You cannot enter this room for 24 hours."
                    },
                    severity = AlertSeverity.WARNING
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun kickOrBanParticipant(
        room: ChatRoom,
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier,
        ban: Boolean,
        reason: String
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            if (ban) {
                require(room.canBanInRoom(uid, actorTier)) {
                    "Only the Room Administrator or App Administrator can ban users."
                }
            }
            require(targetUserId != room.hostId || actorTier == StaffTier.ADMIN) { "Cannot remove the Room Administrator." }

            if (ban) {
                val updatedBanned = (room.bannedUserIds + targetUserId).distinct().take(100)
                val updatedMuted = room.mutedUserIds.filterNot { it == targetUserId }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "bannedUserIds" to updatedBanned,
                        "mutedUserIds" to updatedMuted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(targetUserId)
            val snap = participantRef.get().await()
            if (snap.exists()) {
                val wasInvisible = snap.getBoolean("invisible") == true
                participantRef.delete().await()
                if (!wasInvisible && room.participantCount > 0L) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetName,
                actionType = if (ban) ModActionType.BAN else ModActionType.KICK,
                reason = reason.ifBlank { if (ban) "Banned by $actorName" else "Kicked from room" }
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = if (ban) "$targetName was banned from the room" else "$targetName was kicked from the room"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun unbanUser(
        room: ChatRoom,
        targetUserId: String,
        actorName: String,
        actorTier: StaffTier
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.canBanInRoom(uid, actorTier)) {
                "Only the Room Administrator or App Administrator can manage room bans."
            }
            val updatedBanned = room.bannedUserIds.filterNot { it == targetUserId }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "bannedUserIds" to updatedBanned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetUserId.take(16),
                actionType = ModActionType.UNBAN,
                reason = "Ban lifted by $actorName"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- REAL-TIME MESSAGES ---

    fun observeMessages(roomId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("messages")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        roomId: String,
        senderName: String,
        senderRole: ParticipantRole,
        senderTier: StaffTier,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        flaggedByAutoMod: Boolean = false
    ): Result<Unit> {
        var path = "rooms/$roomId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val docRef = db.collection("rooms").document(roomId)
                .collection("messages").document()
            path = docRef.path
            val payload = mapOf(
                "roomId" to roomId,
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Member" }.take(40),
                "senderRole" to senderRole.name,
                "senderTier" to senderTier.name,
                "text" to cleanText,
                "messageType" to messageType.name,
                "flaggedByAutoMod" to flaggedByAutoMod,
                "deletedByMod" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleRoomMessageReaction(
        roomId: String,
        message: ChatMessage,
        emoji: String
    ): Result<Unit> {
        val path = "rooms/$roomId/messages/${message.id}"
        return try {
            val uid = requireUserId()
            require(!message.deletedByMod) { "Cannot react to a removed message." }
            require(emoji in ROOM_REACTION_EMOJIS) { "Unsupported reaction emoji." }
            val msgRef = db.collection("rooms").document(roomId)
                .collection("messages").document(message.id)

            db.runTransaction { txn ->
                val snap = txn.get(msgRef)
                if (!snap.exists()) return@runTransaction null
                val rawMap = snap.get("reactionsByEmoji") as? Map<*, *>
                val nextMap = mutableMapOf<String, List<String>>()
                rawMap?.forEach { (k, v) ->
                    val key = k as? String
                    val uids = (v as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (key != null && uids.isNotEmpty()) {
                        nextMap[key] = uids
                    }
                }
                val currentUids = nextMap[emoji].orEmpty()
                val updatedUids = if (currentUids.contains(uid)) {
                    currentUids.filter { it != uid }
                } else {
                    (currentUids + uid).distinct().take(200)
                }
                if (updatedUids.isEmpty()) {
                    nextMap.remove(emoji)
                } else {
                    nextMap[emoji] = updatedUids
                }
                txn.update(
                    msgRef,
                    mapOf(
                        "reactionsByEmoji" to nextMap,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun sendModNotice(
        roomId: String,
        actorName: String,
        actorTier: StaffTier,
        text: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val docRef = db.collection("rooms").document(roomId)
                .collection("messages").document()
            docRef.set(
                mapOf(
                    "roomId" to roomId,
                    "senderId" to uid,
                    "senderName" to actorName.trim().ifBlank { "Moderator" }.take(40),
                    "senderRole" to "SYSTEM",
                    "senderTier" to actorTier.name,
                    "text" to text.take(500),
                    "messageType" to MessageType.MOD_NOTICE.name,
                    "flaggedByAutoMod" to false,
                    "deletedByMod" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rooms/$roomId/messages")
        }
    }

    suspend fun redactMessage(
        room: ChatRoom,
        message: ChatMessage,
        actorName: String,
        actorTier: StaffTier
    ): Result<Unit> {
        val path = "rooms/${room.id}/messages/${message.id}"
        return try {
            val uid = requireUserId()
            require(room.hasModPrivileges(uid, actorTier)) { "Moderator or staff privileges required." }
            db.collection("rooms").document(room.id)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "text" to "[Message removed by moderator]",
                        "deletedByMod" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = message.senderId,
                targetName = message.senderName,
                actionType = ModActionType.DELETE_MSG,
                reason = "Redacted chat message"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- MODERATION AUDIT LOGS ---

    fun observeModLogs(roomId: String): Flow<List<ModerationLog>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/modLogs"
        emitAll(
            db.collection("rooms").document(roomId)
                .collection("modLogs")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ModerationLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logAutoModTrigger(
        roomId: String,
        actorName: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            writeModLog(
                roomId = roomId,
                actorId = uid,
                actorName = actorName,
                targetUserId = uid,
                targetName = actorName,
                actionType = ModActionType.AUTO_MOD,
                reason = reason
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rooms/$roomId/modLogs")
            Result.failure(e)
        }
    }

    private suspend fun writeModLog(
        roomId: String,
        actorId: String,
        actorName: String,
        targetUserId: String,
        targetName: String,
        actionType: ModActionType,
        reason: String
    ) {
        val docRef = db.collection("rooms").document(roomId)
            .collection("modLogs").document()
        docRef.set(
            mapOf(
                "roomId" to roomId,
                "actorId" to actorId,
                "actorName" to actorName.trim().ifBlank { "Moderator" }.take(40),
                "targetUserId" to targetUserId.take(128),
                "targetName" to targetName.trim().take(40),
                "actionType" to actionType.name,
                "reason" to reason.trim().take(200),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // --- SAFETY REPORTS ---

    fun observeSafetyReports(userId: String, isBootstrappedAdmin: Boolean): Flow<List<SafetyReport>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user safety reports query denied." }

        if (isBootstrappedAdmin) {
            emitAll(
                db.collection("safetyReports")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(
                            SafetyReport::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { error ->
                        val unwrapped = unwrapFirestoreError(error)
                        if (auth.currentUser?.uid == uid) {
                            handleFirestoreError(unwrapped, OperationType.LIST, "safetyReports")
                        }
                        throw error
                    }
            )
            return@flow
        }

        val filedFlow = db.collection("safetyReports")
            .whereEqualTo("reporterId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    SafetyReport::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapped, OperationType.LIST, "safetyReports(reporterId)")
                }
                throw error
            }

        val hostedFlow = db.collection("safetyReports")
            .whereEqualTo("roomHostId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    SafetyReport::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapped, OperationType.LIST, "safetyReports(roomHostId)")
                }
                throw error
            }

        emitAll(
            combine(filedFlow, hostedFlow) { filed, hosted ->
                (filed + hosted)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun submitSafetyReport(
        room: ChatRoom,
        reporterName: String,
        targetUserId: String,
        targetName: String,
        category: ReportCategory,
        details: String
    ): Result<Unit> {
        var path = "safetyReports"
        return try {
            val uid = requireUserId()
            val cleanDetails = details.trim().take(400)
            require(cleanDetails.isNotEmpty()) { "Please describe the safety issue." }
            require(targetUserId.isNotBlank()) { "Target user ID required." }

            val docRef = db.collection("safetyReports").document()
            path = docRef.path
            val payload = mapOf(
                "reporterId" to uid,
                "reporterName" to reporterName.trim().ifBlank { "Member" }.take(40),
                "roomId" to room.id,
                "roomTitle" to room.displayTitle().take(60),
                "roomHostId" to room.hostId,
                "targetUserId" to targetUserId.take(128),
                "targetName" to targetName.trim().ifBlank { "User" }.take(40),
                "category" to category.name,
                "details" to cleanDetails,
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveSafetyReport(
        reportId: String,
        newStatus: ReportStatus,
        resolutionNote: String
    ): Result<Unit> {
        val path = "safetyReports/$reportId"
        return try {
            requireUserId()
            require(newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
                "Report can only transition to RESOLVED or DISMISSED."
            }
            db.collection("safetyReports").document(reportId).update(
                mapOf(
                    "status" to newStatus.name,
                    "resolutionNote" to resolutionNote.trim().take(200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PUBLIC USER DIRECTORY, FRIENDS LIST & PRIVATE DIRECT MESSAGES ---

    fun observeUserDirectory(): Flow<List<DirectoryEntry>> = flow {
        val uid = requireUserId()
        val path = "userDirectory"
        emitAll(
            db.collection("userDirectory")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DirectoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyFriendships(userId: String): Flow<List<Friendship>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user friendship observation denied." }
        val path = "friendships"
        emitAll(
            db.collection("friendships")
                .whereArrayContains("userIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        Friendship::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendFriendRequest(
        myProfile: UserProfile,
        myTier: StaffTier,
        target: DirectoryEntry
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val pairId = canonicalPairId(uid, target.userId)
        val path = "friendships/$pairId"
        return try {
            require(target.userId != uid) { "You cannot add yourself as a friend." }
            val ref = db.collection("friendships").document(pairId)
            val existing = ref.get().await()
            if (existing.exists()) {
                val status = existing.getString("status")
                if (status == FriendshipStatus.ACCEPTED.name) {
                    return Result.failure(IllegalStateException("You are already friends with ${target.displayName}."))
                }
                return Result.failure(IllegalStateException("A friend request is already pending."))
            }

            ref.set(
                mapOf(
                    "userIds" to listOf(uid, target.userId),
                    "requesterId" to uid,
                    "requesterName" to myProfile.displayName.trim().ifBlank { "Member" }.take(40),
                    "requesterHandle" to myProfile.handle.trim().ifBlank { "member" }.take(30),
                    "requesterColorHex" to myProfile.avatarColorHex,
                    "requesterTier" to myTier.name,
                    "recipientId" to target.userId,
                    "recipientName" to target.displayName.trim().ifBlank { "Member" }.take(40),
                    "recipientHandle" to target.handle.trim().ifBlank { "member" }.take(30),
                    "recipientColorHex" to target.avatarColorHex,
                    "recipientTier" to target.tierEnum().name,
                    "status" to FriendshipStatus.PENDING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(
        friendshipId: String,
        myProfile: UserProfile,
        myTier: StaffTier
    ): Result<Unit> {
        val path = "friendships/$friendshipId"
        return try {
            requireUserId()
            db.collection("friendships").document(friendshipId).update(
                mapOf(
                    "status" to FriendshipStatus.ACCEPTED.name,
                    "recipientName" to myProfile.displayName.trim().ifBlank { "Member" }.take(40),
                    "recipientHandle" to myProfile.handle.trim().ifBlank { "member" }.take(30),
                    "recipientColorHex" to myProfile.avatarColorHex,
                    "recipientTier" to myTier.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeOrDeclineFriendship(friendshipId: String): Result<Unit> {
        val path = "friendships/$friendshipId"
        return try {
            requireUserId()
            db.collection("friendships").document(friendshipId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeMyDirectThreads(userId: String): Flow<List<DirectThread>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user direct thread observation denied." }
        val path = "directThreads"
        emitAll(
            db.collection("directThreads")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DirectThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun openOrCreateDirectThread(
        myProfile: UserProfile,
        myTier: StaffTier,
        otherUserId: String,
        otherName: String,
        otherColorHex: String,
        otherTier: StaffTier
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val threadId = canonicalPairId(uid, otherUserId)
        val path = "directThreads/$threadId"
        return try {
            require(otherUserId != uid) { "Cannot start a private chat with yourself." }
            val ref = db.collection("directThreads").document(threadId)
            val snap = ref.get().await()
            val myName = myProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val myColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val cleanOtherName = otherName.trim().ifBlank { "Member" }.take(40)
            val cleanOtherColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(otherColorHex)) otherColorHex else "#0088FF"

            val meFirst = uid <= otherUserId
            if (!snap.exists()) {
                val payload = mapOf(
                    "participantIds" to if (meFirst) listOf(uid, otherUserId) else listOf(otherUserId, uid),
                    "isGroup" to false,
                    "groupName" to "",
                    "participantNamesById" to mapOf(uid to myName, otherUserId to cleanOtherName),
                    "kickedUserIds" to emptyList<String>(),
                    "userAId" to if (meFirst) uid else otherUserId,
                    "userAName" to if (meFirst) myName else cleanOtherName,
                    "userAColorHex" to if (meFirst) myColor else cleanOtherColor,
                    "userATier" to (if (meFirst) myTier else otherTier).name,
                    "userBId" to if (meFirst) otherUserId else uid,
                    "userBName" to if (meFirst) cleanOtherName else myName,
                    "userBColorHex" to if (meFirst) cleanOtherColor else myColor,
                    "userBTier" to (if (meFirst) otherTier else myTier).name,
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "unreadByUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            } else {
                val patch = if (meFirst) {
                    mapOf(
                        "userAName" to myName,
                        "userAColorHex" to myColor,
                        "userATier" to myTier.name,
                        "userBName" to cleanOtherName,
                        "userBColorHex" to cleanOtherColor,
                        "userBTier" to otherTier.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                } else {
                    mapOf(
                        "userAName" to cleanOtherName,
                        "userAColorHex" to cleanOtherColor,
                        "userATier" to otherTier.name,
                        "userBName" to myName,
                        "userBColorHex" to myColor,
                        "userBTier" to myTier.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                }
                runCatching { ref.update(patch).await() }
            }
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Adding another user to a 1-on-1 private message seamlessly creates a Group Chat with all 3 users.
     * Adding a user to an existing Group Chat appends them in-place (up to 15 members).
     */
    suspend fun addUserToDirectChatOrCreateGroup(
        currentThread: DirectThread,
        myProfile: UserProfile,
        myTier: StaffTier,
        addedUser: DirectoryEntry
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            require(addedUser.userId.isNotBlank() && addedUser.userId != uid) {
                "Choose another member to add."
            }
            require(!currentThread.participantIds.contains(addedUser.userId)) {
                "${addedUser.displayName} is already in this chat."
            }
            val myName = myProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val myColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val addedName = addedUser.displayName.trim().ifBlank { "Member" }.take(40)

            if (!currentThread.isGroup) {
                val otherUid = currentThread.otherUserId(uid)
                val otherName = currentThread.otherName(uid).trim().ifBlank { "Member" }.take(40)
                val otherColor = currentThread.otherColorHex(uid)
                val otherTier = currentThread.otherTier(uid)

                val members = listOf(uid, otherUid, addedUser.userId).distinct()
                val namesMap = mapOf(
                    uid to myName,
                    otherUid to otherName,
                    addedUser.userId to addedName
                )
                val title = "$myName, $otherName & $addedName".take(60)
                val groupRef = db.collection("directThreads").document()
                val introText = "$myName added $addedName · Group chat created"
                groupRef.set(
                    mapOf(
                        "participantIds" to members,
                        "isGroup" to true,
                        "groupName" to title,
                        "participantNamesById" to namesMap,
                        "kickedUserIds" to emptyList<String>(),
                        "userAId" to uid,
                        "userAName" to myName,
                        "userAColorHex" to myColor,
                        "userATier" to myTier.name,
                        "userBId" to otherUid,
                        "userBName" to otherName,
                        "userBColorHex" to otherColor,
                        "userBTier" to otherTier.name,
                        "lastMessageText" to introText.take(300),
                        "lastMessageSenderId" to uid,
                        "unreadByUserIds" to members.filter { it != uid },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                runCatching {
                    groupRef.collection("messages").document().set(
                        mapOf(
                            "threadId" to groupRef.id,
                            "participantIds" to members,
                            "senderId" to uid,
                            "senderName" to myName,
                            "senderTier" to myTier.name,
                            "text" to introText.take(500),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                Result.success(groupRef.id)
            } else {
                require(currentThread.participantIds.size < 15) {
                    "Group chats support up to 15 members."
                }
                val updatedIds = (currentThread.participantIds + addedUser.userId).distinct()
                val updatedNames = currentThread.participantNamesById.toMutableMap().apply {
                    put(uid, myName)
                    put(addedUser.userId, addedName)
                }
                val noticeText = "$myName added $addedName to the group"
                val threadRef = db.collection("directThreads").document(currentThread.id)
                threadRef.update(
                    mapOf(
                        "participantIds" to updatedIds,
                        "participantNamesById" to updatedNames,
                        "lastMessageText" to noticeText.take(300),
                        "lastMessageSenderId" to uid,
                        "unreadByUserIds" to updatedIds.filter { it != uid },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                runCatching {
                    threadRef.collection("messages").document().set(
                        mapOf(
                            "threadId" to currentThread.id,
                            "participantIds" to updatedIds,
                            "senderId" to uid,
                            "senderName" to myName,
                            "senderTier" to myTier.name,
                            "text" to noticeText.take(500),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                Result.success(currentThread.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "directThreads/${currentThread.id}")
            Result.failure(e)
        }
    }

    fun observeMyDmAccessPasses(userId: String): Flow<List<DmAccessPass>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user DM pass observation denied." }
        val path = "dmAccessPasses"
        emitAll(
            db.collection("dmAccessPasses")
                .whereEqualTo("senderId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DmAccessPass::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePaidDmMembersForMe(userId: String): Flow<List<DmAccessPass>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user paid DM members observation denied." }
        val path = "dmAccessPasses"
        emitAll(
            db.collection("dmAccessPasses")
                .whereEqualTo("recipientId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DmAccessPass::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Pays $10 to unlock private messaging with a user whose DMs are locked to paid members.
     * Split: $5 (500c) goes to the recipient user, $5 (500c) goes to the app owner.
     */
    suspend fun unlockPaidDirectMessage(
        recipientId: String,
        recipientName: String,
        buyerName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val passId = "${uid}_${recipientId}"
        val path = "dmAccessPasses/$passId"
        return try {
            require(recipientId.isNotBlank() && recipientId != uid) {
                "Cannot pay to message yourself."
            }
            val passRef = db.collection("dmAccessPasses").document(passId)
            val existing = passRef.get().await()
            if (existing.exists()) {
                val kicked = existing.getBoolean("kicked") == true
                val active = existing.getBoolean("active") == true
                if (kicked) {
                    return Result.failure(IllegalStateException("$recipientName removed you from private messaging them."))
                }
                if (active) {
                    return Result.success(Unit)
                }
            }

            val buyerRef = db.collection("users").document(uid)
            val recipientRef = db.collection("users").document(recipientId)
            val txRef = db.collection("pointTransactions").document()
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val safeRecipientName = recipientName.trim().ifBlank { "Member" }.take(40)

            val batch = db.batch()
            batch.set(
                passRef,
                mapOf(
                    "senderId" to uid,
                    "senderName" to safeBuyerName,
                    "recipientId" to recipientId,
                    "recipientName" to safeRecipientName,
                    "priceCents" to 1000L,
                    "creatorShareCents" to 500L,
                    "platformShareCents" to 500L,
                    "active" to true,
                    "kicked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                buyerRef,
                mapOf(
                    "totalSpentCents" to FieldValue.increment(1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                recipientRef,
                mapOf(
                    "creatorEarningsCents" to FieldValue.increment(500L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.UNLOCK_DM.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to recipientId,
                    "creatorName" to safeRecipientName,
                    "postId" to "",
                    "pointsDelta" to 0L,
                    "grossCents" to 1000L,
                    "creatorShareCents" to 500L,
                    "platformShareCents" to 500L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Kicks a paid member from private messaging the current user.
     * Revokes their dmAccessPass (active = false, kicked = true) and adds them to kickedUserIds on their 1-on-1 DM thread.
     */
    suspend fun kickPaidDmMember(
        senderId: String,
        senderName: String,
        myName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val passId = "${senderId}_${uid}"
        val path = "dmAccessPasses/$passId"
        return try {
            val passRef = db.collection("dmAccessPasses").document(passId)
            val passSnap = passRef.get().await()
            if (passSnap.exists()) {
                passRef.update(
                    mapOf(
                        "active" to false,
                        "kicked" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val pairId = canonicalPairId(uid, senderId)
            val threadRef = db.collection("directThreads").document(pairId)
            val threadSnap = threadRef.get().await()
            if (threadSnap.exists()) {
                val existingKicked = (threadSnap.get("kickedUserIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                val updatedKicked = (existingKicked + senderId).distinct().take(20)
                threadRef.update(
                    mapOf(
                        "kickedUserIds" to updatedKicked,
                        "lastMessageText" to "${senderName.take(30)} was removed from messaging ${myName.take(30)}",
                        "lastMessageSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeDirectThread(threadId: String): Flow<DirectThread?> = flow {
        val uid = requireUserId()
        val path = "directThreads/$threadId"
        emitAll(
            db.collection("directThreads").document(threadId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            DirectThread::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDirectMessages(threadId: String): Flow<List<DirectMessage>> = flow {
        val uid = requireUserId()
        val path = "directThreads/$threadId/messages"
        emitAll(
            db.collection("directThreads").document(threadId)
                .collection("messages")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DirectMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendDirectMessage(
        thread: DirectThread,
        senderName: String,
        senderTier: StaffTier,
        text: String,
        imageBase64: String = "",
        audioBase64: String = "",
        audioDurationSec: Long = 0L
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanText = text.trim().take(500)
        val cleanImage = imageBase64.trim()
        val cleanAudio = audioBase64.trim()
        val safeAudioDuration = audioDurationSec.coerceIn(0L, 60L)
        if (cleanText.isEmpty() && cleanImage.isEmpty() && cleanAudio.isEmpty()) return Result.success(Unit)
        val msgRef = db.collection("directThreads").document(thread.id)
            .collection("messages").document()
        val path = msgRef.path
        return try {
            require(!thread.isUserKicked(uid)) { "You have been removed from messaging in this chat." }
            require(cleanImage.length <= 460_000) { "Picture is too large to send. Please try again." }
            require(cleanAudio.length <= 300_000) { "Voice note is too long. Please keep voice notes under 30 seconds." }
            val safeName = senderName.trim().ifBlank { "Member" }.take(40)
            val payload = mutableMapOf<String, Any>(
                "threadId" to thread.id,
                "participantIds" to thread.participantIds,
                "senderId" to uid,
                "senderName" to safeName,
                "senderTier" to senderTier.name,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (cleanImage.isNotEmpty()) {
                payload["imageBase64"] = cleanImage
            }
            if (cleanAudio.isNotEmpty()) {
                payload["audioBase64"] = cleanAudio
                payload["audioDurationSec"] = safeAudioDuration.coerceAtLeast(1L)
            }
            msgRef.set(payload).await()

            val previewText = when {
                cleanAudio.isNotEmpty() && cleanText.isNotEmpty() -> "🎤 $cleanText"
                cleanAudio.isNotEmpty() -> "🎤 Voice note (${safeAudioDuration.coerceAtLeast(1L)}s)"
                cleanImage.isNotEmpty() && cleanText.isNotEmpty() -> "📷 $cleanText"
                cleanImage.isNotEmpty() -> "📷 Sent a snap"
                else -> cleanText
            }.take(300)

            val unreadRecipients = thread.participantIds.filter { it != uid }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageSenderId" to uid,
                    "unreadByUserIds" to unreadRecipients,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markDirectThreadRead(thread: DirectThread): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (!thread.unreadByUserIds.contains(uid)) return Result.success(Unit)
        val path = "directThreads/${thread.id}"
        return try {
            val updatedUnread = thread.unreadByUserIds.filter { it != uid }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "unreadByUserIds" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PUBLIC USER PROFILES & PROFILE POSTS (UPDATES, PICTURES, TEXT) ---

    fun observeDirectoryEntry(userId: String): Flow<DirectoryEntry?> = flow {
        val uid = requireUserId()
        val path = "userDirectory/$userId"
        emitAll(
            db.collection("userDirectory").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            DirectoryEntry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePostsByUser(targetUserId: String): Flow<List<ProfilePost>> = flow {
        val uid = requireUserId()
        val path = "profilePosts"
        emitAll(
            db.collection("profilePosts")
                .whereEqualTo("authorId", targetUserId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ProfilePost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRecentProfilePosts(): Flow<List<ProfilePost>> = flow {
        val uid = requireUserId()
        val path = "profilePosts"
        emitAll(
            db.collection("profilePosts")
                .snapshots()
                .map { snap ->
                    val nowMs = System.currentTimeMillis()
                    snap.toObjects(
                        ProfilePost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<ProfilePost> { it.isCurrentlyBoosted(nowMs) }
                            .thenByDescending { if (it.isCurrentlyBoosted(nowMs)) it.boostedAtMillis else 0L }
                            .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    ).take(80)
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProfilePost(
        myProfile: UserProfile,
        myTier: StaffTier,
        text: String,
        imageBase64: String,
        locked: Boolean = false,
        boostToTop: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("profilePosts").document()
        val path = docRef.path
        return try {
            val cleanText = text.trim().take(1000)
            val cleanImage = imageBase64.trim()
            require(cleanText.isNotEmpty() || cleanImage.isNotEmpty()) {
                "Write an update or attach a picture before posting."
            }
            require(cleanImage.length <= 460_000) {
                "Image is too large to post. Try selecting another photo."
            }
            val effectiveLocked = locked && cleanImage.isNotEmpty()
            if (effectiveLocked) {
                require(myProfile.isAdult18Plus()) {
                    "You must verify you are 18+ before locking photos."
                }
            }
            val safeName = myProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val safeHandle = myProfile.handle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val safeAvatarPhoto = if (myProfile.photoBase64.length <= 140_000) myProfile.photoBase64 else ""

            val nowMs = System.currentTimeMillis()
            val boostedAt = if (boostToTop) nowMs else 0L
            val boostedUntil = if (boostToTop) nowMs + 24L * 60L * 60L * 1000L else 0L

            val postPayload = mapOf(
                "authorId" to uid,
                "authorName" to safeName,
                "authorHandle" to safeHandle,
                "authorColorHex" to safeColor,
                "authorPhotoBase64" to safeAvatarPhoto,
                "authorTier" to myTier.name,
                "text" to cleanText,
                "imageBase64" to cleanImage,
                "locked" to effectiveLocked,
                "unlockPricePoints" to 2L,
                "unlockedByUserIds" to emptyList<String>(),
                "boostedAtMillis" to boostedAt,
                "boostedUntilMillis" to boostedUntil,
                "likedByUserIds" to emptyList<String>(),
                "commentCount" to 0L,
                "shareCount" to 0L,
                "sharedFromPostId" to "",
                "sharedFromAuthorId" to "",
                "sharedFromAuthorName" to "",
                "sharedFromAuthorHandle" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (boostToTop) {
                val userRef = db.collection("users").document(uid)
                val userSnap = userRef.get().await()
                val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(currentPoints >= 5L) {
                    "Not enough points. Boosting to the top of Discovery costs 5 points (\$5) — buy points in Profile Settings."
                }
                val txRef = db.collection("pointTransactions").document()
                val batch = db.batch()
                batch.update(
                    userRef,
                    mapOf(
                        "pointsBalance" to FieldValue.increment(-5L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(docRef, postPayload)
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.BOOST_POST.name,
                        "buyerId" to uid,
                        "buyerName" to safeName,
                        "creatorId" to "",
                        "creatorName" to "",
                        "postId" to docRef.id,
                        "pointsDelta" to -5L,
                        "grossCents" to 500L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 500L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } else {
                docRef.set(postPayload).await()
            }

            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "postCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Pays 5 points ($5) atomically to boost an existing post to the top of Discovery for 24 hours.
     */
    suspend fun boostProfilePost(
        post: ProfilePost,
        buyerName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "profilePosts/${post.id}"
        return try {
            require(post.authorId == uid) { "You can only boost your own posts." }
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            require(currentPoints >= 5L) {
                "Not enough points. Boosting a post to the top of Discovery costs 5 points (\$5) — buy points in Profile Settings."
            }

            val nowMs = System.currentTimeMillis()
            val boostUntilMs = nowMs + 24L * 60L * 60L * 1000L
            val postRef = db.collection("profilePosts").document(post.id)
            val txRef = db.collection("pointTransactions").document()
            val safeName = buyerName.trim().ifBlank { "Member" }.take(40)

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-5L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "boostedAtMillis" to nowMs,
                    "boostedUntilMillis" to boostUntilMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.BOOST_POST.name,
                    "buyerId" to uid,
                    "buyerName" to safeName,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to post.id,
                    "pointsDelta" to -5L,
                    "grossCents" to 500L,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 500L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observePostComments(postId: String): Flow<List<PostComment>> = flow {
        val uid = requireUserId()
        val path = "profilePosts/$postId/comments"
        emitAll(
            db.collection("profilePosts")
                .document(postId)
                .collection("comments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PostComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addPostComment(
        post: ProfilePost,
        myProfile: UserProfile,
        myTier: StaffTier,
        text: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanText = text.trim().take(500)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Write a comment before sending."))
        }
        val postRef = db.collection("profilePosts").document(post.id)
        val commentRef = postRef.collection("comments").document()
        val path = commentRef.path
        return try {
            val safeName = myProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val safeHandle = myProfile.handle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val safeAvatarPhoto = if (myProfile.photoBase64.length <= 140_000) myProfile.photoBase64 else ""

            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "postId" to post.id,
                    "authorId" to uid,
                    "authorName" to safeName,
                    "authorHandle" to safeHandle,
                    "authorColorHex" to safeColor,
                    "authorPhotoBase64" to safeAvatarPhoto,
                    "authorTier" to myTier.name,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePostComment(
        postId: String,
        commentId: String
    ): Result<Unit> {
        val postRef = db.collection("profilePosts").document(postId)
        val commentRef = postRef.collection("comments").document(commentId)
        val path = commentRef.path
        return try {
            commentRef.delete().await()
            runCatching {
                val snap = postRef.get().await()
                val current = (snap.get("commentCount") as? Number)?.toLong() ?: 0L
                if (snap.exists() && current > 0L) {
                    postRef.update(
                        mapOf(
                            "commentCount" to (current - 1L).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Reposts an unlocked post/picture onto the signed-in user's profile & Discovery feed.
     * Locked pictures are strictly blocked both here and in Firestore Security Rules.
     */
    suspend fun shareProfilePostToFeed(
        originalPost: ProfilePost,
        myProfile: UserProfile,
        myTier: StaffTier,
        note: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val newPostRef = db.collection("profilePosts").document()
        val path = newPostRef.path
        return try {
            require(!originalPost.locked) { "Locked pictures cannot be shared." }
            val safeName = myProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val safeHandle = myProfile.handle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val safeAvatarPhoto = if (myProfile.photoBase64.length <= 140_000) myProfile.photoBase64 else ""

            val cleanNote = note.trim().take(280)
            val combinedText = when {
                cleanNote.isNotEmpty() && originalPost.text.isNotBlank() ->
                    "$cleanNote\n\n${originalPost.text}".take(1000)
                cleanNote.isNotEmpty() -> cleanNote.take(1000)
                else -> originalPost.text.take(1000)
            }
            val origRef = db.collection("profilePosts").document(originalPost.id)

            val batch = db.batch()
            batch.set(
                newPostRef,
                mapOf(
                    "authorId" to uid,
                    "authorName" to safeName,
                    "authorHandle" to safeHandle,
                    "authorColorHex" to safeColor,
                    "authorPhotoBase64" to safeAvatarPhoto,
                    "authorTier" to myTier.name,
                    "text" to combinedText,
                    "imageBase64" to originalPost.imageBase64,
                    "locked" to false,
                    "unlockPricePoints" to 2L,
                    "unlockedByUserIds" to emptyList<String>(),
                    "boostedAtMillis" to 0L,
                    "boostedUntilMillis" to 0L,
                    "likedByUserIds" to emptyList<String>(),
                    "commentCount" to 0L,
                    "shareCount" to 0L,
                    "sharedFromPostId" to originalPost.id,
                    "sharedFromAuthorId" to originalPost.authorId,
                    "sharedFromAuthorName" to originalPost.authorName.take(40),
                    "sharedFromAuthorHandle" to originalPost.authorHandle.take(30),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                origRef,
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "postCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun recordExternalPostShare(post: ProfilePost): Result<Unit> {
        val path = "profilePosts/${post.id}"
        return try {
            require(!post.locked) { "Locked pictures cannot be shared." }
            db.collection("profilePosts").document(post.id).update(
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLikeProfilePost(post: ProfilePost): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "profilePosts/${post.id}"
        return try {
            val currentlyLiked = post.likedByUserIds.contains(uid)
            val updatedLikes = if (currentlyLiked) {
                post.likedByUserIds.filter { it != uid }
            } else {
                (post.likedByUserIds + uid).distinct().take(500)
            }
            db.collection("profilePosts").document(post.id).update(
                mapOf(
                    "likedByUserIds" to updatedLikes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProfilePost(post: ProfilePost): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "profilePosts/${post.id}"
        return try {
            db.collection("profilePosts").document(post.id).delete().await()
            if (post.authorId == uid) {
                runCatching {
                    val dirRef = db.collection("userDirectory").document(uid)
                    val snap = dirRef.get().await()
                    val currentCount = (snap.get("postCount") as? Number)?.toLong() ?: 0L
                    if (snap.exists() && currentCount > 0L) {
                        dirRef.update(
                            mapOf(
                                "postCount" to (currentCount - 1L).coerceAtLeast(0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- POINTS WALLET, LOCKED PHOTO UNLOCKS ($1 CREATOR / $1 APP OWNER) & $15/MO SUBSCRIPTIONS ---

    fun observeMySubscriptions(userId: String): Flow<List<CreatorSubscription>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user subscription observation denied." }
        val path = "creatorSubscriptions"
        emitAll(
            db.collection("creatorSubscriptions")
                .whereEqualTo("subscriberId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CreatorSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.expiresAtMillis }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyTransactions(userId: String): Flow<List<PointTransaction>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user transaction observation denied." }
        val buyerFlow = db.collection("pointTransactions")
            .whereEqualTo("buyerId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PointTransaction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapped, OperationType.LIST, "pointTransactions(buyer)")
                }
                throw error
            }
        val creatorFlow = db.collection("pointTransactions")
            .whereEqualTo("creatorId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    PointTransaction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapped, OperationType.LIST, "pointTransactions(creator)")
                }
                throw error
            }
        emitAll(
            combine(buyerFlow, creatorFlow) { bought, earned ->
                (bought + earned)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    .take(60)
            }
        )
    }

    fun observeAllTransactionsForAdmin(): Flow<List<PointTransaction>> = flow {
        val uid = requireUserId()
        val path = "pointTransactions"
        emitAll(
            db.collection("pointTransactions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PointTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .take(150)
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Buys points at $1 per point (2 points = $2). Atomically increments user's pointsBalance
     * and totalSpentCents, and logs an immutable BUY_POINTS ledger entry.
     */
    suspend fun buyPoints(
        pointsToBuy: Long,
        buyerName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(pointsToBuy in 2L..500L) { "Select between 2 and 500 points." }
            val costCents = pointsToBuy * 100L
            val userRef = db.collection("users").document(uid)
            val txRef = db.collection("pointTransactions").document()
            val safeName = buyerName.trim().ifBlank { "Member" }.take(40)

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(pointsToBuy),
                    "totalSpentCents" to FieldValue.increment(costCents),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.BUY_POINTS.name,
                    "buyerId" to uid,
                    "buyerName" to safeName,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to "",
                    "pointsDelta" to pointsToBuy,
                    "grossCents" to costCents,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Unlocks a locked profile photo for 2 points ($2):
     * - Deducts 2 points from buyer
     * - Adds buyer to post's unlockedByUserIds
     * - Credits $1.00 (100c) to the photo owner (creator)
     * - Logs $1.00 (100c) platform share to app owner in pointTransactions
     */
    suspend fun unlockProfilePost(
        post: ProfilePost,
        buyerName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "profilePosts/${post.id}"
        return try {
            require(post.locked) { "This photo is already public." }
            require(post.authorId != uid) { "You own this photo." }
            if (post.unlockedByUserIds.contains(uid)) return Result.success(Unit)

            val buyerRef = db.collection("users").document(uid)
            val buyerSnap = buyerRef.get().await()
            val isAdult = buyerSnap.getBoolean("adultVerified") == true
            require(isAdult) {
                "You must verify you are 18+ to unlock or view locked photos."
            }
            val currentPoints = (buyerSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            require(currentPoints >= 2L) {
                "Not enough points. You need 2 points ($2) to unlock this photo — buy points in Profile Settings."
            }

            val postRef = db.collection("profilePosts").document(post.id)
            val creatorRef = db.collection("users").document(post.authorId)
            val txRef = db.collection("pointTransactions").document()
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val safeCreatorName = post.authorName.trim().ifBlank { "Creator" }.take(40)
            val updatedUnlocked = (post.unlockedByUserIds + uid).distinct().take(500)

            val batch = db.batch()
            batch.update(
                buyerRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-2L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "unlockedByUserIds" to updatedUnlocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                creatorRef,
                mapOf(
                    "creatorEarningsCents" to FieldValue.increment(100L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.UNLOCK_PHOTO.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to post.authorId,
                    "creatorName" to safeCreatorName,
                    "postId" to post.id,
                    "pointsDelta" to -2L,
                    "grossCents" to 200L,
                    "creatorShareCents" to 100L,
                    "platformShareCents" to 100L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Subscribes the signed-in user to a creator for $15/month (30 days),
     * unlocking ALL of that creator's locked photos. Credits $7.50 to creator and logs $7.50 to app owner.
     */
    suspend fun subscribeToCreator(
        creatorId: String,
        creatorName: String,
        buyerName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val subId = "${uid}_$creatorId"
        val path = "creatorSubscriptions/$subId"
        return try {
            require(creatorId.isNotBlank() && creatorId != uid) { "Cannot subscribe to yourself." }
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val safeCreatorName = creatorName.trim().ifBlank { "Creator" }.take(40)
            val subRef = db.collection("creatorSubscriptions").document(subId)
            val existingSub = subRef.get().await()
            val nowMs = System.currentTimeMillis()
            val baseExpiry = if (existingSub.exists()) {
                val prev = (existingSub.get("expiresAtMillis") as? Number)?.toLong() ?: 0L
                maxOf(prev, nowMs)
            } else {
                nowMs
            }
            val newExpiryMs = baseExpiry + 30L * 24L * 60L * 60L * 1000L

            val buyerRef = db.collection("users").document(uid)
            val buyerSnap = buyerRef.get().await()
            require(buyerSnap.getBoolean("adultVerified") == true) {
                "You must verify you are 18+ to subscribe and view locked photos."
            }
            val creatorRef = db.collection("users").document(creatorId)
            val txRef = db.collection("pointTransactions").document()

            val batch = db.batch()
            if (existingSub.exists()) {
                batch.update(
                    subRef,
                    mapOf(
                        "creatorName" to safeCreatorName,
                        "active" to true,
                        "expiresAtMillis" to newExpiryMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    subRef,
                    mapOf(
                        "subscriberId" to uid,
                        "creatorId" to creatorId,
                        "creatorName" to safeCreatorName,
                        "priceCents" to 1500L,
                        "creatorShareCents" to 750L,
                        "platformShareCents" to 750L,
                        "active" to true,
                        "expiresAtMillis" to newExpiryMs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                buyerRef,
                mapOf(
                    "totalSpentCents" to FieldValue.increment(1500L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                creatorRef,
                mapOf(
                    "creatorEarningsCents" to FieldValue.increment(750L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.SUBSCRIBE_CREATOR.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to creatorId,
                    "creatorName" to safeCreatorName,
                    "postId" to "",
                    "pointsDelta" to 0L,
                    "grossCents" to 1500L,
                    "creatorShareCents" to 750L,
                    "platformShareCents" to 750L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
