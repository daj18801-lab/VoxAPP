package com.example.data

import android.content.Context
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
        FirebaseFirestore.getInstance()
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

            // If not already in staffRoles and email is verified, check for a pending Admin or Support invite
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

            // Keep public user directory tier synced
            val dirRef = db.collection("userDirectory").document(targetUserId)
            if (dirRef.get().await().exists()) {
                dirRef.update(
                    mapOf(
                        "staffTier" to tier.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Sync live room badge/color if performed inside a room
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
            } else {
                resolvedName = snapshot.getString("displayName").orEmpty().ifBlank { displayName.trim().ifBlank { "Studio Host" } }.take(40)
                resolvedHandle = snapshot.getString("handle").orEmpty().ifBlank { "vox_user" }.take(30)
                resolvedBio = snapshot.getString("bio").orEmpty().take(200)
                resolvedColor = snapshot.getString("avatarColorHex").orEmpty().ifBlank { "#0088FF" }
                resolvedPhoto = snapshot.getString("photoBase64").orEmpty()
            }
            // Publish/refresh public userDirectory card so users can discover this profile
            syncUserDirectoryEntry(
                userId = uid,
                displayName = resolvedName,
                handle = resolvedHandle,
                bio = resolvedBio,
                avatarColorHex = resolvedColor,
                photoBase64 = resolvedPhoto,
                staffTier = staffTier
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
        staffTier: StaffTier
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
            ref.set(
                mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "bio" to safeBio,
                    "avatarColorHex" to safeColor,
                    "photoBase64" to safePhoto,
                    "staffTier" to staffTier.name,
                    "postCount" to existingPostCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
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
        currentPhotoBase64: String = ""
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
                staffTier = currentStaffTier
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
                staffTier = currentStaffTier
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

            // Sync renamed displayName, handle & bio to public userDirectory and staffRoles if present
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
                            "updatedAt" to FieldValue.
