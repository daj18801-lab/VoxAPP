package com.example.data

import android.content.Context
import com.example.R
import com.example.security.MessageSpamTracker
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
        const val DIRECT_TYPING_TTL_MS = 8_000L
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
                val safeName = UsernamePolicy.sanitizeForNewUser(displayName, fallback = "VoxUser")
                val rawHandle = emailPrefix.lowercase()
                    .replace(Regex("[^a-z0-9_\\-]"), "")
                    .ifBlank { "vox_${uid.take(6).lowercase()}" }
                    .take(24)
                val safeHandle = if (rawHandle.length < 2) "vox_user" else rawHandle
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "bio" to "Live on VOX",
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
                    "sex" to UserSex.UNSPECIFIED.name,
                    "age" to 0L,
                    "totalTimeSpentSec" to 0L,
                    "appearanceStatus" to PresenceStatus.ONLINE.name,
                    "blockedUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                resolvedName = safeName
                resolvedHandle = safeHandle
                resolvedBio = "Live on VOX"
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
            val resolvedSex = if (snapshot.exists()) {
                UserSex.fromRaw(snapshot.getString("sex"))
            } else {
                UserSex.UNSPECIFIED
            }
            val resolvedAge = if (snapshot.exists()) {
                val explicitAge = (snapshot.get("age") as? Number)?.toLong() ?: 0L
                if (explicitAge in 13L..120L) {
                    explicitAge
                } else {
                    val bYear = (snapshot.get("birthYear") as? Number)?.toLong() ?: 0L
                    if (bYear >= 1900L) {
                        val currYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        (currYear - bYear).takeIf { it in 13L..120L } ?: 0L
                    } else 0L
                }
            } else 0L
            val resolvedAppearance = if (snapshot.exists()) {
                PresenceStatus.fromRaw(snapshot.getString("appearanceStatus"))
            } else {
                PresenceStatus.ONLINE
            }
            val resolvedTimeSpent = if (snapshot.exists()) {
                (snapshot.get("totalTimeSpentSec") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            } else 0L
            val resolvedProLifetime = if (snapshot.exists()) {
                snapshot.getBoolean("proLifetime") == true
            } else false
            val resolvedPriorityChirpOptIn = if (snapshot.exists()) {
                snapshot.getBoolean("priorityChirpOptIn") == true
            } else false
            syncUserDirectoryEntry(
                userId = uid,
                displayName = resolvedName,
                handle = resolvedHandle,
                bio = resolvedBio,
                avatarColorHex = resolvedColor,
                photoBase64 = resolvedPhoto,
                staffTier = staffTier,
                dmPolicy = resolvedDmPolicy,
                sex = resolvedSex,
                age = resolvedAge,
                appearanceStatus = resolvedAppearance,
                totalTimeSpentSec = resolvedTimeSpent,
                proLifetime = resolvedProLifetime,
                priorityChirpOptIn = resolvedPriorityChirpOptIn
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
        dmPolicy: DmPolicy? = null,
        sex: UserSex? = null,
        age: Long? = null,
        appearanceStatus: PresenceStatus? = null,
        totalTimeSpentSec: Long? = null,
        proMember: Boolean? = null,
        proExpiresAtMillis: Long? = null,
        proLifetime: Boolean? = null,
        proTier: ProTier? = null,
        priorityChirpOptIn: Boolean? = null,
        profileBoostedUntilMillis: Long? = null,
        spotlightFrameUntilMillis: Long? = null,
        spotlightFrameColorHex: String? = null,
        referralCode: String? = null
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
            val effectiveSex = sex ?: UserSex.fromRaw(existing.getString("sex"))
            val rawExistingAge = (existing.get("age") as? Number)?.toLong() ?: 0L
            val candidateAge = age ?: rawExistingAge
            val effectiveAge = if (candidateAge in 13L..120L) candidateAge else 0L
            val effectiveAppearance = appearanceStatus ?: PresenceStatus.fromRaw(existing.getString("appearanceStatus"))
            val existingTime = (existing.get("totalTimeSpentSec") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val effectiveTimeSpent = maxOf(existingTime, (totalTimeSpentSec ?: existingTime).coerceAtLeast(0L))
            val existingProMember = existing.getBoolean("proMember") == true
            val existingProExpiry = (existing.get("proExpiresAtMillis") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val existingProLifetime = existing.getBoolean("proLifetime") == true
            val existingProTier = ProTier.fromRaw(existing.getString("proTier"))
            val existingPriorityChirpOptIn = existing.getBoolean("priorityChirpOptIn") == true
            val effectiveProLifetime = proLifetime ?: existingProLifetime
            val effectiveProMember = (proMember ?: existingProMember) || effectiveProLifetime
            val effectiveProExpiry = (proExpiresAtMillis ?: existingProExpiry).coerceAtLeast(0L)
            val nowMs = System.currentTimeMillis()
            val hasActivePro = effectiveProLifetime || (effectiveProMember && effectiveProExpiry > nowMs)
            val effectiveProTier = when {
                !hasActivePro -> ProTier.FREE
                proTier != null && proTier != ProTier.FREE -> proTier
                existingProTier != ProTier.FREE -> existingProTier
                else -> ProTier.PRO
            }
            val effectivePriorityChirpOptIn = priorityChirpOptIn ?: existingPriorityChirpOptIn
            val existingProfileBoost = (existing.get("profileBoostedUntilMillis") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val effectiveProfileBoost = (profileBoostedUntilMillis ?: existingProfileBoost).coerceAtLeast(0L)
            val existingSpotlightUntil = (existing.get("spotlightFrameUntilMillis") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val effectiveSpotlightUntil = (spotlightFrameUntilMillis ?: existingSpotlightUntil).coerceAtLeast(0L)
            val rawSpotlightHex = spotlightFrameColorHex ?: existing.getString("spotlightFrameColorHex") ?: SpotlightFrameColor.ELECTRIC_CYAN.primaryHex
            val effectiveSpotlightHex = if (Regex("^#[0-9A-Fa-f]{6}$").matches(rawSpotlightHex)) rawSpotlightHex else SpotlightFrameColor.ELECTRIC_CYAN.primaryHex
            val effectiveReferralCode = (referralCode ?: existing.getString("referralCode").orEmpty())
                .trim()
                .ifBlank { generateReferralCode(userId) }
            val existingFriends = (existing.get("acceptedFriendIds") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.take(200)
                .orEmpty()
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
                "proMember" to effectiveProMember,
                "proExpiresAtMillis" to effectiveProExpiry,
                "proLifetime" to effectiveProLifetime,
                "proTier" to effectiveProTier.name,
                "priorityChirpOptIn" to effectivePriorityChirpOptIn,
                "profileBoostedUntilMillis" to effectiveProfileBoost,
                "spotlightFrameUntilMillis" to effectiveSpotlightUntil,
                "spotlightFrameColorHex" to effectiveSpotlightHex,
                "referralCode" to effectiveReferralCode,
                "postCount" to existingPostCount,
                "dmPolicy" to effectivePolicy.name,
                "sex" to effectiveSex.name,
                "age" to effectiveAge,
                "online" to effectiveOnline,
                "appearanceStatus" to effectiveAppearance.name,
                "totalTimeSpentSec" to effectiveTimeSpent,
                "acceptedFriendIds" to existingFriends,
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

    suspend fun updateAppearanceStatus(status: PresenceStatus): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val batch = db.batch()
            val userRef = db.collection("users").document(uid)
            val dirRef = db.collection("userDirectory").document(uid)
            batch.update(
                userRef,
                mapOf(
                    "appearanceStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val dirSnap = dirRef.get().await()
            if (dirSnap.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "appearanceStatus" to status.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementTimeSpentOnApp(deltaSeconds: Long) {
        val uid = auth.currentUser?.uid ?: return
        val safeDelta = deltaSeconds.coerceIn(1L, 3600L)
        runCatching {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) return@runCatching
            val currentSec = (userSnap.get("totalTimeSpentSec") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val nextSec = (currentSec + safeDelta).coerceAtMost(100_000_000L)
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "totalTimeSpentSec" to nextSec,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val dirRef = db.collection("userDirectory").document(uid)
            val dirSnap = dirRef.get().await()
            if (dirSnap.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "totalTimeSpentSec" to nextSec,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
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

    suspend fun verifyUserAge13Plus(birthYear: Int): Result<Int> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            require(birthYear >= 1900 && birthYear <= currentYear) {
                "Please enter a valid birth year."
            }
            val derivedAge = currentYear - birthYear
            require(derivedAge >= 13) {
                "You must be 13 or older to use VOX or buy Pro membership and points."
            }
            val clampedAge = derivedAge.toLong().coerceIn(13L, 120L)
            val updates = mutableMapOf<String, Any>(
                "age13Verified" to true,
                "birthYear" to birthYear.toLong(),
                "age" to clampedAge,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (derivedAge >= 18) {
                updates["adultVerified"] = true
            }
            db.collection("users").document(uid).update(updates).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "age" to clampedAge,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(derivedAge)
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
            val derivedAge = (currentYear - birthYear).toLong().coerceIn(18L, 120L)
            db.collection("users").document(uid).update(
                mapOf(
                    "age13Verified" to true,
                    "adultVerified" to true,
                    "birthYear" to birthYear.toLong(),
                    "age" to derivedAge,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "age" to derivedAge,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private object RoomJoinFloodTracker {
        private const val WINDOW_MS = 60_000L
        private const val MAX_JOINS_PER_WINDOW = 3
        private val joinsByUserRoom = HashMap<String, MutableList<Long>>()

        @Synchronized
        fun recordAndCheckExceeded(userId: String, roomId: String, nowMs: Long): Boolean {
            val key = "${userId}_$roomId"
            val list = joinsByUserRoom.getOrPut(key) { mutableListOf() }
            list.removeAll { nowMs - it > WINDOW_MS || it > nowMs + 10_000L }
            list.add(nowMs)
            return list.size > MAX_JOINS_PER_WINDOW
        }

        @Synchronized
        fun clearForUserRoom(userId: String, roomId: String) {
            joinsByUserRoom.remove("${userId}_$roomId")
        }
    }

    private fun normalizeIdentityForMatch(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            val mapped = when (ch) {
                '0' -> 'o'
                '1', '!' -> 'i'
                '3' -> 'e'
                '4', '@' -> 'a'
                '5', '$' -> 's'
                '7' -> 't'
                else -> ch
            }
            if (mapped.isLetterOrDigit()) sb.append(mapped)
        }
        return sb.toString()
    }

    private fun extractNormalizedWords(raw: String): Set<String> {
        return raw.lowercase()
            .split(Regex("[^a-z0-9@$!]+"))
            .map { normalizeIdentityForMatch(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private suspend fun detectStaffOrAdminNameImpersonation(
        uid: String,
        candidateName: String,
        candidateHandle: String
    ): String? {
        val normName = normalizeIdentityForMatch(candidateName)
        val normHandle = normalizeIdentityForMatch(candidateHandle)
        val allWords = extractNormalizedWords(candidateName) + extractNormalizedWords(candidateHandle)

        val reservedSubstrings = listOf(
            "admin", "administrator", "superadmin", "sysadmin",
            "voxstaff", "voxsupport", "voxadmin", "voxowner", "voxmod", "voxofficial",
            "officialvox", "appadmin", "appowner", "supportteam", "supportstaff",
            "moderator", "coowner"
        )
        val reservedExactWords = setOf(
            "staff", "admin", "admins", "mod", "mods", "support",
            "owner", "administrator", "moderator", "official"
        )

        val matchedKeyword = reservedSubstrings.firstOrNull { kw ->
            normName.contains(kw) || normHandle.contains(kw)
        } ?: reservedExactWords.firstOrNull { w -> w in allWords }
        if (matchedKeyword != null) {
            return "reserved Staff/Admin term \"$matchedKeyword\""
        }

        val staffDocs = runCatching {
            db.collection("userDirectory")
                .whereIn("staffTier", listOf(StaffTier.ADMIN.name, StaffTier.SUPPORT.name))
                .get()
                .await()
                .documents
        }.getOrDefault(emptyList())

        for (doc in staffDocs) {
            val entry = doc.toObject(DirectoryEntry::class.java) ?: continue
            if (entry.userId == uid) continue
            val staffNames = listOf(entry.displayName, entry.customDisplayName, entry.handle)
                .map { normalizeIdentityForMatch(it) }
                .filter { it.length >= 2 }
                .toSet()
            if ((normName.length >= 2 && normName in staffNames) ||
                (normHandle.length >= 2 && normHandle in staffNames)
            ) {
                return "Staff/Admin member \"${entry.effectiveDisplayName()}\""
            }
        }
        return null
    }

    /**
     * Atomically writes a SYSTEM_AUTOMOD safety report, creates or increments userModerationFiles/{uid},
     * and appends a matching note to userModerationFiles/{uid}/notes/{reportId}.
     * Returns the created reportId.
     */
    private suspend fun recordAutoModReportAndUserFile(
        uid: String,
        targetName: String,
        roomId: String,
        roomTitle: String,
        category: ReportCategory,
        reportDetails: String,
        noteReason: String,
        suspendUntilMillis: Long = 0L,
        suspendReason: String = ""
    ): String {
        val safeTarget = targetName.trim().ifBlank { "Member" }.take(40)
        val reportRef = db.collection("safetyReports").document()
        val reportId = reportRef.id
        val modRef = db.collection("userModerationFiles").document(uid)
        val existingSnap = runCatching { modRef.get().await() }.getOrNull()
        val existingFile = existingSnap?.takeIf { it.exists() }?.toObject(UserModerationFile::class.java)
        val newReportCount = (existingFile?.reportCount ?: 0) + 1

        val batch = db.batch()
        batch.set(
            reportRef,
            mapOf(
                "reporterId" to uid,
                "reporterName" to "AutoMod",
                "roomId" to roomId.take(260),
                "roomTitle" to roomTitle.trim().take(60),
                "roomHostId" to "SYSTEM_AUTOMOD",
                "targetUserId" to uid,
                "targetName" to safeTarget,
                "category" to category.name,
                "details" to reportDetails.trim().take(400),
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (existingFile == null) {
            batch.set(
                modRef,
                mapOf(
                    "userId" to uid,
                    "targetName" to safeTarget,
                    "reportCount" to 1,
                    "warningCount" to 0,
                    "investigationActive" to true,
                    "investigationStatus" to InvestigationStatus.INVESTIGATING.name,
                    "suspendedUntilMillis" to 0L,
                    "suspendedUntilInvestigationResolved" to false,
                    "pendingAdminReview" to false,
                    "suspensionReason" to "",
                    "lastWarningMessage" to "",
                    "lastWarningAtMillis" to 0L,
                    "lastWarningAcknowledgedAtMillis" to 0L,
                    "lastReportId" to reportId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            val effectiveSuspendUntil = maxOf(existingFile.suspendedUntilMillis, suspendUntilMillis)
            val effectiveSuspendReason = suspendReason.trim().take(240)
                .ifBlank { existingFile.suspensionReason.take(240) }
            val nextStatus = if (suspendUntilMillis > System.currentTimeMillis()) {
                InvestigationStatus.SUSPENDED_PENDING_ADMIN_REVIEW.name
            } else {
                InvestigationStatus.INVESTIGATING.name
            }
            batch.update(
                modRef,
                mapOf(
                    "targetName" to safeTarget,
                    "reportCount" to newReportCount,
                    "investigationActive" to true,
                    "investigationStatus" to nextStatus,
                    "suspendedUntilMillis" to effectiveSuspendUntil,
                    "suspensionReason" to effectiveSuspendReason,
                    "lastReportId" to reportId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val noteRef = modRef.collection("notes").document(reportId)
        batch.set(
            noteRef,
            mapOf(
                "targetUserId" to uid,
                "reportId" to reportId,
                "strikeNumber" to newReportCount,
                "actionTaken" to FileNoteAction.INVESTIGATION_STARTED.name,
                "category" to category.name,
                "reason" to noteReason.trim().take(450),
                "reporterId" to uid,
                "reporterName" to "AutoMod",
                "actorRole" to "REPORTER",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        return reportId
    }

    suspend fun updateUserProfile(
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        nightModeEnabled: Boolean,
        invisibleModePreferred: Boolean? = null,
        sex: UserSex = UserSex.UNSPECIFIED,
        age: Long = 0L,
        currentStaffTier: StaffTier = StaffTier.USER,
        currentPhotoBase64: String = "",
        currentDmPolicy: DmPolicy = DmPolicy.OPEN
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val trimmedName = displayName.trim()
            val safeHandle = handle.trim().replace(" ", "_").take(30)
            val safeBio = bio.trim().take(200)
            require(trimmedName.isNotEmpty()) { "Display name cannot be empty." }
            require(safeHandle.length >= 2) { "Handle must be at least 2 characters." }
            require(Regex("^#[0-9A-Fa-f]{6}$").matches(avatarColorHex)) { "Invalid color hex." }
            require(age == 0L || age in 13L..120L) { "Age must be between 13 and 120 (or left blank)." }

            val roleTierFromServer = runCatching {
                val roleDoc = db.collection("staffRoles").document(uid).get().await()
                if (roleDoc.exists()) StaffTier.fromRaw(roleDoc.getString("tier")) else StaffTier.USER
            }.getOrDefault(StaffTier.USER)
            val isRealAdmin = currentStaffTier == StaffTier.ADMIN || roleTierFromServer == StaffTier.ADMIN
            val isRealStaff = currentStaffTier.isStaff() || roleTierFromServer.isStaff()

            val currentUserDoc = runCatching {
                db.collection("users").document(uid).get().await()
            }.getOrNull()
            val existingName = currentUserDoc?.getString("displayName")
                ?.trim().orEmpty()

            // Enforce 10-character max and no symbols for non-admins whenever the name is changed.
            // If an Admin previously added symbols to this user's name and the user is only saving
            // other profile settings without editing their name, preserve their existing name.
            val nameChanged = trimmedName != existingName
            if (isRealAdmin) {
                UsernamePolicy.validate(trimmedName, allowSymbols = true)?.let { err ->
                    return Result.failure(IllegalArgumentException(err))
                }
            } else if (nameChanged || existingName.isBlank()) {
                UsernamePolicy.validate(trimmedName, allowSymbols = false)?.let { err ->
                    return Result.failure(IllegalArgumentException(err))
                }
            }
            val safeName = if (!isRealAdmin && !nameChanged && existingName.isNotBlank()) {
                existingName.take(40)
            } else {
                trimmedName.take(UsernamePolicy.MAX_LENGTH)
            }

            // Block non-staff users from changing their username or handle to a Staff/Admin name
            // and automatically file an impersonation report to Staff + log in their moderation file.
            if (!isRealStaff) {
                val impersonationMatch = detectStaffOrAdminNameImpersonation(
                    uid = uid,
                    candidateName = safeName,
                    candidateHandle = safeHandle
                )
                if (impersonationMatch != null) {
                    val existingNameForReport = existingName.ifBlank { "Member" }
                    runCatching {
                        recordAutoModReportAndUserFile(
                            uid = uid,
                            targetName = existingNameForReport,
                            roomId = "AUTOMOD_NAME_IMPERSONATION",
                            roomTitle = "Staff/Admin Name Impersonation",
                            category = ReportCategory.IMPERSONATION,
                            reportDetails = "Blocked attempt by $existingNameForReport to change username to \"$safeName\" (@$safeHandle), matching $impersonationMatch.",
                            noteReason = "Blocked Staff/Admin username impersonation attempt: tried to set name \"$safeName\" (@$safeHandle), matching $impersonationMatch."
                        )
                    }
                    return Result.failure(
                        IllegalStateException(
                            "You cannot change your username or handle to a Staff or Admin name. This attempt has been reported to VOX Staff."
                        )
                    )
                }
            }

            val updates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "avatarColorHex" to avatarColorHex,
                "pushToTalkPreferred" to pushToTalkPreferred,
                "darkThemePreferred" to nightModeEnabled,
                "nightModeEnabled" to nightModeEnabled,
                "sex" to sex.name,
                "age" to age,
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
                dmPolicy = currentDmPolicy,
                sex = sex,
                age = age
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Enforces the global picture upload limit: maximum 10 pictures per 10-minute sliding window.
     * Returns the updated sorted timestamp list to persist onto users/{uid}.pictureUploadTimestamps.
     */
    private suspend fun reservePictureUploadSlot(uid: String): List<Long> {
        val userSnap = db.collection("users").document(uid).get().await()
        val rawList = (userSnap.get("pictureUploadTimestamps") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            .orEmpty()
        val nowMs = System.currentTimeMillis()
        val windowMs = UserProfile.PICTURE_UPLOAD_WINDOW_MS
        val activeTimestamps = rawList
            .filter { nowMs - it < windowMs && it <= nowMs + 60_000L }
            .sorted()
        if (activeTimestamps.size >= UserProfile.MAX_PICTURES_PER_WINDOW) {
            val oldestMs = activeTimestamps.first()
            val remainingMs = (windowMs - (nowMs - oldestMs)).coerceAtLeast(1_000L)
            val mins = remainingMs / 60_000L
            val secs = (remainingMs % 60_000L) / 1_000L
            val waitLabel = if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
            throw IllegalStateException(
                "Upload limit reached: Max 10 pictures in 10 minutes. Try again in $waitLabel."
            )
        }
        return (activeTimestamps + nowMs).takeLast(UserProfile.MAX_PICTURES_PER_WINDOW)
    }

    /**
     * Enforces the Discovery weekly photo limit for free members: max 2 pictures per 7-day rolling window.
     * Paid members (who have purchased Points / subscriptions) and Staff have unlimited Discovery photos.
     */
    private suspend fun reserveWeeklyDiscoveryPhotoSlot(uid: String, myTier: StaffTier): List<Long>? {
        if (myTier.isStaff()) return null
        val userSnap = db.collection("users").document(uid).get().await()
        val pointsBalance = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
        val totalSpentCents = (userSnap.get("totalSpentCents") as? Number)?.toLong() ?: 0L
        if (pointsBalance > 0L || totalSpentCents > 0L) return null

        val rawList = (userSnap.get("discoveryPhotoTimestamps") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            .orEmpty()
        val nowMs = System.currentTimeMillis()
        val weekMs = UserProfile.DISCOVERY_PHOTO_WEEK_MS
        val activeTimestamps = rawList
            .filter { nowMs - it < weekMs && it <= nowMs + 60_000L }
            .sorted()
        if (activeTimestamps.size >= UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK) {
            val oldestMs = activeTimestamps.first()
            val remainingMs = (weekMs - (nowMs - oldestMs)).coerceAtLeast(60_000L)
            val days = remainingMs / 86_400_000L
            val hours = (remainingMs % 86_400_000L) / 3_600_000L
            val waitLabel = when {
                days > 0L && hours > 0L -> "${days}d ${hours}h"
                days > 0L -> "${days}d"
                hours > 0L -> "${hours}h"
                else -> "under 1 hour"
            }
            throw IllegalStateException(
                "Weekly Discovery limit reached: Free members can post 2 pictures per week. Try again in $waitLabel or buy Points for unlimited Discovery photos."
            )
        }
        return (activeTimestamps + nowMs).takeLast(UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK)
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
            val updateMap = mutableMapOf<String, Any>(
                "photoBase64" to photoBase64,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (photoBase64.isNotBlank()) {
                updateMap["pictureUploadTimestamps"] = reservePictureUploadSlot(uid)
            }
            db.collection("users").document(uid).update(updateMap).await()
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
        roomId: String? = null,
        pointsBalance: Long? = null,
        proLifetime: Boolean? = null,
        disableMessaging: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$targetUserId"
        return try {
            val trimmedName = displayName.trim()
            UsernamePolicy.validate(trimmedName, allowSymbols = true)?.let { err ->
                return Result.failure(IllegalArgumentException(err))
            }
            val safeName = trimmedName.take(UsernamePolicy.MAX_LENGTH)
            val safeHandle = handle.trim().replace(" ", "_").take(30)
            val safeBio = bio.trim().take(200)
            val safeReason = suspensionReason.trim().take(200)
            require(safeHandle.length >= 2) { "Handle must be at least 2 characters." }

            val userUpdates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "globallyBanned" to globallyBanned,
                "disableCreateRoom" to disableCreateRoom,
                "disableEnterRoom" to disableEnterRoom,
                "disableMic" to disableMic,
                "disableVideo" to disableVideo,
                "disableMessaging" to disableMessaging,
                "suspendedUntilMillis" to suspendedUntilMillis.coerceAtLeast(0L),
                "suspensionReason" to safeReason,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (pointsBalance != null) {
                userUpdates["pointsBalance"] = pointsBalance.coerceIn(0L, 1_000_000L)
            }
            if (proLifetime != null) {
                userUpdates["proLifetime"] = proLifetime
                if (proLifetime) {
                    userUpdates["proMember"] = true
                }
            }
            db.collection("users").document(targetUserId).update(userUpdates).await()

            runCatching {
                syncDeviceBanForTargetUser(
                    targetUserId = targetUserId,
                    targetName = safeName,
                    active = globallyBanned,
                    reason = safeReason.ifBlank {
                        if (globallyBanned) "Globally banned by Administrator" else "Ban lifted by Administrator"
                    },
                    actorUid = uid,
                    actorName = actorName
                )
            }

            runCatching {
                val dirRef = db.collection("userDirectory").document(targetUserId)
                if (dirRef.get().await().exists()) {
                    val dirUpdates = mutableMapOf<String, Any>(
                        "displayName" to safeName,
                        "handle" to safeHandle,
                        "bio" to safeBio,
                        "disableMessaging" to disableMessaging,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (proLifetime != null) {
                        dirUpdates["proLifetime"] = proLifetime
                        if (proLifetime) {
                            dirUpdates["proMember"] = true
                        }
                    }
                    dirRef.update(dirUpdates).await()
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
            if (suspendedUntilMillis <= 0L) {
                runCatching {
                    val modRef = db.collection("userModerationFiles").document(targetUserId)
                    val modSnap = modRef.get().await()
                    if (modSnap.exists()) {
                        modRef.update(
                            mapOf(
                                "suspendedUntilMillis" to 0L,
                                "suspendedUntilInvestigationResolved" to false,
                                "pendingAdminReview" to false,
                                "pendingSuspensionRequestId" to "",
                                "investigationActive" to false,
                                "investigationStatus" to InvestigationStatus.RESOLVED.name,
                                "suspensionReason" to "",
                                "lastResolvedById" to uid,
                                "lastResolvedByName" to actorName.trim().ifBlank { "Administrator" }.take(40),
                                "lastResolutionNote" to "Suspension cleared by Administrator",
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
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Admin-only one-tap removal of any active account suspension across both users/{uid}
     * and userModerationFiles/{uid} (plus LOGIN feature suspension & device ban if present).
     */
    suspend fun adminRemoveUserSuspension(
        targetUserId: String,
        targetName: String,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeActor = actorName.trim().ifBlank { "Administrator" }.take(40)
        val safeTarget = targetName.trim().ifBlank { "Member" }.take(40)
        val path = "users/$targetUserId"
        return try {
            val userRef = db.collection("users").document(targetUserId)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val existingFeatureSuspensions = (userSnap.get("featureSuspensions") as? Map<*, *>)
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val num = (v as? Number)?.toLong() ?: return@mapNotNull null
                        if (key == RestrictableFeature.LOGIN.name) null else key to num
                    }
                    ?.toMap()
                    .orEmpty()
                val updates = mutableMapOf<String, Any>(
                    "globallyBanned" to false,
                    "suspendedUntilMillis" to 0L,
                    "suspensionReason" to "",
                    "featureSuspensions" to existingFeatureSuspensions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.update(updates).await()
            }

            runCatching {
                syncDeviceBanForTargetUser(
                    targetUserId = targetUserId,
                    targetName = safeTarget,
                    active = false,
                    reason = "Suspension removed by Administrator",
                    actorUid = uid,
                    actorName = safeActor
                )
            }

            runCatching {
                val modRef = db.collection("userModerationFiles").document(targetUserId)
                val modSnap = modRef.get().await()
                if (modSnap.exists()) {
                    val existingCount = (modSnap.getLong("reportCount") ?: 0L).toInt()
                    val noteRef = modRef.collection("notes").document()
                    val batch = db.batch()
                    batch.update(
                        modRef,
                        mapOf(
                            "investigationActive" to false,
                            "investigationStatus" to InvestigationStatus.RESOLVED.name,
                            "suspendedUntilMillis" to 0L,
                            "suspendedUntilInvestigationResolved" to false,
                            "pendingAdminReview" to false,
                            "pendingSuspensionRequestId" to "",
                            "suspensionReason" to "",
                            "lastResolvedById" to uid,
                            "lastResolvedByName" to safeActor,
                            "lastResolutionNote" to "Suspension removed by Administrator",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        noteRef,
                        mapOf(
                            "targetUserId" to targetUserId,
                            "reportId" to "",
                            "strikeNumber" to existingCount,
                            "actionTaken" to FileNoteAction.ADMIN_REVIEW_COMPLETED.name,
                            "category" to "ADMIN_REVIEW",
                            "reason" to "Administrator $safeActor removed active suspension.",
                            "reporterId" to uid,
                            "reporterName" to safeActor,
                            "actorRole" to "ADMIN",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- DEVICE BAN, DISABLE MESSAGING, ADD-PEOPLE RATE LIMIT & SPAM ESCALATION ---

    suspend fun recordUserDeviceId(deviceHash: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 8) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (snap.exists() && snap.getString("lastDeviceId") != safeHash) {
                userRef.update(
                    mapOf(
                        "lastDeviceId" to safeHash,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeBannedDevice(deviceHash: String): Flow<BannedDevice?> {
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 8) return flowOf(null)
        return db.collection("bannedDevices").document(safeHash)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(BannedDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { emit(null) }
    }

    suspend fun registerSelfDeviceBanIfGloballyBanned(
        deviceHash: String,
        displayName: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 8) return Result.success(Unit)
        val path = "bannedDevices/$safeHash"
        return try {
            val ref = db.collection("bannedDevices").document(safeHash)
            val existing = runCatching { ref.get().await() }.getOrNull()
            if (existing?.exists() == true && existing.getBoolean("active") == true) {
                return Result.success(Unit)
            }
            val safeName = displayName.trim().ifBlank { "Banned User" }.take(40)
            val safeReason = reason.trim().ifBlank { "Account globally banned — device blocked from alternate logins." }.take(240)
            ref.set(
                mapOf(
                    "deviceHash" to safeHash,
                    "bannedUserId" to uid,
                    "bannedUserName" to safeName,
                    "reason" to safeReason,
                    "bannedByUid" to uid,
                    "bannedByName" to "System Device Ban",
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun syncDeviceBanForTargetUser(
        targetUserId: String,
        targetName: String,
        active: Boolean,
        reason: String,
        actorUid: String,
        actorName: String
    ) {
        val userSnap = runCatching {
            db.collection("users").document(targetUserId).get().await()
        }.getOrNull() ?: return
        val deviceHash = userSnap.getString("lastDeviceId")?.trim().orEmpty()
        if (deviceHash.length < 8) return
        val banRef = db.collection("bannedDevices").document(deviceHash)
        val existingBan = runCatching { banRef.get().await() }.getOrNull()
        val safeName = targetName.trim().ifBlank { "Member" }.take(40)
        val safeActor = actorName.trim().ifBlank { "Admin" }.take(40)
        val safeReason = reason.trim().take(240)

        if (active) {
            if (existingBan?.exists() == true) {
                banRef.update(
                    mapOf(
                        "bannedUserId" to targetUserId,
                        "bannedUserName" to safeName,
                        "reason" to safeReason.ifBlank { "Device banned with user account" },
                        "bannedByUid" to actorUid,
                        "bannedByName" to safeActor,
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                banRef.set(
                    mapOf(
                        "deviceHash" to deviceHash,
                        "bannedUserId" to targetUserId,
                        "bannedUserName" to safeName,
                        "reason" to safeReason.ifBlank { "Device banned with user account" },
                        "bannedByUid" to actorUid,
                        "bannedByName" to safeActor,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } else if (existingBan?.exists() == true && existingBan.getBoolean("active") == true) {
            banRef.update(
                mapOf(
                    "active" to false,
                    "reason" to safeReason.ifBlank { "Device ban lifted by Admin" },
                    "bannedByUid" to actorUid,
                    "bannedByName" to safeActor,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeAllDeviceRecords(): Flow<List<BannedDevice>> {
        if (auth.currentUser?.uid.isNullOrBlank()) return flowOf(emptyList())
        return db.collection("bannedDevices")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(BannedDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }
            .catch { emit(emptyList()) }
    }

    suspend fun syncReportedDeviceFlagForCurrentUser(
        deviceHash: String,
        displayName: String,
        reportCount: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 16 || reportCount <= 0) return Result.success(Unit)
        val path = "bannedDevices/$safeHash"
        return try {
            val ref = db.collection("bannedDevices").document(safeHash)
            val existing = runCatching { ref.get().await() }.getOrNull()
            val safeName = displayName.trim().ifBlank { "Member" }.take(40)
            val safeReason = "Device linked to account ($safeName) with $reportCount safety report(s)."
            if (existing == null || !existing.exists()) {
                ref.set(
                    mapOf(
                        "deviceHash" to safeHash,
                        "bannedUserId" to uid,
                        "bannedUserName" to safeName,
                        "reason" to safeReason.take(240),
                        "bannedByUid" to uid,
                        "bannedByName" to "Safety Report Tracker",
                        "active" to false,
                        "reportedDeviceFlag" to true,
                        "deletedAccountFlagged" to false,
                        "priorReportCount" to reportCount,
                        "returningFlagActive" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingUid = existing.getString("bannedUserId").orEmpty()
                val alreadyFlagged = existing.getBoolean("reportedDeviceFlag") == true
                val existingCount = (existing.get("priorReportCount") as? Number)?.toInt() ?: 0
                if (existingUid == uid && (!alreadyFlagged || reportCount > existingCount)) {
                    ref.update(
                        mapOf(
                            "bannedUserName" to safeName,
                            "reason" to safeReason.take(240),
                            "reportedDeviceFlag" to true,
                            "priorReportCount" to maxOf(reportCount, existingCount),
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

    /**
     * Permanently deletes the current user's account and stops all active memberships.
     * If the user has any safety reports on file, preserves their moderation file and safety reports
     * under Deleted Accounts/Reports along with their username and device ID, flags their device,
     * and notifies Admins for Keep/Delete file resolution.
     */
    suspend fun deleteOwnAccount(
        deviceHash: String,
        displayName: String,
        handle: String
    ): Result<Boolean> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val safeHash = deviceHash.trim().take(128)
        val safeName = displayName.trim().ifBlank { "Former Member" }.take(40)
        val safeHandle = handle.trim().removePrefix("@").take(40)
        return try {
            val modRef = db.collection("userModerationFiles").document(uid)
            val modSnap = runCatching { modRef.get().await() }.getOrNull()
            val reportCount = (modSnap?.get("reportCount") as? Number)?.toInt() ?: 0
            val hasReports = modSnap != null && modSnap.exists() && reportCount > 0

            if (hasReports) {
                val nowMs = System.currentTimeMillis()
                val retainedHash = if (safeHash.length >= 16) safeHash else "dev_${uid.take(24)}".padEnd(16, '0')
                modRef.update(
                    mapOf(
                        "targetName" to safeName,
                        "deletedAccount" to true,
                        "deletedAtMillis" to nowMs,
                        "deletedDeviceHash" to retainedHash,
                        "deletedHandle" to safeHandle,
                        "deletedFileDecision" to "PENDING",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val devRef = db.collection("bannedDevices").document(retainedHash)
                val existingDev = runCatching { devRef.get().await() }.getOrNull()
                val existingActive = existingDev?.getBoolean("active") == true
                val flagReason = "Deleted account ($safeName) had $reportCount safety report(s) on file."
                if (existingDev != null && existingDev.exists()) {
                    devRef.update(
                        mapOf(
                            "bannedUserId" to uid,
                            "bannedUserName" to safeName,
                            "reason" to flagReason.take(240),
                            "active" to existingActive,
                            "reportedDeviceFlag" to true,
                            "deletedAccountFlagged" to true,
                            "priorReportCount" to reportCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    devRef.set(
                        mapOf(
                            "deviceHash" to retainedHash,
                            "bannedUserId" to uid,
                            "bannedUserName" to safeName,
                            "reason" to flagReason.take(240),
                            "bannedByUid" to uid,
                            "bannedByName" to "Account Deletion Audit",
                            "active" to false,
                            "reportedDeviceFlag" to true,
                            "deletedAccountFlagged" to true,
                            "priorReportCount" to reportCount,
                            "returningFlagActive" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                runCatching {
                    val notifId = "del_rep_${uid.take(24)}_${nowMs}"
                    db.collection("adminNotifications").document(notifId).set(
                        mapOf(
                            "type" to AdminNotificationType.DELETED_ACCOUNT_WITH_REPORTS.name,
                            "title" to "Deleted Account Retained ($reportCount Report${if (reportCount == 1) "" else "s"})".take(120),
                            "message" to "$safeName (@${safeHandle.ifBlank { "deleted" }}) deleted their account while having $reportCount report(s). Saved under Deleted Accounts/Reports with Device ID ${retainedHash.take(12)}… — Admin can Keep or Delete the file.".take(500),
                            "createdById" to uid,
                            "createdByName" to safeName,
                            "targetUserId" to uid,
                            "targetName" to safeName,
                            "referenceId" to retainedHash,
                            "countValue" to reportCount,
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Stop all active creator subscriptions owned by this user
            runCatching {
                val subsSnap = db.collection("creatorSubscriptions")
                    .whereEqualTo("subscriberId", uid)
                    .get()
                    .await()
                for (doc in subsSnap.documents) {
                    runCatching { doc.reference.delete().await() }
                }
            }

            // Delete public directory entry and private user profile (also terminates VOX Pro/VIP/Business membership)
            runCatching { db.collection("userDirectory").document(uid).delete().await() }
            db.collection("users").document(uid).delete().await()

            // Delete Firebase Auth user (fallback to signOut if recent re-login is required)
            val deletedAuth = runCatching { currentUser.delete().await() }.isSuccess
            if (!deletedAuth) {
                runCatching { auth.signOut() }
            }
            Result.success(hasReports)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    /**
     * Flags a returning user who signed in under a different account/name on a device
     * that has safety reports on record, locking their account and notifying Staff/Admin.
     */
    suspend fun flagReturningUserOnReportedDevice(
        deviceHash: String,
        returningUserName: String,
        existingRecord: BannedDevice
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 16) return Result.success(Unit)
        val path = "bannedDevices/$safeHash"
        return try {
            val safeName = returningUserName.trim().ifBlank { "Returning User" }.take(40)
            val devRef = db.collection("bannedDevices").document(safeHash)
            devRef.update(
                mapOf(
                    "returningUserId" to uid,
                    "returningUserName" to safeName,
                    "returningFlagActive" to true,
                    "staffReviewStatus" to "PENDING_STAFF_REVIEW",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            runCatching {
                val notifId = "ret_dev_${uid.take(20)}_${safeHash.take(16)}"
                val priorName = existingRecord.bannedUserName.ifBlank { "prior account" }
                val reports = existingRecord.priorReportCount.coerceAtLeast(1)
                db.collection("adminNotifications").document(notifId).set(
                    mapOf(
                        "type" to AdminNotificationType.RETURNING_REPORTED_DEVICE.name,
                        "title" to "Flagged Returning Device: $safeName".take(120),
                        "message" to "$safeName signed in on device ${safeHash.take(12)}… previously tied to $priorName ($reports report${if (reports == 1) "" else "s"}). Account is locked pending Staff/Admin action (Grant Access, Disable Features, or Ban).".take(500),
                        "createdById" to uid,
                        "createdByName" to safeName,
                        "targetUserId" to uid,
                        "targetName" to safeName,
                        "referenceId" to safeHash,
                        "countValue" to reports,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
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

    /**
     * Admin resolves a Deleted Account / Reports moderation file by either Keeping it on record
     * or Permanently Deleting the file, its notes, associated reports, and non-banned device flag.
     */
    suspend fun adminResolveDeletedAccountFile(
        targetUserId: String,
        deviceHash: String,
        keepFile: Boolean,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank()) return Result.failure(IllegalArgumentException("Missing user ID"))
        val safeActor = actorName.trim().ifBlank { "Admin" }.take(40)
        val path = "userModerationFiles/$targetUserId"
        return try {
            val modRef = db.collection("userModerationFiles").document(targetUserId)
            val reportsSnap = runCatching {
                db.collection("safetyReports")
                    .whereEqualTo("targetUserId", targetUserId)
                    .get()
                    .await()
            }.getOrNull()

            if (keepFile) {
                modRef.update(
                    mapOf(
                        "deletedFileDecision" to "KEPT",
                        "investigationActive" to false,
                        "investigationStatus" to InvestigationStatus.RESOLVED.name,
                        "suspendedUntilMillis" to 0L,
                        "suspendedUntilInvestigationResolved" to false,
                        "lastResolvedById" to uid,
                        "lastResolvedByName" to safeActor,
                        "lastResolutionNote" to "Admin kept deleted account & device report file on record.",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                reportsSnap?.documents?.forEach { doc ->
                    if (doc.getString("status") == ReportStatus.OPEN.name) {
                        runCatching {
                            doc.reference.update(
                                mapOf(
                                    "status" to ReportStatus.RESOLVED.name,
                                    "resolutionNote" to "Deleted account — report file retained by $safeActor",
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            } else {
                // Delete subcollection notes first
                runCatching {
                    val notesSnap = modRef.collection("notes").get().await()
                    notesSnap.documents.forEach { n -> runCatching { n.reference.delete().await() } }
                }
                // Delete associated safety reports
                reportsSnap?.documents?.forEach { doc ->
                    runCatching { doc.reference.delete().await() }
                }
                // Delete moderation file
                modRef.delete().await()
                // Clear device flag if device isn't under an active hard ban
                val safeHash = deviceHash.trim().take(128)
                if (safeHash.length >= 16) {
                    val devRef = db.collection("bannedDevices").document(safeHash)
                    val devSnap = runCatching { devRef.get().await() }.getOrNull()
                    if (devSnap != null && devSnap.exists() && devSnap.getBoolean("active") != true) {
                        runCatching { devRef.delete().await() }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (keepFile) OperationType.UPDATE else OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Staff/Admin grants access to a returning user who was flagged on a reported device.
     */
    suspend fun staffGrantFlaggedDeviceAccess(
        deviceHash: String,
        actorName: String,
        note: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 16) return Result.failure(IllegalArgumentException("Invalid device ID"))
        val safeActor = actorName.trim().ifBlank { "Staff" }.take(40)
        val safeNote = note.trim().ifBlank { "Access granted after staff review." }.take(240)
        val path = "bannedDevices/$safeHash"
        return try {
            db.collection("bannedDevices").document(safeHash).update(
                mapOf(
                    "active" to false,
                    "reportedDeviceFlag" to false,
                    "returningFlagActive" to false,
                    "staffReviewStatus" to "ACCESS_GRANTED",
                    "reviewedByUid" to uid,
                    "reviewedByName" to safeActor,
                    "reviewNote" to safeNote,
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

    /**
     * Staff/Admin clears the full returning-device lockout after applying feature restrictions
     * so the user can enter with only their allowed app features enabled.
     */
    suspend fun staffMarkFlaggedDeviceRestricted(
        deviceHash: String,
        actorName: String,
        note: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 16) return Result.failure(IllegalArgumentException("Invalid device ID"))
        val safeActor = actorName.trim().ifBlank { "Staff" }.take(40)
        val safeNote = note.trim().ifBlank { "Granted restricted access with app features disabled." }.take(240)
        val path = "bannedDevices/$safeHash"
        return try {
            db.collection("bannedDevices").document(safeHash).update(
                mapOf(
                    "returningFlagActive" to false,
                    "staffReviewStatus" to "RESTRICTED",
                    "reviewedByUid" to uid,
                    "reviewedByName" to safeActor,
                    "reviewNote" to safeNote,
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

    /**
     * Staff/Admin bans a returning user and permanently bans their device.
     */
    suspend fun staffBanFlaggedReturningUser(
        deviceHash: String,
        targetUserId: String,
        targetName: String,
        actorTier: StaffTier,
        actorName: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val safeHash = deviceHash.trim().take(128)
        if (safeHash.length < 16) return Result.failure(IllegalArgumentException("Invalid device ID"))
        val safeActor = actorName.trim().ifBlank { "Staff" }.take(40)
        val safeTarget = targetName.trim().ifBlank { "Returning User" }.take(40)
        val safeReason = reason.trim().ifBlank { "Banned by $safeActor after returning on a reported device." }.take(240)
        val path = "bannedDevices/$safeHash"
        return try {
            db.collection("bannedDevices").document(safeHash).update(
                mapOf(
                    "active" to true,
                    "reportedDeviceFlag" to true,
                    "returningFlagActive" to false,
                    "staffReviewStatus" to "BANNED",
                    "bannedUserId" to targetUserId.ifBlank { uid },
                    "bannedUserName" to safeTarget,
                    "reason" to safeReason,
                    "bannedByUid" to uid,
                    "bannedByName" to safeActor,
                    "reviewedByUid" to uid,
                    "reviewedByName" to safeActor,
                    "reviewNote" to safeReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (targetUserId.isNotBlank() && actorTier == StaffTier.ADMIN) {
                runCatching {
                    db.collection("users").document(targetUserId).update(
                        mapOf(
                            "globallyBanned" to true,
                            "suspensionReason" to safeReason,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfileOnce(targetUserId: String): Result<UserProfile?> {
        if (targetUserId.isBlank()) return Result.success(null)
        val path = "users/$targetUserId"
        return try {
            val snap = db.collection("users").document(targetUserId).get().await()
            val prof = if (snap.exists()) snap.toObject(UserProfile::class.java) else null
            Result.success(prof)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserFeatureRestrictions(
        targetUserId: String,
        actorTier: StaffTier,
        disabledFeatures: List<String>?,
        featureSuspensions: Map<String, Long>,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank() || targetUserId == uid) {
            return Result.failure(IllegalArgumentException("Select another user."))
        }
        val path = "users/$targetUserId"
        return try {
            if (actorTier != StaffTier.ADMIN && isTargetUserBusinessAccount(targetUserId)) {
                return Result.failure(
                    IllegalStateException(
                        "Support Staff cannot edit or restrict a Business Account. Submit an App Admin approval request from the Business tab."
                    )
                )
            }
            val nowMs = System.currentTimeMillis()
            val validKeys = RestrictableFeature.entries.map { it.key }.toSet()
            val cleanSuspensions = featureSuspensions
                .filterKeys { it in validKeys }
                .filterValues { it > nowMs }
            val cleanReason = reason.trim().take(240)
            val updates = mutableMapOf<String, Any>(
                "featureSuspensions" to cleanSuspensions,
                "suspensionReason" to cleanReason,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (actorTier == StaffTier.ADMIN && disabledFeatures != null) {
                val cleanDisabled = disabledFeatures.filter { it in validKeys }.distinct()
                val dmPermDisabled = RestrictableFeature.DM.key in cleanDisabled
                val roomPermDisabled = RestrictableFeature.ENTER_ROOM.key in cleanDisabled
                val loginPermDisabled = RestrictableFeature.LOGIN.key in cleanDisabled
                updates["disabledFeatures"] = cleanDisabled
                updates["disableMessaging"] = dmPermDisabled
                updates["disableEnterRoom"] = roomPermDisabled
                updates["globallyBanned"] = loginPermDisabled
                db.collection("users").document(targetUserId).update(updates).await()
                runCatching {
                    val dirRef = db.collection("userDirectory").document(targetUserId)
                    if (dirRef.get().await().exists()) {
                        dirRef.update(
                            mapOf(
                                "disableMessaging" to dmPermDisabled,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            } else {
                // Support Staff: strictly timed feature suspensions only — never permanent disables
                db.collection("users").document(targetUserId).update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserMessagingDisabled(
        targetUserId: String,
        disabled: Boolean,
        actorTier: StaffTier = StaffTier.SUPPORT
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank() || targetUserId == uid) {
            return Result.failure(IllegalArgumentException("Select another user."))
        }
        if (actorTier != StaffTier.ADMIN) {
            return Result.failure(
                IllegalStateException(
                    "Support Staff can only apply timed feature suspensions. Permanent disables require an App Admin."
                )
            )
        }
        val path = "users/$targetUserId"
        return try {
            val snap = db.collection("users").document(targetUserId).get().await()
            val existing = snap.toObject(UserProfile::class.java)?.disabledFeatures.orEmpty()
            val nextDisabled = if (disabled) {
                (existing + RestrictableFeature.DM.key).distinct()
            } else {
                existing.filter { it != RestrictableFeature.DM.key }
            }
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "disableMessaging" to disabled,
                    "disabledFeatures" to nextDisabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(targetUserId)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "disableMessaging" to disabled,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun isTargetUserBusinessAccount(targetUserId: String): Boolean {
        val userSnap = runCatching { db.collection("users").document(targetUserId).get().await() }.getOrNull()
        if (userSnap != null && userSnap.exists()) {
            if (userSnap.getString("proTier").equals(ProTier.BUSINESS.name, ignoreCase = true) &&
                userSnap.getBoolean("companySubAccount") != true
            ) {
                return true
            }
        }
        val dirSnap = runCatching { db.collection("userDirectory").document(targetUserId).get().await() }.getOrNull()
        if (dirSnap != null && dirSnap.exists()) {
            if (dirSnap.getString("proTier").equals(ProTier.BUSINESS.name, ignoreCase = true) &&
                dirSnap.getBoolean("companySubAccount") != true
            ) {
                return true
            }
        }
        return false
    }

    private suspend fun reserveAddPeopleSlots(
        uid: String,
        count: Int = 1,
        bypassForAdmin: Boolean = false
    ) {
        if (bypassForAdmin || count <= 0) return
        val nowMs = System.currentTimeMillis()
        val windowMs = 10L * 60L * 1000L // 10 minutes
        val userRef = db.collection("users").document(uid)
        val snap = runCatching { userRef.get().await() }.getOrNull() ?: return
        if (!snap.exists()) return
        val raw = (snap.get("addPeopleTimestamps") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            .orEmpty()
        val recent = raw.filter { it > nowMs - windowMs && it <= nowMs + 60_000L }.sorted()
        if (recent.size + count > 25) {
            val oldest = recent.firstOrNull() ?: nowMs
            val waitMs = (oldest + windowMs - nowMs).coerceAtLeast(1_000L)
            val waitMins = ((waitMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            throw IllegalStateException(
                "Rate limit reached: You cannot add more than 25 people in 10 minutes. Please wait ${waitMins}m."
            )
        }
        val updated = (recent + List(count) { nowMs }).takeLast(30)
        runCatching {
            userRef.update(
                mapOf(
                    "addPeopleTimestamps" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun enforceMessagingAllowedAndAntiSpam(
        uid: String,
        senderName: String,
        senderTier: StaffTier
    ) {
        val userRef = db.collection("users").document(uid)
        val snap = runCatching { userRef.get().await() }.getOrNull()
        val nowMs = System.currentTimeMillis()
        if (snap != null && snap.exists()) {
            val prof = runCatching { snap.toObject(UserProfile::class.java) }.getOrNull()
            if (snap.getBoolean("disableMessaging") == true ||
                prof?.isFeatureRestricted(RestrictableFeature.DM, nowMs) == true
            ) {
                throw IllegalStateException(
                    prof?.featureRestrictionNotice(RestrictableFeature.DM, nowMs)
                        ?: "Your messaging has been disabled by Staff."
                )
            }
        }
        if (senderTier == StaffTier.ADMIN) return
        if (!MessageSpamTracker.checkAndRecordMessageAttempt(uid, nowMs)) return

        val existingViolations = (snap?.get("spamViolationTimestamps") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            .orEmpty()
            .filter { it > nowMs - MessageSpamTracker.ESCALATION_WINDOW_24H_MS && it <= nowMs + 60_000L }
            .sorted()
        val updatedViolations = (existingViolations + nowMs).takeLast(20)
        val strikeNumber = updatedViolations.size
        val existingSuspendedUntil = (snap?.get("suspendedUntilMillis") as? Number)?.toLong() ?: 0L
        val safeName = senderName.trim().ifBlank { "Member" }.take(40)

        when (strikeNumber) {
            1 -> {
                runCatching {
                    userRef.update(
                        mapOf(
                            "spamViolationTimestamps" to updatedViolations,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                val warnMsg = "Spam Warning (Strike 1 of 4): You sent more than 20 messages in 30 seconds. Slow down — a 2nd offense will suspend your account for 1 hour."
                MessageSpamTracker.postSpamWarning(warnMsg)
                throw IllegalStateException(warnMsg)
            }
            2 -> {
                val untilMs = maxOf(nowMs + 1L * 60L * 60L * 1000L, existingSuspendedUntil + 1_000L)
                val reason = "Automated 1-hour suspension: 2nd message spam violation (>20 messages in 30 seconds)."
                runCatching {
                    userRef.update(
                        mapOf(
                            "spamViolationTimestamps" to updatedViolations,
                            "suspendedUntilMillis" to untilMs,
                            "suspensionReason" to reason.take(200),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                throw IllegalStateException(reason)
            }
            3 -> {
                val untilMs = maxOf(nowMs + 4L * 60L * 60L * 1000L, existingSuspendedUntil + 1_000L)
                val reason = "Automated 4-hour suspension: 3rd message spam violation (>20 messages in 30 seconds). A report has been sent to Staff."
                runCatching {
                    userRef.update(
                        mapOf(
                            "spamViolationTimestamps" to updatedViolations,
                            "suspendedUntilMillis" to untilMs,
                            "suspensionReason" to reason.take(200),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                runCatching {
                    submitAutoModSpamReport(
                        uid = uid,
                        userName = safeName,
                        details = "Automated Staff Report (3rd Strike): $safeName sent more than 20 messages in 30 seconds for a 3rd time within 24 hours. Applied 4-hour suspension."
                    )
                }
                throw IllegalStateException(reason)
            }
            else -> {
                val untilMs = maxOf(nowMs + 24L * 60L * 60L * 1000L, existingSuspendedUntil + 1_000L)
                val reason = "Automated 24-hour suspension: 4th message spam violation within 24 hours. Reported to Staff for action."
                runCatching {
                    userRef.update(
                        mapOf(
                            "spamViolationTimestamps" to updatedViolations,
                            "suspendedUntilMillis" to untilMs,
                            "suspensionReason" to reason.take(200),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                runCatching {
                    submitAutoModSpamReport(
                        uid = uid,
                        userName = safeName,
                        details = "Automated Staff Report (4th Strike — Action Required): $safeName exceeded 20 messages in 30 seconds 4+ times within 24 hours. Applied 24-hour suspension. Staff/Admin can disable messaging."
                    )
                }
                throw IllegalStateException(reason)
            }
        }
    }

    private suspend fun submitAutoModSpamReport(
        uid: String,
        userName: String,
        details: String
    ) {
        db.collection("safetyReports").document().set(
            mapOf(
                "roomId" to "AUTOMOD_SPAM",
                "roomTitle" to "Message Spam Auto-Mod",
                "roomHostId" to "SYSTEM_AUTOMOD",
                "reporterId" to uid,
                "reporterName" to "AutoMod",
                "targetUserId" to uid,
                "targetName" to userName.take(40),
                "category" to ReportCategory.SPAM_SCAM.name,
                "details" to details.take(500),
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
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
            if (actorTier != StaffTier.ADMIN && isTargetUserBusinessAccount(targetUserId)) {
                return Result.failure(
                    IllegalStateException(
                        "Support Staff cannot suspend a Business Account. Submit an App Admin approval request from the Business tab."
                    )
                )
            }
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

            val pairId = canonicalPairId(uid, targetUserId)
            // When blocking: remove from friends list so they no longer appear as a friend
            if (block) {
                runCatching {
                    val friendRef = db.collection("friendships").document(pairId)
                    if (friendRef.get().await().exists()) {
                        friendRef.delete().await()
                    }
                }
            }

            // Update 1-on-1 DM thread blockedByUserIds so incoming messages stop immediately (or resume on unblock)
            runCatching {
                val threadRef = db.collection("directThreads").document(pairId)
                val threadSnap = threadRef.get().await()
                if (threadSnap.exists()) {
                    val thread = threadSnap.toObject(DirectThread::class.java)
                    if (thread != null && !thread.isGroup) {
                        val nextBlocked = if (block) {
                            (thread.blockedByUserIds + uid).distinct()
                        } else {
                            thread.blockedByUserIds.filterNot { it == uid }
                        }
                        if (nextBlocked != thread.blockedByUserIds) {
                            val nextUnread = thread.unreadByUserIds.filterNot { it == uid }
                            threadRef.update(
                                mapOf(
                                    "blockedByUserIds" to nextBlocked,
                                    "unreadByUserIds" to nextUnread,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- ROOMS DISCOVERY & MANAGEMENT ---

    suspend fun ensureOfficialVoxChatRoomExists(): Result<Unit> {
        val path = "rooms/${ChatRoom.VOX_CHAT_ROOM_ID}"
        return try {
            requireUserId()
            val docRef = db.collection("rooms").document(ChatRoom.VOX_CHAT_ROOM_ID)
            val snap = docRef.get().await()
            if (snap.exists()) {
                return Result.success(Unit)
            }
            val payload = mapOf(
                "hostId" to ChatRoom.VOX_APP_HOST_ID,
                "hostName" to "VOX",
                "hostTier" to StaffTier.ADMIN.name,
                "title" to "VOX CHAT",
                "topic" to "Official always-open VOX community lounge",
                "category" to RoomCategory.LOUNGE.name,
                "coverKey" to RoomCoverKey.HERO.name,
                "visibility" to RoomVisibility.PUBLIC.name,
                "status" to RoomStatus.LIVE.name,
                "videoEnabled" to true,
                "autoModEnabled" to true,
                "autoModBlockMode" to false,
                "linksBlocked" to true,
                "roomLocked" to false,
                "pinLocked" to false,
                "roomPinCode" to "",
                "stageMicLocked" to false,
                "adminMicPriorityActive" to false,
                "officialVoxChat" to true,
                "hiddenFromLobby" to false,
                "maxPrivateUsers" to 0L,
                "slowModeSeconds" to 0L,
                "maxSpeakers" to 8L,
                "participantCount" to 0L,
                "moderatorIds" to listOf(ChatRoom.VOX_APP_HOST_ID),
                "bannedUserIds" to emptyList<String>(),
                "bannedNamesByUserId" to emptyMap<String, String>(),
                "bouncedUntilByUserId" to emptyMap<String, Long>(),
                "bouncedNamesByUserId" to emptyMap<String, String>(),
                "mutedUserIds" to emptyList<String>(),
                "blockedWords" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeOfficialVoxChatRoom(): Flow<ChatRoom?> = observeRoom(ChatRoom.VOX_CHAT_ROOM_ID)

    suspend fun updateOfficialVoxChatControls(
        enabled: Boolean,
        hiddenFromLobby: Boolean,
        pinLocked: Boolean,
        roomPinCode: String,
        actorName: String
    ): Result<Unit> {
        val path = "rooms/${ChatRoom.VOX_CHAT_ROOM_ID}"
        return try {
            val uid = requireUserId()
            val cleanPin = roomPinCode.filter { it.isDigit() }.take(4)
            if (pinLocked) {
                require(cleanPin.length == 4) { "VOX CHAT PIN must be exactly 4 digits." }
            }
            ensureOfficialVoxChatRoomExists()
            db.collection("rooms").document(ChatRoom.VOX_CHAT_ROOM_ID).update(
                mapOf(
                    "status" to if (enabled) RoomStatus.LIVE.name else RoomStatus.CLOSED.name,
                    "hiddenFromLobby" to hiddenFromLobby,
                    "pinLocked" to pinLocked,
                    "roomPinCode" to if (pinLocked) cleanPin else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                writeModLog(
                    roomId = ChatRoom.VOX_CHAT_ROOM_ID,
                    actorId = uid,
                    actorName = actorName,
                    targetUserId = "",
                    targetName = "VOX CHAT",
                    actionType = ModActionType.SETTINGS,
                    reason = buildString {
                        append(if (enabled) "Enabled VOX CHAT" else "Disabled VOX CHAT")
                        append(if (hiddenFromLobby) " · Hidden from Lobby" else " · Visible in Lobby")
                        append(if (pinLocked) " · PIN locked" else " · PIN unlocked")
                    }
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

    private fun isRoomAliveForLobby(room: ChatRoom, nowMs: Long): Boolean {
        if (room.statusEnum() != RoomStatus.LIVE) return false
        if (room.isOfficialVoxChatRoom()) return true
        if (room.participantCount > 0L) return true
        val createdMs = room.createdAt?.toDate()?.time ?: nowMs
        // Give newly created rooms a 15-second grace window for the host to finish joining
        return (nowMs - createdMs) < 15_000L
    }

    fun observePublicRooms(): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("visibility", RoomVisibility.PUBLIC.name)
                .snapshots()
                .map { snapshot ->
                    val nowMs = System.currentTimeMillis()
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { isRoomAliveForLobby(it, nowMs) }
                        .sortedWith(
                            compareByDescending<ChatRoom> { it.isOfficialVoxChatRoom() }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
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

    fun observeMyRooms(userId: String): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Cross-user hosted rooms query denied." }
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("hostId", userId)
                .snapshots()
                .map { snapshot ->
                    val nowMs = System.currentTimeMillis()
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { isRoomAliveForLobby(it, nowMs) }
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
        maxSpeakers: Long,
        pinLocked: Boolean = false,
        roomPinCode: String = "",
        maxPrivateUsers: Long = 0L
    ): Result<String> {
        var path = "rooms"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(60)
            val cleanTopic = topic.trim().take(160)
            val cleanHost = hostName.trim().ifBlank { "Host" }.take(40)
            val cleanPin = roomPinCode.filter { it.isDigit() }.take(4)
            require(cleanTitle.length >= 3) { "Room title must be at least 3 characters." }
            require(slowModeSeconds in listOf(0L, 3L, 10L, 30L)) { "Invalid slow mode interval." }
            require(maxSpeakers in 2L..12L) { "Max speakers must be between 2 and 12." }
            if (pinLocked) {
                require(cleanPin.length == 4) { "Room PIN must be exactly 4 digits." }
            }

            // Restrict non-Admin users to only 1 active public or private room at a time
            if (hostTier != StaffTier.ADMIN) {
                val existingRooms = db.collection("rooms")
                    .whereEqualTo("hostId", uid)
                    .get()
                    .await()
                val existingLive = existingRooms.documents.firstOrNull {
                    it.getString("status") == RoomStatus.LIVE.name &&
                        (it.getLong("participantCount") ?: 0L) > 0L
                }
                if (existingLive != null) {
                    val existingTitle = existingLive.getString("title").orEmpty().ifBlank { "your active room" }
                    return Result.failure(
                        IllegalStateException(
                            "You can only have 1 active public or private room at a time. Close \"$existingTitle\" before creating a new room."
                        )
                    )
                }
            }

            val docRef = db.collection("rooms").document()
            path = docRef.path

            val resolvedMaxPrivateUsers = if (visibility == RoomVisibility.PRIVATE) {
                maxPrivateUsers.takeIf { it > 0L }?.coerceIn(
                    ChatRoom.FREE_MAX_PRIVATE_USERS.toLong(),
                    ChatRoom.ELITE_MAX_PRIVATE_USERS.toLong()
                ) ?: ChatRoom.FREE_MAX_PRIVATE_USERS.toLong()
            } else {
                0L
            }

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
                "pinLocked" to pinLocked,
                "roomPinCode" to if (pinLocked) cleanPin else "",
                "stageMicLocked" to false,
                "adminMicPriorityActive" to false,
                "officialVoxChat" to false,
                "hiddenFromLobby" to false,
                "maxPrivateUsers" to resolvedMaxPrivateUsers,
                "slowModeSeconds" to slowModeSeconds,
                "maxSpeakers" to maxSpeakers,
                "participantCount" to 0L,
                "moderatorIds" to listOf(uid),
                "bannedUserIds" to emptyList<String>(),
                "bannedNamesByUserId" to emptyMap<String, String>(),
                "bouncedUntilByUserId" to emptyMap<String, Long>(),
                "bouncedNamesByUserId" to emptyMap<String, String>(),
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
        enterInvisible: Boolean,
        enteredPin: String = "",
        deviceHash: String = ""
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "rooms/${room.id}/participants/$uid"
        val cleanDeviceHash = deviceHash.trim().ifBlank { profile.lastDeviceId.trim() }.take(128)
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
                // Bounce-Evasion Protection:
                // If this device was bounced from this room and the user tries to re-enter
                // under a different account/username, flag the user, notify Staff, and impose
                // a 5-day device suspension per VOX safety protocol.
                if (cleanDeviceHash.length >= 16 && room.isDeviceBouncedOut(cleanDeviceHash)) {
                    val nowMs = System.currentTimeMillis()
                    val suspendUntilMs = nowMs + 5L * 24L * 60L * 60L * 1000L
                    val currentName = profile.effectiveDisplayName().trim().ifBlank { "Member" }.take(40)
                    val evasionReason = "VOX Safety Protocol Violation: Attempted to re-enter \"${room.displayTitle().take(32)}\" under another username while this device was bounced. Your device is suspended for 5 days."
                        .take(240)
                    runCatching {
                        val devRef = db.collection("bannedDevices").document(cleanDeviceHash)
                        val devSnap = devRef.get().await()
                        val existingAct = devSnap.getBoolean("active") == true
                        val existingReported = devSnap.getBoolean("reportedDeviceFlag") == true
                        val existingDeletedFlag = devSnap.getBoolean("deletedAccountFlagged") == true
                        val existingPriorCount = (devSnap.getLong("priorReportCount") ?: 0L).toInt().coerceAtLeast(1)
                        val existingSusp = devSnap.getLong("suspendedUntilMillis") ?: 0L
                        val nextSusp = maxOf(existingSusp, suspendUntilMs)
                        val payload = mutableMapOf<String, Any>(
                            "deviceHash" to cleanDeviceHash,
                            "bannedUserId" to (devSnap.getString("bannedUserId").orEmpty().ifBlank { uid }),
                            "bannedUserName" to (devSnap.getString("bannedUserName").orEmpty().ifBlank { currentName }.take(40)),
                            "reason" to evasionReason,
                            "bannedByUid" to (devSnap.getString("bannedByUid").orEmpty().ifBlank { uid }),
                            "bannedByName" to (devSnap.getString("bannedByName").orEmpty().ifBlank { "VOX Safety Protocol" }.take(40)),
                            "active" to existingAct,
                            "reportedDeviceFlag" to existingReported,
                            "deletedAccountFlagged" to existingDeletedFlag,
                            "priorReportCount" to existingPriorCount,
                            "returningUserId" to uid,
                            "returningUserName" to currentName,
                            "returningFlagActive" to true,
                            "suspendedUntilMillis" to nextSusp,
                            "bounceEvasionFlag" to true,
                            "staffReviewStatus" to "BOUNCE_EVASION_SUSPENDED",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (devSnap.exists()) {
                            payload["createdAt"] = devSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                        } else {
                            payload["createdAt"] = FieldValue.serverTimestamp()
                        }
                        devRef.set(payload).await()
                    }
                    val reportId = runCatching {
                        recordAutoModReportAndUserFile(
                            uid = uid,
                            targetName = currentName,
                            roomId = "BOUNCE_EVASION_${room.id.take(36)}",
                            roomTitle = "Bounce Evasion (5-Day Device Suspension)",
                            category = ReportCategory.OTHER,
                            reportDetails = "VOX Safety Protocol Violation: $currentName attempted to re-enter \"${room.displayTitle().take(36)}\" from a bounced device (${cleanDeviceHash.take(12)}…) under another account/username. Device suspended for 5 days.",
                            noteReason = evasionReason,
                            suspendUntilMillis = suspendUntilMs,
                            suspendReason = evasionReason
                        )
                    }.getOrDefault("")
                    runCatching {
                        db.collection("adminNotifications").document().set(
                            mapOf(
                                "type" to AdminNotificationType.BOUNCE_EVASION_DEVICE_SUSPENSION.name,
                                "title" to "Bounce Evasion (5-Day Ban): $currentName".take(120),
                                "message" to "$currentName tried to re-enter \"${room.displayTitle().take(36)}\" using a different account on a bounced device (${cleanDeviceHash.take(12)}…). Device was suspended for 5 days per VOX safety protocol. Staff can lift this ban in Safety Center.".take(500),
                                "createdById" to uid,
                                "createdByName" to currentName,
                                "targetUserId" to uid,
                                "targetName" to currentName,
                                "referenceId" to cleanDeviceHash.take(260),
                                "countValue" to 1,
                                "read" to false,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    return Result.failure(IllegalStateException(evasionReason))
                }
            }
            if (room.isOfficialVoxChatRoom() && room.statusEnum() != RoomStatus.LIVE && !staffCapabilities.isAdmin) {
                return Result.failure(IllegalStateException("VOX CHAT is currently disabled by an Administrator."))
            }
            if (room.roomLocked && !room.hasModPrivileges(uid, staffCapabilities.tier)) {
                return Result.failure(IllegalStateException("This room is currently locked by moderators."))
            }
            val cleanPin = enteredPin.filter { it.isDigit() }.take(4)
            if (room.requiresPinForUser(uid, staffCapabilities.tier) && cleanPin != room.roomPinCode) {
                return Result.failure(IllegalStateException("A valid 4-digit room PIN is required to enter."))
            }

            val actuallyInvisible = enterInvisible && room.canEnterInvisibleInRoom(uid, staffCapabilities)
            val participantRef = db.collection("rooms").document(room.id)
                .collection("participants").document(uid)
            val existingSnap = participantRef.get().await()

            // Room Join Flood Protection:
            // A non-admin user cannot join the same room more than 3 times in 1 minute.
            // 1st offense -> 24-hour bounce from that room + report filed in user's moderation file.
            // 2nd+ offense -> 5-day app suspension + report in user's file + Admin notification.
            if (!staffCapabilities.isAdmin && !existingSnap.exists()) {
                val nowMs = System.currentTimeMillis()
                if (RoomJoinFloodTracker.recordAndCheckExceeded(uid, room.id, nowMs)) {
                    RoomJoinFloodTracker.clearForUserRoom(uid, room.id)
                    val floodUserName = profile.effectiveDisplayName().trim().ifBlank { "Member" }.take(40)
                    val priorFloodOffenses = runCatching {
                        db.collection("safetyReports")
                            .whereEqualTo("reporterId", uid)
                            .get()
                            .await()
                            .documents
                            .count { it.getString("roomId") == "AUTOMOD_ROOM_FLOOD" }
                    }.getOrDefault(0)

                    if (priorFloodOffenses == 0) {
                        val bounceUntilMs = nowMs + 24L * 60L * 60L * 1000L
                        runCatching {
                            val roomRef = db.collection("rooms").document(room.id)
                            val roomSnap = roomRef.get().await()
                            val updatedBounced = ((roomSnap.get("bouncedUntilByUserId") as? Map<*, *>)
                                ?.mapNotNull { (k, v) ->
                                    val key = k as? String ?: return@mapNotNull null
                                    val num = (v as? Number)?.toLong() ?: return@mapNotNull null
                                    key to num
                                }?.toMap().orEmpty() + (uid to bounceUntilMs))
                            val updatedNames = ((roomSnap.get("bouncedNamesByUserId") as? Map<*, *>)
                                ?.mapNotNull { (k, v) ->
                                    val key = k as? String ?: return@mapNotNull null
                                    val value = v as? String ?: return@mapNotNull null
                                    key to value
                                }?.toMap().orEmpty() + (uid to floodUserName))
                            val floodUpdate = mutableMapOf<String, Any>(
                                "bouncedUntilByUserId" to updatedBounced,
                                "bouncedNamesByUserId" to updatedNames,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (cleanDeviceHash.length >= 16) {
                                val updatedDevices = ((roomSnap.get("bouncedDevicesByHash") as? Map<*, *>)
                                    ?.mapNotNull { (k, v) ->
                                        val key = k as? String ?: return@mapNotNull null
                                        val num = (v as? Number)?.toLong() ?: return@mapNotNull null
                                        if (num > nowMs) key to num else null
                                    }?.toMap().orEmpty() + (cleanDeviceHash to bounceUntilMs))
                                    .entries
                                    .sortedByDescending { it.value }
                                    .take(100)
                                    .associate { it.key to it.value }
                                floodUpdate["bouncedDevicesByHash"] = updatedDevices
                            }
                            roomRef.update(floodUpdate).await()
                        }
                        runCatching {
                            sendMessage(
                                roomId = room.id,
                                senderName = "VOX AutoMod",
                                senderRole = ParticipantRole.MODERATOR,
                                senderTier = staffCapabilities.tier,
                                text = "$floodUserName was bounced for 24 hours (joined this room more than 3 times in 1 minute).",
                                messageType = MessageType.MOD_NOTICE,
                                flaggedByAutoMod = false,
                                displayedSenderTier = StaffTier.USER
                            )
                        }
                        runCatching {
                            recordAutoModReportAndUserFile(
                                uid = uid,
                                targetName = floodUserName,
                                roomId = "AUTOMOD_ROOM_FLOOD",
                                roomTitle = "Room Flood Bounce (24h)",
                                category = ReportCategory.SPAM_SCAM,
                                reportDetails = "AutoMod 1st Offense: $floodUserName tried to join \"${room.title.take(36)}\" more than 3 times in 1 minute. Bounced from the room for 24 hours.",
                                noteReason = "Room join flood (1st offense): Attempted >3 joins in 1 minute to \"${room.title.take(36)}\". Bounced for 24 hours."
                            )
                        }
                        return Result.failure(
                            IllegalStateException(
                                "You cannot join the same room more than 3 times in 1 minute. You have been bounced from this room for 24 hours and a report has been added to your account file. A 2nd offense will suspend your account for 5 days."
                            )
                        )
                    } else {
                        val suspendUntilMs = nowMs + 5L * 24L * 60L * 60L * 1000L
                        val suspendReason = "Suspended for 5 days: Repeated room join flooding (2nd offense — joined \"${room.title.take(36)}\" more than 3 times in 1 minute)."
                            .take(195)
                        runCatching {
                            val userRef = db.collection("users").document(uid)
                            val userSnap = userRef.get().await()
                            val existingSpamTs = (userSnap.get("spamViolationTimestamps") as? List<*>)
                                ?.mapNotNull { (it as? Number)?.toLong() }
                                .orEmpty()
                            val nextSpamTs = (existingSpamTs + nowMs).takeLast(20)
                            userRef.update(
                                mapOf(
                                    "spamViolationTimestamps" to nextSpamTs,
                                    "suspendedUntilMillis" to suspendUntilMs,
                                    "suspensionReason" to suspendReason,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        val reportId = runCatching {
                            recordAutoModReportAndUserFile(
                                uid = uid,
                                targetName = floodUserName,
                                roomId = "AUTOMOD_ROOM_FLOOD",
                                roomTitle = "5-Day Auto-Suspension (Room Flood #2)",
                                category = ReportCategory.SPAM_SCAM,
                                reportDetails = "AutoMod 2nd Offense: $floodUserName joined \"${room.title.take(36)}\" more than 3 times in 1 minute. Automatically suspended from the app for 5 days.",
                                noteReason = "Room join flood (2nd offense): Automatically suspended from app for 5 days. Administrator notified for corrective action.",
                                suspendUntilMillis = suspendUntilMs,
                                suspendReason = suspendReason
                            )
                        }.getOrDefault("")
                        runCatching {
                            db.collection("adminNotifications").document().set(
                                mapOf(
                                    "type" to AdminNotificationType.AUTOMOD_ROOM_FLOOD_SUSPENSION.name,
                                    "title" to "5-Day Auto-Suspension: $floodUserName".take(120),
                                    "message" to "$floodUserName triggered a 2nd room join flood offense (>3 joins in 1 minute in \"${room.title.take(36)}\") and was automatically suspended for 5 days. Review or remove suspension in the Admin Console.".take(500),
                                    "createdById" to uid,
                                    "createdByName" to floodUserName,
                                    "targetUserId" to uid,
                                    "targetName" to floodUserName,
                                    "referenceId" to reportId.take(260),
                                    "countValue" to (priorFloodOffenses + 1),
                                    "read" to false,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        return Result.failure(
                            IllegalStateException(
                                "2nd Room Join Flood Offense: Your account has been suspended for 5 days and an Administrator has been notified for corrective action."
                            )
                        )
                    }
                }
            }

            // Enforce Private Chat Room user limits by host membership tier:
            // Free = 25, Pro = 50, VIP = 75, Elite = 100
            if (!existingSnap.exists() &&
                room.visibilityEnum() == RoomVisibility.PRIVATE &&
                !actuallyInvisible &&
                !staffCapabilities.isAdmin &&
                room.hostId != uid
            ) {
                val hostDir = runCatching {
                    db.collection("userDirectory").document(room.hostId).get().await()
                        .toObject(DirectoryEntry::class.java)
                }.getOrNull()
                val hostCap = hostDir?.maxPrivateRoomUsers() ?: ChatRoom.FREE_MAX_PRIVATE_USERS
                val maxPrivateUsers = room.maxPrivateUsers.toInt()
                    .takeIf { it > 0 }
                    ?.coerceAtMost(hostCap)
                    ?: hostCap
                if (room.participantCount >= maxPrivateUsers) {
                    return Result.failure(
                        IllegalStateException(
                            "This private room is full ($maxPrivateUsers/$maxPrivateUsers users). Private room limits: Free (25), Pro (50), VIP (75), Elite (100)."
                        )
                    )
                }
            }

            val assignedRole = when {
                room.hostId == uid -> ParticipantRole.HOST
                actuallyInvisible -> ParticipantRole.LISTENER
                room.moderatorIds.contains(uid) || staffCapabilities.isStaff -> ParticipantRole.MODERATOR
                else -> ParticipantRole.SPEAKER
            }
            val isMuted = room.mutedUserIds.contains(uid)

            val effectiveName = profile.effectiveDisplayName().trim().ifBlank { "Guest" }.take(40)
            val displayedTier = profile.displayedStaffTier(staffCapabilities.tier)
            if (!existingSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "roomId" to room.id,
                    "displayName" to effectiveName,
                    "avatarColorHex" to profile.avatarColorHex,
                    "role" to assignedRole.name,
                    "staffTier" to displayedTier.name,
                    "invisible" to actuallyInvisible,
                    "micActive" to false,
                    "camActive" to false,
                    "handRaised" to false,
                    "mutedByMod" to isMuted,
                    "speakingLevel" to 0.0,
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanPin.length == 4) {
                    payload["enteredPin"] = cleanPin
                }
                if (cleanDeviceHash.isNotEmpty()) {
                    payload["deviceHash"] = cleanDeviceHash
                }
                participantRef.set(payload).await()

                if (!actuallyInvisible) {
                    db.collection("rooms").document(room.id).update(
                        mapOf(
                            "participantCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    if (!isMuted) {
                        runCatching {
                            sendMessage(
                                roomId = room.id,
                                senderName = effectiveName,
                                senderRole = assignedRole,
                                senderTier = staffCapabilities.tier,
                                text = "$effectiveName joined the room",
                                messageType = MessageType.JOIN_LEAVE,
                                flaggedByAutoMod = false,
                                displayedSenderTier = displayedTier
                            )
                        }
                    }
                }
            } else {
                val prevInvisible = existingSnap.getBoolean("invisible") == true
                participantRef.update(
                    mapOf(
                        "displayName" to effectiveName,
                        "avatarColorHex" to profile.avatarColorHex,
                        "staffTier" to displayedTier.name,
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
                    if (!isMuted) {
                        runCatching {
                            sendMessage(
                                roomId = room.id,
                                senderName = effectiveName,
                                senderRole = assignedRole,
                                senderTier = staffCapabilities.tier,
                                text = "$effectiveName joined the room",
                                messageType = MessageType.JOIN_LEAVE,
                                flaggedByAutoMod = false,
                                displayedSenderTier = displayedTier
                            )
                        }
                    }
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
                require(room.canEnterInvisibleInRoom(uid, staffCapabilities)) {
                    "Invisible mode is only available for this room's Admin or authorized staff."
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
            val roomRef = db.collection("rooms").document(roomId)
            val participantRef = roomRef.collection("participants").document(uid)
            val snap = participantRef.get().await()
            val participantName = snap.getString("displayName")?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
            val participantRole = ParticipantRole.fromRaw(snap.getString("role").orEmpty())
            val participantTier = StaffTier.fromRaw(snap.getString("staffTier"))
            val wasMutedByMod = snap.getBoolean("mutedByMod") == true
            val docInvisible = if (snap.exists()) {
                val inv = snap.getBoolean("invisible") == true || wasInvisible
                participantRef.delete().await()
                inv
            } else {
                wasInvisible
            }

            val roomSnap = runCatching { roomRef.get().await() }.getOrNull()
            if (roomSnap == null || !roomSnap.exists()) {
                return Result.success(Unit)
            }
            val isOfficial = roomId == ChatRoom.VOX_CHAT_ROOM_ID ||
                roomSnap.getBoolean("officialVoxChat") == true
            val roomIsLive = roomSnap.getString("status") == RoomStatus.LIVE.name
            val liveCount = roomSnap.getLong("participantCount") ?: currentParticipantCount

            suspend fun broadcastLeaveNoticeIfEligible() {
                if (snap.exists() && !docInvisible && !wasMutedByMod && roomIsLive) {
                    runCatching {
                        val msgRef = roomRef.collection("messages").document()
                        msgRef.set(
                            mapOf(
                                "roomId" to roomId,
                                "senderId" to uid,
                                "senderName" to participantName.take(40),
                                "senderRole" to participantRole.name,
                                "senderTier" to participantTier.name,
                                "text" to "$participantName left the room".take(500),
                                "messageType" to MessageType.JOIN_LEAVE.name,
                                "flaggedByAutoMod" to false,
                                "deletedByMod" to false,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (isOfficial) {
                // Official VOX CHAT stays open even with 0 users
                broadcastLeaveNoticeIfEligible()
                if (snap.exists() && !docInvisible && liveCount > 0L) {
                    roomRef.update(
                        mapOf(
                            "participantCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val remainingSnap = runCatching {
                    roomRef.collection("participants")
                        .whereEqualTo("roomId", roomId)
                        .get()
                        .await()
                }.getOrNull()
                val remainingVisible = remainingSnap?.documents?.count {
                    it.getBoolean("invisible") != true
                } ?: (liveCount - if (snap.exists() && !docInvisible) 1L else 0L).toInt().coerceAtLeast(0)
                val remainingTotal = remainingSnap?.size() ?: remainingVisible

                if (remainingTotal == 0 || remainingVisible == 0) {
                    // Auto-remove any regular room once it reaches 0 users
                    val deleted = runCatching { roomRef.delete().await() }.isSuccess
                    if (!deleted) {
                        runCatching {
                            roomRef.update(
                                mapOf(
                                    "status" to RoomStatus.CLOSED.name,
                                    "participantCount" to 0L,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                } else {
                    broadcastLeaveNoticeIfEligible()
                    if (snap.exists() && !docInvisible && liveCount > 0L) {
                        roomRef.update(
                            mapOf(
                                "participantCount" to FieldValue.increment(-1L),
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
            val bouncedDeviceHash = snap.getString("deviceHash").orEmpty().trim().take(128)
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

            // Any bounce from a chat room imposes a 24-hour lockout & records the user's device ID
            // so they cannot evade the bounce by returning under another username.
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
            val updatedBounceNames = room.bouncedNamesByUserId
                .filterKeys { updatedBounces.containsKey(it) }
                .toMutableMap()
                .apply { put(targetUserId, targetName.trim().ifBlank { "Member" }.take(40)) }
            val roomBounceUpdate = mutableMapOf<String, Any>(
                "bouncedUntilByUserId" to updatedBounces,
                "bouncedNamesByUserId" to updatedBounceNames,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val resolvedBounceDeviceHash = if (bouncedDeviceHash.length >= 16) {
                bouncedDeviceHash
            } else if (actorTier.isStaff()) {
                runCatching {
                    db.collection("users").document(targetUserId).get().await()
                        .getString("lastDeviceId").orEmpty().trim().take(128)
                }.getOrDefault("")
            } else {
                ""
            }
            if (resolvedBounceDeviceHash.length >= 16) {
                val updatedBounceDevices = room.bouncedDevicesByHash
                    .filterValues { it > nowMs }
                    .toMutableMap()
                    .apply { put(resolvedBounceDeviceHash, bounceUntilMs) }
                    .entries
                    .sortedByDescending { it.value }
                    .take(100)
                    .associate { it.key to it.value }
                roomBounceUpdate["bouncedDevicesByHash"] = updatedBounceDevices
            }
            db.collection("rooms").document(room.id).update(roomBounceUpdate).await()

            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = targetName,
                actionType = ModActionType.BOUNCE,
                reason = reason.ifBlank { "Bounced for 24 hours by $actorName" }
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = "$targetName was bounced from the room for 24 hours"
            )
            if (actorTier.isStaff()) {
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

    suspend fun removeBounce(
        room: ChatRoom,
        targetUserId: String,
        targetName: String,
        actorName: String,
        actorTier: StaffTier
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.canManageBansAndBounces(uid, actorTier)) {
                "Only the Room Administrator or App Administrator can remove a room bounce."
            }
            val nowMs = System.currentTimeMillis()
            val updatedBounces = room.bouncedUntilByUserId
                .filter { (k, v) -> k != targetUserId && v > nowMs }
            val updatedBounceNames = room.bouncedNamesByUserId
                .filterKeys { updatedBounces.containsKey(it) }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "bouncedUntilByUserId" to updatedBounces,
                    "bouncedNamesByUserId" to updatedBounceNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val safeName = targetName.ifBlank { room.bouncedNamesByUserId[targetUserId].orEmpty().ifBlank { targetUserId.take(16) } }
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = safeName,
                actionType = ModActionType.UNBOUNCE,
                reason = "24-hour bounce removed by $actorName"
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = "$safeName's bounce was removed by $actorName"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomPinLock(
        room: ChatRoom,
        pinLocked: Boolean,
        pinCode: String,
        actorName: String,
        actorTier: StaffTier
    ): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.canManagePinLock(uid, actorTier)) {
                "Only the Room Administrator or App Administrator can set a room PIN."
            }
            val cleanPin = pinCode.filter { it.isDigit() }.take(4)
            if (pinLocked) {
                require(cleanPin.length == 4) { "Room PIN must be exactly 4 digits." }
            }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "pinLocked" to pinLocked,
                    "roomPinCode" to if (pinLocked) cleanPin else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = "",
                targetName = "",
                actionType = ModActionType.PIN_LOCK,
                reason = if (pinLocked) "4-digit PIN lock enabled by $actorName" else "Room PIN lock removed by $actorName"
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = if (pinLocked) "Room is now protected by a 4-digit PIN" else "Room PIN lock was removed"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
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
                val updatedBannedNames = room.bannedNamesByUserId
                    .filterKeys { updatedBanned.contains(it) }
                    .toMutableMap()
                    .apply { put(targetUserId, targetName.trim().ifBlank { "Member" }.take(40)) }
                val updatedMuted = room.mutedUserIds.filterNot { it == targetUserId }
                val updatedBounces = room.bouncedUntilByUserId.filterKeys { it != targetUserId }
                val updatedBounceNames = room.bouncedNamesByUserId.filterKeys { it != targetUserId }
                db.collection("rooms").document(room.id).update(
                    mapOf(
                        "bannedUserIds" to updatedBanned,
                        "bannedNamesByUserId" to updatedBannedNames,
                        "bouncedUntilByUserId" to updatedBounces,
                        "bouncedNamesByUserId" to updatedBounceNames,
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
            val safeName = room.bannedDisplayName(targetUserId)
            val updatedBanned = room.bannedUserIds.filterNot { it == targetUserId }
            val updatedBannedNames = room.bannedNamesByUserId.filterKeys { updatedBanned.contains(it) }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "bannedUserIds" to updatedBanned,
                    "bannedNamesByUserId" to updatedBannedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeModLog(
                roomId = room.id,
                actorId = uid,
                actorName = actorName,
                targetUserId = targetUserId,
                targetName = safeName.take(40),
                actionType = ModActionType.UNBAN,
                reason = "Ban lifted by $actorName"
            )
            sendModNotice(
                roomId = room.id,
                actorName = actorName,
                actorTier = actorTier,
                text = "$safeName was unbanned by $actorName"
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
        flaggedByAutoMod: Boolean = false,
        displayedSenderTier: StaffTier = senderTier
    ): Result<Unit> {
        var path = "rooms/$roomId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            if (messageType != MessageType.JOIN_LEAVE) {
                enforceMessagingAllowedAndAntiSpam(
                    uid = uid,
                    senderName = senderName,
                    senderTier = senderTier
                )
            }
            val docRef = db.collection("rooms").document(roomId)
                .collection("messages").document()
            path = docRef.path
            val payload = mapOf(
                "roomId" to roomId,
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Member" }.take(40),
                "senderRole" to senderRole.name,
                "senderTier" to displayedSenderTier.name,
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
            val safeReporterName = reporterName.trim().ifBlank { "Member" }.take(40)
            val safeTargetName = targetName.trim().ifBlank { "User" }.take(40)
            val safeTargetId = targetUserId.take(128)
            val payload = mapOf(
                "reporterId" to uid,
                "reporterName" to safeReporterName,
                "roomId" to room.id,
                "roomTitle" to room.displayTitle().take(60),
                "roomHostId" to room.hostId,
                "targetUserId" to safeTargetId,
                "targetName" to safeTargetName,
                "category" to category.name,
                "details" to cleanDetails,
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            commitReportWithAutomatedStrikes(
                reportRef = docRef,
                reportPayload = payload,
                reporterId = uid,
                reporterName = safeReporterName,
                targetUserId = safeTargetId,
                targetName = safeTargetName,
                category = category.name,
                details = cleanDetails
            )
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
        val cleanId = reportId.trim()
        val path = "safetyReports/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotEmpty()) { "Missing report ID — refresh and try again." }
            require(newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
                "Report can only transition to RESOLVED or DISMISSED."
            }
            db.collection("safetyReports").document(cleanId).update(
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

            reserveAddPeopleSlots(
                uid = uid,
                count = 1,
                bypassForAdmin = myTier == StaffTier.ADMIN
            )

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
                    "blockedByUserIds" to emptyList<String>(),
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
            // Youth Safety Monitoring: if an 18+ user opens a private 1:1 DM with an under-18 (13-17) user,
            // notify VOX Staff and open staff monitoring access (Visible or Invisible).
            val wasAlreadyFlagged = snap.exists() && snap.getBoolean("reportStaffAccessOpen") == true
            if (!wasAlreadyFlagged && !myTier.isStaff() && !otherTier.isStaff()) {
                runCatching {
                    flagAdultMinorPrivateDmIfNeeded(
                        threadId = threadId,
                        callerUid = uid,
                        callerName = myName,
                        callerIsAdult = myProfile.isAdultViewer18Plus(),
                        callerIsMinor = myProfile.isMinor13To17(),
                        otherUserId = otherUserId,
                        otherName = cleanOtherName
                    )
                }
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
            reserveAddPeopleSlots(
                uid = uid,
                count = 1,
                bypassForAdmin = myTier == StaffTier.ADMIN
            )
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
                        "groupAdminId" to uid,
                        "groupSupportIds" to emptyList<String>(),
                        "pttAllowedUserIds" to emptyList<String>(),
                        "participantNamesById" to namesMap,
                        "kickedUserIds" to emptyList<String>(),
                        "mutedUserIds" to emptyList<String>(),
                        "bouncedUntilByUserId" to emptyMap<String, Long>(),
                        "bouncedNamesByUserId" to emptyMap<String, String>(),
                        "bannedUserIds" to emptyList<String>(),
                        "bannedNamesByUserId" to emptyMap<String, String>(),
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
                val currentCount = currentThread.participantIds.size
                require(currentCount < DirectThread.MAX_GROUP_MEMBERS) {
                    "Group DMs cannot exceed ${DirectThread.MAX_GROUP_MEMBERS} users."
                }
                val adminUid = currentThread.effectiveGroupAdminId()
                val adminTier = if (adminUid == uid) {
                    myProfile.effectiveProTier(myTier)
                } else {
                    val dirSnap = runCatching {
                        db.collection("userDirectory").document(adminUid).get().await()
                    }.getOrNull()
                    dirSnap?.toObject(DirectoryEntry::class.java)?.effectiveProTier() ?: ProTier.FREE
                }
                val maxAllowed = adminTier.maxGroupDmMembers.coerceAtMost(DirectThread.MAX_GROUP_MEMBERS)
                if (currentCount >= maxAllowed) {
                    val upgradeMsg = when (adminTier) {
                        ProTier.FREE -> "Free Group DMs cannot exceed 5 users. Upgrade to VOX Pro (\$9.99/mo) for up to 15 users."
                        ProTier.PRO -> "VOX Pro Group DMs support up to 15 users. Upgrade to VIP Pro (\$29.99/mo) for up to 25 users."
                        ProTier.VIP_PRO -> "VIP Pro Group DMs support up to 25 users. Upgrade to a Business Account (\$99.99/mo) for up to 50 users."
                        ProTier.BUSINESS -> "Group DMs cannot exceed 50 users."
                    }
                    return Result.failure(IllegalStateException(upgradeMsg))
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
     * Pays $5 to unlock private messaging with a user whose DMs are locked to paid members.
     * Full $5 (500c) goes to the app owner; the recipient earns +1 usable in-app point.
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
                    "priceCents" to 500L,
                    "creatorShareCents" to 0L,
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
                    "totalSpentCents" to FieldValue.increment(500L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                recipientRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(1L),
                    "creatorEarnedPoints" to FieldValue.increment(1L),
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

    fun observeDirectMessages(
        threadId: String,
        asCompanyOwnerId: String = "",
        asBusinessStaffHelper: Boolean = false
    ): Flow<List<DirectMessage>> = flow {
        val uid = requireUserId()
        val path = "directThreads/$threadId/messages"
        val query = when {
            asCompanyOwnerId.isNotBlank() && asCompanyOwnerId == uid ->
                db.collection("directThreads").document(threadId)
                    .collection("messages")
                    .whereEqualTo("companyOwnerId", asCompanyOwnerId)
            asBusinessStaffHelper ->
                db.collection("directThreads").document(threadId)
                    .collection("messages")
            else ->
                db.collection("directThreads").document(threadId)
                    .collection("messages")
                    .whereArrayContains("participantIds", uid)
        }
        emitAll(
            query
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
        audioDurationSec: Long = 0L,
        messageType: String = DirectMessage.TYPE_TEXT,
        hideSenderName: Boolean = false,
        displayedSenderTier: StaffTier = senderTier
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
            require(!thread.isGroup || !thread.groupDisabled || senderTier.isStaff()) {
                "This Group DM has been disabled by VOX Staff${thread.groupDisabledReason.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}."
            }
            require(!thread.isUserKicked(uid)) { "You have been removed from messaging in this chat." }
            require(!thread.isBannedInGroup(uid)) { "You are banned from this group DM by the Group Admin." }
            require(!thread.isBouncedInGroup(uid)) { "You are bounced from this group DM (${thread.remainingGroupBounceLabel(uid)})." }
            require(!thread.isMutedInGroup(uid)) { "You are muted in this group DM." }
            if (messageType == DirectMessage.TYPE_TEXT) {
                enforceMessagingAllowedAndAntiSpam(
                    uid = uid,
                    senderName = senderName,
                    senderTier = senderTier
                )
            }
            if (cleanAudio.isNotEmpty() && thread.requiresAdminPttSelection()) {
                require(thread.isPttAllowedInGroup(uid, isAppAdmin = senderTier == StaffTier.ADMIN)) {
                    "Only members selected by the Group DM Administrator can use Push-to-Talk in groups with more than 15 users."
                }
            }
            require(thread.isGroup || !thread.hasAnyBlock()) { "Messaging is blocked in this conversation." }
            require(cleanImage.length <= 460_000) { "Picture is too large to send. Please try again." }
            require(cleanAudio.length <= 300_000) { "Voice note is too long. Please keep voice notes under 30 seconds." }
            val updatedPicTimestamps = if (cleanImage.isNotEmpty()) {
                reservePictureUploadSlot(uid)
            } else null

            val isVisibleCompanyOwner = !thread.isGroup &&
                thread.companyOwnerId == uid &&
                !thread.participantIds.contains(uid)
            val baseSafeName = senderName.trim().ifBlank { "Member" }
            val safeName = if (isVisibleCompanyOwner && !baseSafeName.contains("(Owner)", ignoreCase = true)) {
                "$baseSafeName (Owner)".take(40)
            } else {
                baseSafeName.take(40)
            }
            val safeType = if (messageType in setOf(
                    DirectMessage.TYPE_TEXT,
                    DirectMessage.TYPE_SCREENSHOT,
                    DirectMessage.TYPE_SUPPORT_HANDOFF
                )
            ) messageType else DirectMessage.TYPE_TEXT
            if (hideSenderName && thread.isGroup && thread.anonymousModeDisabled) {
                throw IllegalArgumentException("Anonymous Mode (Hide Name) has been disabled by the Group DM Administrator.")
            }
            val effectiveHideName = hideSenderName && thread.isGroup && !thread.anonymousModeDisabled && safeType == DirectMessage.TYPE_TEXT
            val payload = mutableMapOf<String, Any>(
                "threadId" to thread.id,
                "participantIds" to thread.participantIds,
                "senderId" to uid,
                "senderName" to safeName,
                "senderTier" to displayedSenderTier.name,
                "text" to cleanText,
                "messageType" to safeType,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (thread.companyOwnerId.isNotBlank()) {
                payload["companyOwnerId"] = thread.companyOwnerId
            }
            if (effectiveHideName) {
                payload["hideSenderName"] = true
            }
            if (cleanImage.isNotEmpty()) {
                payload["imageBase64"] = cleanImage
            }
            if (cleanAudio.isNotEmpty()) {
                payload["audioBase64"] = cleanAudio
                payload["audioDurationSec"] = safeAudioDuration.coerceAtLeast(1L)
            }
            if (thread.disappearingDurationSec > 0L && safeType == DirectMessage.TYPE_TEXT) {
                payload["expiresAtMillis"] = System.currentTimeMillis() + (thread.disappearingDurationSec * 1000L)
            }

            val rawPreview = when {
                safeType == DirectMessage.TYPE_SCREENSHOT -> "📸 $cleanText"
                cleanAudio.isNotEmpty() && cleanText.isNotEmpty() -> "🎤 $cleanText"
                cleanAudio.isNotEmpty() -> "🎤 Voice note (${safeAudioDuration.coerceAtLeast(1L)}s)"
                cleanImage.isNotEmpty() && cleanText.isNotEmpty() -> "📷 $cleanText"
                cleanImage.isNotEmpty() -> "📷 Sent a snap"
                else -> cleanText
            }
            val previewText = (if (effectiveHideName) "Hidden Member: $rawPreview" else rawPreview).take(300)

            val unreadRecipients = thread.participantIds.filter { it != uid }
            val threadUpdate = mutableMapOf<String, Any>(
                "lastMessageText" to previewText,
                "lastMessageSenderId" to uid,
                "unreadByUserIds" to unreadRecipients,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!thread.isGroup && !isVisibleCompanyOwner && safeType == DirectMessage.TYPE_TEXT) {
                val streakFields = computeNextTalkStreakUpdate(thread, uid)
                threadUpdate.putAll(streakFields)
            }
            if (!isVisibleCompanyOwner && thread.participantIds.contains(uid) && thread.typingByUserId.containsKey(uid)) {
                threadUpdate["typingByUserId.$uid"] = FieldValue.delete()
            }
            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                db.collection("directThreads").document(thread.id),
                threadUpdate
            )
            if (updatedPicTimestamps != null) {
                batch.update(
                    db.collection("users").document(uid),
                    mapOf(
                        "pictureUploadTimestamps" to updatedPicTimestamps,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            // Youth Safety Monitoring: if an 18+ user sends a private DM to an under-18 user
            // on an unflagged 1:1 thread, immediately flag for Staff monitoring & alert Staff.
            if (!thread.isGroup &&
                !thread.reportStaffAccessOpen &&
                !thread.voxOfficial &&
                thread.companyOwnerId.isBlank() &&
                !senderTier.isStaff() &&
                !isVisibleCompanyOwner
            ) {
                val otherUid = thread.otherUserId(uid)
                if (otherUid.isNotBlank()) {
                    runCatching {
                        val myDir = db.collection("userDirectory").document(uid).get().await()
                            .toObject(DirectoryEntry::class.java)
                        flagAdultMinorPrivateDmIfNeeded(
                            threadId = thread.id,
                            callerUid = uid,
                            callerName = safeName,
                            callerIsAdult = myDir?.isAdult18Plus() == true,
                            callerIsMinor = myDir?.isMinor13To17() == true,
                            otherUserId = otherUid,
                            otherName = thread.otherName(uid)
                        )
                    }
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

    suspend fun setDirectThreadTyping(
        threadId: String,
        isTyping: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (threadId.isBlank()) return Result.success(Unit)
        return try {
            val value: Any = if (isTyping) {
                System.currentTimeMillis() + DIRECT_TYPING_TTL_MS
            } else {
                FieldValue.delete()
            }
            db.collection("directThreads")
                .document(threadId)
                .update(mapOf("typingByUserId.$uid" to value))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.d("VoxStageRepository", "Typing indicator update skipped for $threadId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun toggleDirectMessageReaction(
        threadId: String,
        messageId: String,
        currentReactions: Map<String, String>,
        emoji: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (emoji !in DirectMessage.ALLOWED_REACTIONS) {
            return Result.failure(IllegalArgumentException("Unsupported reaction"))
        }
        val msgRef = db.collection("directThreads").document(threadId)
            .collection("messages").document(messageId)
        val path = msgRef.path
        return try {
            val next = currentReactions.toMutableMap()
            if (next[uid] == emoji) {
                next.remove(uid)
            } else {
                next[uid] = emoji
            }
            msgRef.update("reactionsByUserId", next).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reportDirectMessage(
        threadId: String,
        peerTitle: String,
        reporterName: String,
        message: DirectMessage,
        category: ReportCategory,
        extraDetails: String,
        isGroupThread: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("safetyReports").document()
        val path = ref.path
        return try {
            val excerpt = when {
                message.text.isNotBlank() -> "\"${message.text.trim().take(160)}\""
                message.imageBase64.isNotBlank() -> "[Private photo snap]"
                message.audioBase64.isNotBlank() -> "[Private voice note]"
                else -> "[Private message]"
            }
            val note = extraDetails.trim()
            val combinedDetails = buildString {
                if (message.hideSenderName) {
                    append("[Hidden-Name Group DM Message] ")
                }
                append("Reported DM: ")
                append(excerpt)
                if (note.isNotEmpty()) {
                    append(" — ")
                    append(note)
                }
            }.take(400)
            val safeReporterName = reporterName.trim().ifBlank { "Member" }.take(40)
            val safeTargetId = message.senderId.ifBlank { uid }.take(128)
            val safeTargetName = message.senderName.trim().ifBlank { "Member" }.take(40)
            val titlePrefix = if (isGroupThread) "Group DM: " else "Private Chat: "
            val payload = mapOf(
                "reporterId" to uid,
                "reporterName" to safeReporterName,
                "roomId" to threadId.take(260),
                "roomTitle" to "$titlePrefix${peerTitle.ifBlank { "DM" }}".take(60),
                "roomHostId" to uid,
                "targetUserId" to safeTargetId,
                "targetName" to safeTargetName,
                "category" to category.name,
                "details" to combinedDetails,
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            commitReportWithAutomatedStrikes(
                reportRef = ref,
                reportPayload = payload,
                reporterId = uid,
                reporterName = safeReporterName,
                targetUserId = safeTargetId,
                targetName = safeTargetName,
                category = category.name,
                details = combinedDetails
            )
            if (isGroupThread && threadId.isNotBlank()) {
                runCatching {
                    db.collection("directThreads").document(threadId).update(
                        mapOf(
                            "reportStaffAccessOpen" to true,
                            "lastReportedAtMillis" to System.currentTimeMillis(),
                            "lastReportSummary" to "Message from $safeTargetName reported by $safeReporterName (${category.label})".take(240),
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

    suspend fun reportGroupDmUser(
        thread: DirectThread,
        reporterName: String,
        targetUserId: String,
        targetName: String,
        category: ReportCategory,
        extraDetails: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val ref = db.collection("safetyReports").document()
        val path = ref.path
        return try {
            require(thread.isGroup) { "User reporting here is for Group DMs." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Select another member to report." }
            val safeReporterName = reporterName.trim().ifBlank { "Member" }.take(40)
            val safeTargetName = targetName.trim().ifBlank { "Member" }.take(40)
            val note = extraDetails.trim()
            val combinedDetails = buildString {
                append("Reported Group DM member $safeTargetName (${category.label})")
                if (note.isNotEmpty()) {
                    append(" — ")
                    append(note)
                }
            }.take(400)
            val groupLabel = thread.groupName.ifBlank { "Group DM" }
            val payload = mapOf(
                "reporterId" to uid,
                "reporterName" to safeReporterName,
                "roomId" to thread.id.take(260),
                "roomTitle" to "Group DM: $groupLabel".take(60),
                "roomHostId" to uid,
                "targetUserId" to targetUserId.take(128),
                "targetName" to safeTargetName,
                "category" to category.name,
                "details" to combinedDetails,
                "status" to ReportStatus.OPEN.name,
                "resolutionNote" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            commitReportWithAutomatedStrikes(
                reportRef = ref,
                reportPayload = payload,
                reporterId = uid,
                reporterName = safeReporterName,
                targetUserId = targetUserId,
                targetName = safeTargetName,
                category = category.name,
                details = combinedDetails
            )
            runCatching {
                db.collection("directThreads").document(thread.id).update(
                    mapOf(
                        "reportStaffAccessOpen" to true,
                        "lastReportedAtMillis" to System.currentTimeMillis(),
                        "lastReportSummary" to "User $safeTargetName reported by $safeReporterName (${category.label})".take(240),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markDirectThreadRead(
        thread: DirectThread,
        sendReadReceipt: Boolean = true
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (!thread.participantIds.contains(uid)) return Result.success(Unit)
        val needsClearUnread = thread.unreadByUserIds.contains(uid)
        val allowReceipt = sendReadReceipt && thread.isReadReceiptsEnabledFor(uid)
        val threadUpdatedMs = thread.updatedAt?.toDate()?.time ?: 0L
        val myLastReadMs = thread.lastReadMillisFor(uid)
        val needsReceiptBump = allowReceipt &&
            thread.lastMessageSenderId.isNotBlank() &&
            thread.lastMessageSenderId != uid &&
            (needsClearUnread || myLastReadMs < threadUpdatedMs)
        val needsAckPriorityChirp = thread.hasActivePriorityChirpFor(uid)
        if (!needsClearUnread && !needsReceiptBump && !needsAckPriorityChirp) return Result.success(Unit)
        val path = "directThreads/${thread.id}"
        return try {
            val updates = mutableMapOf<String, Any>(
                "unreadByUserIds" to thread.unreadByUserIds.filter { it != uid },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (allowReceipt && !thread.hasAnyBlock()) {
                val updatedMap = thread.readTimestampsByUserId.toMutableMap()
                updatedMap[uid] = maxOf(System.currentTimeMillis(), threadUpdatedMs + 1L)
                updates["readTimestampsByUserId"] = updatedMap
            }
            if (needsAckPriorityChirp) {
                updates["lastPriorityChirpAcknowledgedBy"] =
                    (thread.lastPriorityChirpAcknowledgedBy + uid).distinct().take(20)
            }
            db.collection("directThreads").document(thread.id).update(updates).await()
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
                "locked" to false,
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

            val updatedPicTimestamps = if (cleanImage.isNotEmpty()) {
                reservePictureUploadSlot(uid)
            } else null
            val updatedWeeklyDiscoveryTimestamps = if (cleanImage.isNotEmpty()) {
                reserveWeeklyDiscoveryPhotoSlot(uid, myTier)
            } else null

            val userRef = db.collection("users").document(uid)
            val freeStaffBoost = boostToTop && myTier.isStaff()
            if (boostToTop && !freeStaffBoost) {
                val userSnap = userRef.get().await()
                val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(currentPoints >= 5L) {
                    "Not enough points. Boosting to the top of Discovery costs 5 points (\$5) — buy points in Profile Settings."
                }
                val txRef = db.collection("pointTransactions").document()
                val batch = db.batch()
                val userPatch = mutableMapOf<String, Any>(
                    "pointsBalance" to FieldValue.increment(-5L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (updatedPicTimestamps != null) {
                    userPatch["pictureUploadTimestamps"] = updatedPicTimestamps
                }
                if (updatedWeeklyDiscoveryTimestamps != null) {
                    userPatch["discoveryPhotoTimestamps"] = updatedWeeklyDiscoveryTimestamps
                }
                batch.update(userRef, userPatch)
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
            } else if (updatedPicTimestamps != null || updatedWeeklyDiscoveryTimestamps != null) {
                val batch = db.batch()
                batch.set(docRef, postPayload)
                val userPatch = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (updatedPicTimestamps != null) {
                    userPatch["pictureUploadTimestamps"] = updatedPicTimestamps
                }
                if (updatedWeeklyDiscoveryTimestamps != null) {
                    userPatch["discoveryPhotoTimestamps"] = updatedWeeklyDiscoveryTimestamps
                }
                batch.update(userRef, userPatch)
                batch.commit().await()
            } else {
                docRef.set(postPayload).await()
            }

            if (freeStaffBoost && myTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeName,
                    perkType = AppPerkType.POST_BOOST_24H,
                    detail = "Boosted new post to top of Discovery (24h · Free Staff Perk)"
                )
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
     * Pays 5 points ($5) atomically (or FREE for Support Staff & Admins) to boost an existing post to the top of Discovery for 24 hours.
     */
    suspend fun boostProfilePost(
        post: ProfilePost,
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "profilePosts/${post.id}"
        return try {
            require(post.authorId == uid) { "You can only boost your own posts." }
            val nowMs = System.currentTimeMillis()
            val boostUntilMs = nowMs + 24L * 60L * 60L * 1000L
            val postRef = db.collection("profilePosts").document(post.id)
            val safeName = buyerName.trim().ifBlank { "Member" }.take(40)

            if (staffTier.isStaff()) {
                postRef.update(
                    mapOf(
                        "boostedAtMillis" to nowMs,
                        "boostedUntilMillis" to boostUntilMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (staffTier == StaffTier.SUPPORT) {
                    logSupportSelfPerkUsage(
                        staffName = safeName,
                        perkType = AppPerkType.POST_BOOST_24H,
                        detail = "Boosted post to top of Discovery (24h · Free Staff Perk)"
                    )
                }
                return Result.success(Unit)
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            require(currentPoints >= 5L) {
                "Not enough points. Boosting a post to the top of Discovery costs 5 points (\$5) — buy points in Profile Settings."
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
        buyerName: String,
        playOrderId: String = "",
        playProductId: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(pointsToBuy in 2L..500L) { "Select between 2 and 500 points." }
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val profile = userSnap.toObject(UserProfile::class.java)
            if (profile != null && !profile.isAge13Plus()) {
                return Result.failure(
                    IllegalStateException("You must be 13 or older to buy points or VOX Pro membership.")
                )
            }
            val costCents = pointsToBuy * 100L
            val txRef = db.collection("pointTransactions").document()
            val safeName = buyerName.trim().ifBlank { "Member" }.take(40)

            val txPayload = mutableMapOf<String, Any>(
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
            if (playOrderId.isNotBlank()) {
                txPayload["playOrderId"] = playOrderId.trim().take(128)
            }
            if (playProductId.isNotBlank()) {
                txPayload["playProductId"] = playProductId.trim().take(64)
            }

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(pointsToBuy),
                    "totalSpentCents" to FieldValue.increment(costCents),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(txRef, txPayload)
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
                    "pointsBalance" to FieldValue.increment(1L),
                    "creatorEarnedPoints" to FieldValue.increment(1L),
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
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 200L,
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
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 1500L,
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
                    "pointsBalance" to FieldValue.increment(8L),
                    "creatorEarnedPoints" to FieldValue.increment(8L),
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
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 1500L,
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

    // --- GLOBAL APP CONFIG (LOGIN NEWS/UPDATES BANNER & TRIAL LOGIN TOGGLE) ---

    fun observeAppConfig(): Flow<AppConfig> = flow {
        emitAll(
            db.collection("appConfig").document("global")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            AppConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AppConfig()
                    } else {
                        AppConfig()
                    }
                }
                .catch { emit(AppConfig()) }
        )
    }

    suspend fun updateAppConfig(
        bannerTitle: String,
        bannerMessage: String,
        bannerEnabled: Boolean,
        trialLoginsEnabled: Boolean,
        adminName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "appConfig/global"
        return try {
            val payload = mapOf(
                "bannerTitle" to bannerTitle.trim().take(80),
                "bannerMessage" to bannerMessage.trim().take(400),
                "bannerEnabled" to bannerEnabled,
                "trialLoginsEnabled" to trialLoginsEnabled,
                "updatedByName" to adminName.trim().ifBlank { "Administrator" }.take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("appConfig").document("global").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- ADMIN ROOM TRANSFERS (SEND USER TO A DIFFERENT ROOM) ---

    fun observePendingRoomTransfer(userId: String): Flow<RoomTransfer?> = flow {
        val uid = requireUserId()
        val path = "roomTransfers/$userId"
        emitAll(
            db.collection("roomTransfers").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        val transfer = snap.toObject(
                            RoomTransfer::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (transfer != null && !transfer.consumed && transfer.targetRoomId.isNotBlank()) {
                            transfer
                        } else null
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    emit(null)
                }
        )
    }

    suspend fun sendUserToRoom(
        targetUserId: String,
        targetRoomId: String,
        targetRoomTitle: String,
        fromRoomId: String,
        adminName: String,
        reason: String
    ): Result<Unit> {
        val adminUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "roomTransfers/$targetUserId"
        return try {
            require(targetUserId.isNotBlank() && targetRoomId.isNotBlank()) {
                "Select a valid user and destination room."
            }
            val payload = mapOf(
                "userId" to targetUserId,
                "targetRoomId" to targetRoomId.trim().take(260),
                "targetRoomTitle" to targetRoomTitle.trim().ifBlank { "Live Room" }.take(60),
                "fromRoomId" to fromRoomId.trim().take(128),
                "movedByAdminId" to adminUid,
                "movedByAdminName" to adminName.trim().ifBlank { "Administrator" }.take(40),
                "reason" to reason.trim().take(200),
                "transferType" to RoomTransfer.TYPE_ROOM,
                "targetThreadId" to "",
                "targetSupportUserId" to "",
                "targetSupportName" to "",
                "consumed" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("roomTransfers").document(targetUserId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendUserToSupportPrivateChat(
        targetUser: DirectoryEntry,
        supportMember: DirectoryEntry,
        adminName: String,
        reason: String
    ): Result<String> {
        val adminUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUser.userId.isBlank() || supportMember.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Select a valid user and support member."))
        }
        if (targetUser.userId == supportMember.userId) {
            return Result.failure(IllegalArgumentException("Choose a different support member for this user."))
        }
        val threadId = canonicalPairId(targetUser.userId, supportMember.userId)
        val threadRef = db.collection("directThreads").document(threadId)
        val path = threadRef.path
        return try {
            val (first, second) = if (targetUser.userId <= supportMember.userId) {
                targetUser to supportMember
            } else {
                supportMember to targetUser
            }
            val safeAdminName = adminName.trim().ifBlank { "Administrator" }.take(40)
            val safeReason = reason.trim().take(200)
            val supportLabel = supportMember.displayName.trim().ifBlank { "Support" }.take(40)
            val userLabel = targetUser.displayName.trim().ifBlank { "Member" }.take(40)
            val handoffText = buildString {
                append("Admin $safeAdminName connected $userLabel with Support ($supportLabel).")
                if (safeReason.isNotEmpty()) {
                    append(" Topic: $safeReason")
                }
            }.take(500)
            val participants = listOf(first.userId, second.userId)
            val unreadBoth = listOf(targetUser.userId, supportMember.userId)

            val existingThread = threadRef.get().await()
            if (!existingThread.exists()) {
                val threadPayload = mapOf(
                    "participantIds" to participants,
                    "userAId" to first.userId,
                    "userAName" to first.displayName.trim().ifBlank { "Member" }.take(40),
                    "userAColorHex" to (first.avatarColorHex.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) } ?: "#0088FF"),
                    "userATier" to first.tierEnum().name,
                    "userBId" to second.userId,
                    "userBName" to second.displayName.trim().ifBlank { "Support" }.take(40),
                    "userBColorHex" to (second.avatarColorHex.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) } ?: "#1565C0"),
                    "userBTier" to second.tierEnum().name,
                    "lastMessageText" to handoffText.take(300),
                    "lastMessageSenderId" to adminUid,
                    "unreadByUserIds" to unreadBoth,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                threadRef.set(threadPayload).await()
            } else {
                threadRef.update(
                    mapOf(
                        "lastMessageText" to handoffText.take(300),
                        "lastMessageSenderId" to adminUid,
                        "unreadByUserIds" to unreadBoth,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Write SUPPORT_HANDOFF system intro message into the private thread
            val msgRef = threadRef.collection("messages").document()
            msgRef.set(
                mapOf(
                    "threadId" to threadId,
                    "participantIds" to participants,
                    "senderId" to adminUid,
                    "senderName" to safeAdminName,
                    "senderTier" to StaffTier.ADMIN.name,
                    "text" to handoffText,
                    "messageType" to DirectMessage.TYPE_SUPPORT_HANDOFF,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Send live SUPPORT_DM transfer directive so target user opens the support private chat automatically
            val transferPayload = mapOf(
                "userId" to targetUser.userId,
                "targetRoomId" to threadId.take(260),
                "targetRoomTitle" to "Support: $supportLabel".take(60),
                "fromRoomId" to "",
                "movedByAdminId" to adminUid,
                "movedByAdminName" to safeAdminName,
                "reason" to safeReason,
                "transferType" to RoomTransfer.TYPE_SUPPORT_DM,
                "targetThreadId" to threadId.take(260),
                "targetSupportUserId" to supportMember.userId.take(128),
                "targetSupportName" to supportLabel,
                "consumed" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("roomTransfers").document(targetUser.userId).set(transferPayload).await()
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun consumeRoomTransfer(userId: String): Result<Unit> {
        val path = "roomTransfers/$userId"
        return try {
            db.collection("roomTransfers").document(userId)
                .update("consumed", true)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- READ-ONLY PUBLIC PREVIEW OBSERVERS FOR 1-DAY TRIAL MODE ---

    fun observePublicRoomsForTrial(): Flow<List<ChatRoom>> = flow {
        emitAll(
            db.collection("rooms")
                .whereEqualTo("visibility", RoomVisibility.PUBLIC.name)
                .snapshots()
                .map { snap ->
                    val nowMs = System.currentTimeMillis()
                    snap.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { isRoomAliveForLobby(it, nowMs) && !it.hiddenFromLobby }
                        .sortedWith(
                            compareByDescending<ChatRoom> { it.isOfficialVoxChatRoom() }
                                .thenByDescending { it.participantCount }
                        )
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeUnlockedPostsForTrial(): Flow<List<ProfilePost>> = flow {
        emitAll(
            db.collection("profilePosts")
                .whereEqualTo("locked", false)
                .snapshots()
                .map { snap ->
                    val nowMs = System.currentTimeMillis()
                    snap.toObjects(ProfilePost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.locked }
                        .sortedWith(
                            compareByDescending<ProfilePost> { it.isCurrentlyBoosted(nowMs) }
                                .thenByDescending { if (it.isCurrentlyBoosted(nowMs)) it.boostedAtMillis else 0L }
                                .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        )
                        .take(60)
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeUserDirectoryForTrial(): Flow<List<DirectoryEntry>> = flow {
        emitAll(
            db.collection("userDirectory")
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { emit(emptyList()) }
        )
    }

    /**
     * Sends a ring + vibrate Attention Alert to an accepted friend in a 1-on-1 private chat.
     * Strictly enforces accepted friendship and max 1 alert every 5 minutes.
     */
    suspend fun sendFriendAttentionAlert(
        thread: DirectThread,
        senderName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.isGroup) { "Attention alerts can only be sent in 1-on-1 friend chats." }
            require(!thread.hasAnyBlock()) { "You cannot alert a blocked contact." }
            require(!thread.isUserKicked(uid)) { "You can no longer message or alert this user." }

            val friendSnap = db.collection("friendships").document(thread.id).get().await()
            val status = friendSnap.getString("status").orEmpty()
            require(friendSnap.exists() && status == FriendshipStatus.ACCEPTED.name) {
                "You can only send attention alerts to users on your friends list."
            }

            val nowMs = System.currentTimeMillis()
            val fiveMinutesMs = 5 * 60 * 1000L
            if (thread.lastAlertSenderId == uid && thread.lastAlertAtMillis > 0L) {
                val elapsed = nowMs - thread.lastAlertAtMillis
                if (elapsed < fiveMinutesMs) {
                    val remainSec = ((fiveMinutesMs - elapsed + 999L) / 1000L).coerceAtLeast(1L)
                    val mins = remainSec / 60L
                    val secs = remainSec % 60L
                    val waitLabel = if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
                    return Result.failure(
                        IllegalStateException("You can only send 1 alert every 5 minutes. Try again in $waitLabel.")
                    )
                }
            }

            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "lastAlertSenderId" to uid,
                    "lastAlertSenderName" to senderName.trim().ifBlank { "Friend" }.take(40),
                    "lastAlertAtMillis" to nowMs,
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

    /**
     * Toggles Push-to-Talk in `directThreads/{threadId}.pttEnabledUserIds`.
     * Regular users toggle only their own UID; when an Admin enables PTT (`autoEnableAllParticipants = true`),
     * PTT is automatically enabled for all participants in the chat right away.
     */
    suspend fun setDirectThreadPttEnabled(
        thread: DirectThread,
        enabled: Boolean,
        autoEnableAllParticipants: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock()) { "Push-to-Talk is unavailable in a blocked chat." }
            require(!thread.isUserKicked(uid)) { "You can no longer use Push-to-Talk in this chat." }
            if (enabled && !autoEnableAllParticipants && thread.requiresAdminPttSelection()) {
                require(thread.isPttAllowedInGroup(uid, isAppAdmin = false)) {
                    "Only members selected by the Group DM Administrator can enable Push-to-Talk when a group has more than 15 users."
                }
            }
            val currentList = thread.pttEnabledUserIds
            val updatedList = when {
                enabled && autoEnableAllParticipants -> {
                    val allParticipants = thread.participantIds.filter { it.isNotBlank() }.distinct()
                    if (allParticipants.isNotEmpty() && currentList.containsAll(allParticipants)) {
                        return Result.success(Unit)
                    }
                    allParticipants.ifEmpty { listOf(uid) }
                }
                !enabled && autoEnableAllParticipants -> {
                    if (currentList.isEmpty()) return Result.success(Unit)
                    emptyList()
                }
                enabled -> {
                    if (currentList.contains(uid)) return Result.success(Unit)
                    currentList + uid
                }
                else -> {
                    if (!currentList.contains(uid)) return Result.success(Unit)
                    currentList.filter { it != uid }
                }
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "pttEnabledUserIds" to updatedList,
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

    /**
     * Toggles PTT Voice-to-Text for the current user in `directThreads/{threadId}.pttVoiceToTextUserIds`.
     * When enabled by any member, PTT transmissions in this DM also send a voice-to-text message
     * so nobody misses important Push-to-Talk audios.
     */
    suspend fun setDirectThreadPttVoiceToText(
        thread: DirectThread,
        enabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock()) { "Unavailable in a blocked chat." }
            require(!thread.isUserKicked(uid)) { "You can no longer change settings in this chat." }
            val currentList = thread.pttVoiceToTextUserIds
            val updatedList = if (enabled) {
                if (currentList.contains(uid)) return Result.success(Unit)
                currentList + uid
            } else {
                if (!currentList.contains(uid)) return Result.success(Unit)
                currentList.filter { it != uid }
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "pttVoiceToTextUserIds" to updatedList,
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

    /**
     * Toggles Listen-Only Live Audio for the current user in `directThreads/{threadId}.pttListenOnlyUserIds`.
     * Allows users (including sub-accounts or users whose PTT broadcast is restricted by an admin)
     * to opt in to hearing live Push-to-Talk audio even if they cannot broadcast back.
     */
    suspend fun setDirectThreadPttListenOnly(
        thread: DirectThread,
        enabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock()) { "Unavailable in a blocked chat." }
            require(!thread.isUserKicked(uid)) { "You can no longer change settings in this chat." }
            val currentList = thread.pttListenOnlyUserIds
            val updatedList = if (enabled) {
                if (currentList.contains(uid)) return Result.success(Unit)
                currentList + uid
            } else {
                if (!currentList.contains(uid)) return Result.success(Unit)
                currentList.filter { it != uid }
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "pttListenOnlyUserIds" to updatedList,
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

    /**
     * Toggles Do Not Disturb (PTT → Voice-to-Text) for the current user in `directThreads/{threadId}.pttDndUserIds`.
     * While enabled, incoming PTT voice audio is silenced on this device and senders automatically
     * deliver incoming PTT as voice-to-text messages so nothing is missed.
     */
    suspend fun setDirectThreadPttDnd(
        thread: DirectThread,
        enabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock()) { "Unavailable in a blocked chat." }
            require(!thread.isUserKicked(uid)) { "You can no longer change settings in this chat." }
            val currentList = thread.pttDndUserIds
            val updatedList = if (enabled) {
                if (currentList.contains(uid)) return Result.success(Unit)
                currentList + uid
            } else {
                if (!currentList.contains(uid)) return Result.success(Unit)
                currentList.filter { it != uid }
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "pttDndUserIds" to updatedList,
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

    suspend fun setDirectThreadDisappearingDuration(
        thread: DirectThread,
        durationSec: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(durationSec in DirectThread.DISAPPEARING_OPTIONS_SEC) {
                "Unsupported disappearing message timer."
            }
            require(!thread.hasAnyBlock()) { "Unavailable in a blocked chat." }
            require(!thread.isUserKicked(uid)) { "You can no longer change settings in this chat." }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "disappearingDurationSec" to durationSec,
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

    suspend fun setDirectThreadReadReceiptsEnabled(
        thread: DirectThread,
        enabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            val currentDisabled = thread.readReceiptsDisabledByUserIds
            val updatedDisabled = if (enabled) {
                if (!currentDisabled.contains(uid)) return Result.success(Unit)
                currentDisabled.filter { it != uid }
            } else {
                if (currentDisabled.contains(uid)) return Result.success(Unit)
                currentDisabled + uid
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "readReceiptsDisabledByUserIds" to updatedDisabled,
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

    suspend fun deleteDirectMessage(
        threadId: String,
        messageId: String
    ): Result<Unit> {
        if (auth.currentUser?.uid == null) return Result.success(Unit)
        val path = "directThreads/$threadId/messages/$messageId"
        return try {
            db.collection("directThreads").document(threadId)
                .collection("messages").document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silent cleanup for expired messages if already deleted
            Result.failure(e)
        }
    }

    suspend fun syncAcceptedFriendIdsToDirectory(friendIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "userDirectory/$uid"
        return try {
            val cleanIds = friendIds.filter { it.isNotBlank() && it != uid }.distinct().take(200)
            db.collection("userDirectory").document(uid).update(
                mapOf(
                    "acceptedFriendIds" to cleanIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal if directory doc is still being created on first launch
            Result.failure(e)
        }
    }

    // --- SUPPORT TICKETS (DISCOVERY SUPPORT TAB) ---

    fun observeSupportTickets(userId: String, isStaff: Boolean): Flow<List<SupportTicket>> = flow {
        val uid = requireUserId()
        val path = "supportTickets"
        val query = if (isStaff) {
            db.collection("supportTickets").limit(100)
        } else {
            db.collection("supportTickets")
                .whereEqualTo("requesterId", userId)
                .limit(50)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SupportTicket::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
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

    suspend fun createSupportTicket(
        requesterName: String,
        category: SupportTicketCategory,
        subject: String,
        details: String,
        assignedStaffId: String = "",
        assignedStaffName: String = "",
        requesterTier: StaffTier = StaffTier.USER
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanSubject = subject.trim().take(100)
        val cleanMessage = details.trim().take(600)
        if (cleanSubject.length < 2) {
            return Result.failure(IllegalArgumentException("Subject must be at least 2 characters."))
        }
        if (cleanMessage.length < 4) {
            return Result.failure(IllegalArgumentException("Please describe your issue (at least 4 characters)."))
        }
        val docRef = db.collection("supportTickets").document()
        val path = docRef.path
        return try {
            val payload = mapOf(
                "requesterId" to uid,
                "requesterName" to requesterName.trim().ifBlank { "Member" }.take(40),
                "requesterTier" to requesterTier.name,
                "category" to category.toFirestoreCategory(),
                "subject" to cleanSubject,
                "message" to cleanMessage,
                "status" to SupportTicketStatus.OPEN.name,
                "assignedStaffId" to assignedStaffId.trim().take(128),
                "assignedStaffName" to assignedStaffName.trim().take(40),
                "staffResponse" to "",
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

    /**
     * Allows a suspended user to submit ONE review message per suspension.
     * Uses a deterministic document ID so a second submission is strictly rejected.
     */
    suspend fun submitSuspensionReviewAppeal(
        requesterName: String,
        requesterTier: StaffTier,
        suspensionKey: String,
        suspensionReason: String,
        reviewMessage: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanMsg = reviewMessage.trim().take(600)
        if (cleanMsg.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter at least 4 characters for your review message."))
        }
        val safeKey = suspensionKey.filter { it.isLetterOrDigit() || it == '_' }.take(32).ifBlank { "active" }
        val docId = "susp_appeal_${uid}_$safeKey".take(120)
        val docRef = db.collection("supportTickets").document(docId)
        val path = docRef.path
        return try {
            if (docRef.get().await().exists()) {
                return Result.failure(
                    IllegalStateException("You have already submitted a review message for this suspension. Only one message can be sent.")
                )
            }
            val safeReasonSnippet = suspensionReason.trim().take(60)
            val subject = if (safeReasonSnippet.isNotBlank()) {
                "Suspension Review: $safeReasonSnippet".take(100)
            } else {
                "Suspension Review Request"
            }
            val payload = mapOf(
                "requesterId" to uid,
                "requesterName" to requesterName.trim().ifBlank { "Member" }.take(40),
                "requesterTier" to requesterTier.name,
                "category" to "ACCOUNT",
                "subject" to subject,
                "message" to cleanMsg,
                "status" to SupportTicketStatus.OPEN.name,
                "assignedStaffId" to "",
                "assignedStaffName" to "",
                "staffResponse" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToSupportTicket(
        ticketId: String,
        status: SupportTicketStatus,
        staffResponse: String,
        staffName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "supportTickets/$ticketId"
        return try {
            db.collection("supportTickets").document(ticketId).update(
                mapOf(
                    "status" to status.name,
                    "staffResponse" to staffResponse.trim().take(500),
                    "assignedStaffId" to uid,
                    "assignedStaffName" to staffName.trim().ifBlank { "Support" }.take(40),
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

    suspend fun closeMySupportTicket(ticketId: String): Result<Unit> {
        val path = "supportTickets/$ticketId"
        return try {
            db.collection("supportTickets").document(ticketId).update(
                mapOf(
                    "status" to SupportTicketStatus.RESOLVED.name,
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

    // --- PROFILE BIO QUICK UPDATE & FOLLOWERS / FOLLOWING ---

    suspend fun updateUserBio(
        bio: String,
        currentProfile: UserProfile,
        currentStaffTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val safeBio = bio.trim().take(200)
            db.collection("users").document(uid).update(
                mapOf(
                    "bio" to safeBio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            syncUserDirectoryEntry(
                userId = uid,
                displayName = currentProfile.displayName.ifBlank { "Member" },
                handle = currentProfile.handle.ifBlank { "member" },
                bio = safeBio,
                avatarColorHex = currentProfile.avatarColorHex.ifBlank { "#0088FF" },
                photoBase64 = currentProfile.photoBase64,
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

    fun observeFollowers(userId: String): Flow<List<UserFollow>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection("userFollows").whereEqualTo("followingId", userId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(UserFollow::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    handleFirestoreError(root, OperationType.LIST, "userFollows")
                    throw error
                }
        )
    }

    fun observeFollowing(userId: String): Flow<List<UserFollow>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val query = db.collection("userFollows").whereEqualTo("followerId", userId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(UserFollow::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    handleFirestoreError(root, OperationType.LIST, "userFollows")
                    throw error
                }
        )
    }

    suspend fun followUser(targetUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank() || targetUserId == uid) {
            return Result.failure(IllegalArgumentException("Cannot follow this account."))
        }
        val followId = "${uid}__${targetUserId}"
        val path = "userFollows/$followId"
        return try {
            reserveAddPeopleSlots(uid = uid, count = 1, bypassForAdmin = false)
            db.collection("userFollows").document(followId).set(
                mapOf(
                    "followerId" to uid,
                    "followingId" to targetUserId,
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

    suspend fun unfollowUser(targetUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid user."))
        }
        val followId = "${uid}__${targetUserId}"
        val path = "userFollows/$followId"
        return try {
            db.collection("userFollows").document(followId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeAllUserFollows(): Flow<List<UserFollow>> {
        val query = db.collection("userFollows")
        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserFollow::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "userFollows")
                throw error
            }
    }

    // --- ADMIN APP-WIDE BROADCAST MESSAGES TO ALL USERS ---

    fun observeAppBroadcasts(): Flow<List<AppBroadcast>> {
        val query = db.collection("appBroadcasts")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AppBroadcast::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                handleFirestoreError(root, OperationType.LIST, "appBroadcasts")
                throw error
            }
    }

    suspend fun sendAppBroadcastToAllUsers(
        senderName: String,
        title: String,
        message: String,
        severity: AlertSeverity
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTitle = title.trim().take(80)
        val cleanMessage = message.trim().take(500)
        if (cleanTitle.isEmpty() || cleanMessage.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter both a title and a message."))
        }
        val safeSenderName = senderName.trim().ifBlank { "App Admin" }.take(40)
        val docRef = db.collection("appBroadcasts").document()
        val path = "appBroadcasts/${docRef.id}"
        return try {
            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "message" to cleanMessage,
                    "severity" to severity.name,
                    "senderId" to uid,
                    "senderName" to safeSenderName,
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

    /**
     * App Admin mass private message to EVERY user in VOX.
     * Delivered into each user's Chats inbox as an official read-only "VOX" private message
     * (`directThreads/vox_official_<uid>`, `voxOfficial = true`) that users cannot reply to.
     * Uses two batch waves (threads first, then messages) so Firestore security rules always see
     * the thread document before each message is written.
     */
    suspend fun sendMassVoxPrivateMessageToAllUsers(
        title: String,
        message: String,
        isUrgentAlert: Boolean
    ): Result<Int> {
        val adminUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTitle = title.trim().take(80)
        val cleanBody = message.trim().take(400)
        if (cleanBody.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a message to send."))
        }
        val prefix = if (isUrgentAlert) "🚨 VOX ALERT" else "📢 VOX UPDATE"
        val formattedFull = if (cleanTitle.isNotEmpty()) {
            "$prefix • $cleanTitle\n$cleanBody"
        } else {
            "$prefix\n$cleanBody"
        }.take(500)
        val previewText = if (cleanTitle.isNotEmpty()) {
            "$prefix: $cleanTitle — $cleanBody"
        } else {
            "$prefix: $cleanBody"
        }.take(300)

        return try {
            data class RecipientInfo(
                val uid: String,
                val name: String,
                val colorHex: String,
                val tier: String
            )

            val recipientsByUid = LinkedHashMap<String, RecipientInfo>()

            val usersSnap = runCatching { db.collection("users").get().await() }.getOrNull()
            usersSnap?.documents?.forEach { doc ->
                val uid = doc.id.trim()
                if (uid.isNotBlank() && uid != DirectThread.VOX_OFFICIAL_SENDER_ID) {
                    val name = doc.getString("displayName")?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
                    val color = doc.getString("avatarColorHex")?.trim()?.takeIf {
                        it.matches(Regex("^#[0-9A-Fa-f]{6}$"))
                    } ?: "#0088FF"
                    recipientsByUid[uid] = RecipientInfo(
                        uid = uid,
                        name = name.take(40),
                        colorHex = color,
                        tier = StaffTier.USER.name
                    )
                }
            }

            val dirSnap = runCatching { db.collection("userDirectory").get().await() }.getOrNull()
            dirSnap?.documents?.forEach { doc ->
                val uid = doc.id.trim()
                if (uid.isNotBlank() && uid != DirectThread.VOX_OFFICIAL_SENDER_ID && !recipientsByUid.containsKey(uid)) {
                    val name = doc.getString("displayName")?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
                    val color = doc.getString("avatarColorHex")?.trim()?.takeIf {
                        it.matches(Regex("^#[0-9A-Fa-f]{6}$"))
                    } ?: "#0088FF"
                    val rawTier = doc.getString("staffTier")?.trim().orEmpty()
                    val safeTier = if (rawTier in setOf("USER", "SUPPORT", "ADMIN")) rawTier else StaffTier.USER.name
                    recipientsByUid[uid] = RecipientInfo(
                        uid = uid,
                        name = name.take(40),
                        colorHex = color,
                        tier = safeTier
                    )
                }
            }

            if (recipientsByUid.isEmpty()) {
                return Result.failure(IllegalStateException("No users found to receive the VOX private message."))
            }

            // Identify which vox_official_<uid> threads already exist so we use update vs create cleanly
            val existingOfficialThreadIds = runCatching {
                db.collection("directThreads")
                    .whereEqualTo("voxOfficial", true)
                    .get()
                    .await()
                    .documents
                    .map { it.id }
                    .toSet()
            }.getOrDefault(emptySet())

            val recipients = recipientsByUid.values.toList()
            val batchSize = 180

            // Wave 1: Create or update every user's official VOX private thread
            recipients.chunked(batchSize).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { r ->
                    val threadId = DirectThread.voxOfficialThreadId(r.uid)
                    val threadRef = db.collection("directThreads").document(threadId)
                    if (existingOfficialThreadIds.contains(threadId)) {
                        batch.update(
                            threadRef,
                            mapOf(
                                "lastMessageText" to previewText,
                                "lastMessageSenderId" to DirectThread.VOX_OFFICIAL_SENDER_ID,
                                "unreadByUserIds" to listOf(r.uid),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        val participants = listOf(DirectThread.VOX_OFFICIAL_SENDER_ID, r.uid)
                        batch.set(
                            threadRef,
                            mapOf(
                                "participantIds" to participants,
                                "isGroup" to false,
                                "voxOfficial" to true,
                                "userAId" to DirectThread.VOX_OFFICIAL_SENDER_ID,
                                "userAName" to "VOX",
                                "userAColorHex" to "#0088FF",
                                "userATier" to StaffTier.ADMIN.name,
                                "userBId" to r.uid,
                                "userBName" to r.name,
                                "userBColorHex" to r.colorHex,
                                "userBTier" to r.tier,
                                "lastMessageText" to previewText,
                                "lastMessageSenderId" to DirectThread.VOX_OFFICIAL_SENDER_ID,
                                "unreadByUserIds" to listOf(r.uid),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                batch.commit().await()
            }

            // Wave 2: Append the official VOX message into every user's thread
            recipients.chunked(batchSize).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { r ->
                    val threadId = DirectThread.voxOfficialThreadId(r.uid)
                    val participants = listOf(DirectThread.VOX_OFFICIAL_SENDER_ID, r.uid)
                    val msgRef = db.collection("directThreads")
                        .document(threadId)
                        .collection("messages")
                        .document()
                    batch.set(
                        msgRef,
                        mapOf(
                            "threadId" to threadId,
                            "participantIds" to participants,
                            "senderId" to adminUid,
                            "senderName" to "VOX",
                            "senderTier" to StaffTier.ADMIN.name,
                            "text" to formattedFull,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(recipients.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "directThreads/vox_official_mass")
            Result.failure(e)
        }
    }

    suspend fun deleteAppBroadcast(broadcastId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (broadcastId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid broadcast ID."))
        }
        val path = "appBroadcasts/$broadcastId"
        return try {
            db.collection("appBroadcasts").document(broadcastId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PINNING & UNPINNING MESSAGES IN PRIVATE CHATS ---

    suspend fun pinDirectMessage(
        threadId: String,
        message: DirectMessage,
        pinnedByName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (threadId.isBlank() || message.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid message to pin."))
        }
        val preview = when {
            message.text.isNotBlank() -> message.text.trim()
            message.hasVoiceNote() -> "Voice note (${message.audioDurationSec}s)"
            message.imageBase64.isNotBlank() -> "Photo message"
            else -> "Pinned message"
        }.take(300)
        val safeSenderName = if (message.hideSenderName) {
            "Hidden Member"
        } else {
            message.senderName.trim().ifBlank { "Member" }.take(40)
        }
        val safePinnerName = pinnedByName.trim().ifBlank { "Member" }.take(40)
        val path = "directThreads/$threadId"
        return try {
            db.collection("directThreads").document(threadId).update(
                mapOf(
                    "pinnedMessageId" to message.id.take(128),
                    "pinnedMessageText" to preview,
                    "pinnedMessageSenderName" to safeSenderName,
                    "pinnedByUserId" to uid,
                    "pinnedByName" to safePinnerName,
                    "pinnedAtMillis" to System.currentTimeMillis(),
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

    suspend fun unpinDirectMessage(threadId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (threadId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid thread."))
        }
        val path = "directThreads/$threadId"
        return try {
            db.collection("directThreads").document(threadId).update(
                mapOf(
                    "pinnedMessageId" to "",
                    "pinnedMessageText" to "",
                    "pinnedMessageSenderName" to "",
                    "pinnedByUserId" to "",
                    "pinnedByName" to "",
                    "pinnedAtMillis" to 0L,
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

    // --- MEMBERSHIP TIERS (VOX PRO $9.99/mo or 10 pts, VIP PRO $29.99/mo, ELITE PRO $59.99/mo) ---

    suspend fun activateProMembership(
        buyerName: String,
        staffTier: StaffTier,
        paidWithPoints: Boolean = false,
        playOrderId: String = "",
        playProductId: String = "",
        targetTier: ProTier = ProTier.PRO
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val effectiveTier = if (targetTier == ProTier.FREE) ProTier.PRO else targetTier
            require(!paidWithPoints || effectiveTier == ProTier.PRO) {
                "${effectiveTier.label} (${effectiveTier.priceLabel}) requires a monthly subscription."
            }
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val existingProfile = userSnap.toObject(UserProfile::class.java)
            if (!staffTier.isStaff() && existingProfile != null && !existingProfile.isAge13Plus()) {
                return Result.failure(
                    IllegalStateException("You must be 13 or older to buy VOX membership or points.")
                )
            }
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            if (paidWithPoints && currentPoints < UserProfile.PRO_REDEEM_POINTS) {
                return Result.failure(
                    IllegalStateException(
                        "You need ${UserProfile.PRO_REDEEM_POINTS} points to activate VOX Pro with points (or subscribe via Google Play for \$9.99/mo)."
                    )
                )
            }
            val nowMs = System.currentTimeMillis()
            val existingExpiry = (userSnap.get("proExpiresAtMillis") as? Number)?.toLong() ?: 0L
            val baseMs = maxOf(nowMs, existingExpiry)
            val maxAllowedExpiry = nowMs + 31L * 24L * 60L * 60L * 1000L
            val newExpiryMs = minOf(baseMs + UserProfile.PRO_DURATION_MILLIS, maxAllowedExpiry)

            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val dirRef = db.collection("userDirectory").document(uid)
            val existingDir = dirRef.get().await()
            val txRef = db.collection("pointTransactions").document()

            val userUpdates = mutableMapOf<String, Any>(
                "proMember" to true,
                "proTier" to effectiveTier.name,
                "proExpiresAtMillis" to newExpiryMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (paidWithPoints) {
                userUpdates["pointsBalance"] = FieldValue.increment(-UserProfile.PRO_REDEEM_POINTS)
            } else {
                userUpdates["totalSpentCents"] = FieldValue.increment(effectiveTier.priceCents)
            }

            val txData = mutableMapOf<String, Any>(
                "type" to effectiveTier.transactionType().name,
                "buyerId" to uid,
                "buyerName" to safeBuyerName,
                "creatorId" to "",
                "creatorName" to "",
                "postId" to "",
                "pointsDelta" to if (paidWithPoints) -UserProfile.PRO_REDEEM_POINTS else 0L,
                "grossCents" to effectiveTier.priceCents,
                "creatorShareCents" to 0L,
                "platformShareCents" to effectiveTier.priceCents,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (playOrderId.isNotBlank()) txData["playOrderId"] = playOrderId.take(128)
            if (playProductId.isNotBlank()) txData["playProductId"] = playProductId.take(64)

            val batch = db.batch()
            batch.update(userRef, userUpdates)
            if (existingDir.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "proMember" to true,
                        "proTier" to effectiveTier.name,
                        "proExpiresAtMillis" to newExpiryMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val safeHandle = (userSnap.getString("handle") ?: "vox_user").take(30)
                val safeColor = userSnap.getString("avatarColorHex") ?: "#1E6BFF"
                batch.set(
                    dirRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeBuyerName,
                        "handle" to safeHandle,
                        "avatarColorHex" to safeColor,
                        "staffTier" to staffTier.name,
                        "proMember" to true,
                        "proTier" to effectiveTier.name,
                        "proExpiresAtMillis" to newExpiryMs,
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(txRef, txData)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- REELS (PRO-ONLY SHORT VIDEOS <= 30s, $5 BOOST TO TOP, LIKE/COMMENT/SHARE) ---

    fun observeReels(): Flow<UiState<List<Reel>>> = callbackFlow {
        trySend(UiState.Loading)
        val listener = db.collection("reels")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "reels")
                    trySend(UiState.Error(error.message ?: "Unable to load Reels"))
                    return@addSnapshotListener
                }
                val nowMs = System.currentTimeMillis()
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    runCatching { doc.toObject(Reel::class.java) }.getOrNull()
                }.sortedWith(
                    compareByDescending<Reel> { it.isCurrentlyBoosted(nowMs) }
                        .thenByDescending { if (it.isCurrentlyBoosted(nowMs)) it.boostedAtMillis else 0L }
                        .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                )
                trySend(UiState.Success(items))
            }
        awaitClose { listener.remove() }
    }

    suspend fun createReelWithChunks(
        authorName: String,
        authorHandle: String,
        authorColorHex: String,
        authorPhotoBase64: String,
        authorTier: StaffTier,
        caption: String,
        durationSec: Long,
        thumbnailBase64: String,
        chunksBase64: List<String>,
        boostToTop: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (durationSec !in 1L..Reel.MAX_DURATION_SECONDS.toLong()) {
            return Result.failure(IllegalArgumentException("Reels must be between 1 and 30 seconds long."))
        }
        if (chunksBase64.isEmpty() || chunksBase64.size > 16) {
            return Result.failure(IllegalArgumentException("Invalid video payload size."))
        }
        val reelRef = db.collection("reels").document()
        val path = "reels/${reelRef.id}"
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val isPro = authorTier.isStaff() || (
                userSnap.getBoolean("proMember") == true &&
                    ((userSnap.get("proExpiresAtMillis") as? Number)?.toLong() ?: 0L) > System.currentTimeMillis()
                )
            if (!isPro) {
                return Result.failure(
                    IllegalStateException("Pro Membership (\$9.99/mo) is required to upload Reels.")
                )
            }
            val freeStaffBoost = boostToTop && authorTier.isStaff()
            if (boostToTop && !freeStaffBoost) {
                val pts = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                if (pts < Reel.BOOST_PRICE_POINTS) {
                    return Result.failure(
                        IllegalStateException("You need 5 points (\$5) to boost this Reel to the top.")
                    )
                }
            }

            val safeName = authorName.trim().ifBlank { "Member" }.take(40)
            val safeHandle = authorHandle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(authorColorHex)) authorColorHex else "#1E6BFF"
            val safePhoto = if (authorPhotoBase64.length <= 140_000) authorPhotoBase64 else ""
            val safeThumb = if (thumbnailBase64.length <= 140_000) thumbnailBase64 else ""
            val nowMs = System.currentTimeMillis()

            val reelDoc = mutableMapOf<String, Any>(
                "authorId" to uid,
                "authorName" to safeName,
                "authorHandle" to safeHandle,
                "authorColorHex" to safeColor,
                "authorPhotoBase64" to safePhoto,
                "authorTier" to authorTier.name,
                "caption" to caption.trim().take(300),
                "thumbnailBase64" to safeThumb,
                "durationSec" to durationSec,
                "chunkCount" to chunksBase64.size.toLong(),
                "mimeType" to "video/mp4",
                "boostedAtMillis" to if (boostToTop) nowMs else 0L,
                "boostedUntilMillis" to if (boostToTop) nowMs + 86_400_000L else 0L,
                "likedByUserIds" to emptyList<String>(),
                "commentCount" to 0L,
                "shareCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(reelRef, reelDoc)
            if (boostToTop && !freeStaffBoost) {
                batch.update(
                    userRef,
                    mapOf(
                        "pointsBalance" to FieldValue.increment(-Reel.BOOST_PRICE_POINTS),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.BOOST_REEL.name,
                        "buyerId" to uid,
                        "buyerName" to safeName,
                        "creatorId" to "",
                        "creatorName" to "",
                        "postId" to reelRef.id,
                        "pointsDelta" to -Reel.BOOST_PRICE_POINTS,
                        "grossCents" to 500L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 500L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            if (freeStaffBoost && authorTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeName,
                    perkType = AppPerkType.REEL_BOOST_24H,
                    detail = "Boosted new Reel to top of Reels (24h · Free Staff Perk)"
                )
            }

            chunksBase64.forEachIndexed { index, dataBase64 ->
                reelRef.collection("chunks").document("chunk_$index").set(
                    mapOf(
                        "reelId" to reelRef.id,
                        "authorId" to uid,
                        "chunkIndex" to index.toLong(),
                        "dataBase64" to dataBase64,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(reelRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun loadReelChunks(reelId: String): Result<List<ReelChunk>> {
        val path = "reels/$reelId/chunks"
        return try {
            val snap = db.collection("reels")
                .document(reelId)
                .collection("chunks")
                .orderBy("chunkIndex", Query.Direction.ASCENDING)
                .get()
                .await()
            val chunks = snap.documents.mapNotNull { doc ->
                runCatching { doc.toObject(ReelChunk::class.java) }.getOrNull()
            }
            Result.success(chunks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun boostReelToTop(
        reel: Reel,
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reels/${reel.id}"
        return try {
            if (reel.authorId != uid) {
                return Result.failure(IllegalStateException("Only the Reel creator can boost this Reel."))
            }
            val nowMs = System.currentTimeMillis()
            val reelRef = db.collection("reels").document(reel.id)
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)

            if (staffTier.isStaff()) {
                reelRef.update(
                    mapOf(
                        "boostedAtMillis" to nowMs,
                        "boostedUntilMillis" to nowMs + 86_400_000L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (staffTier == StaffTier.SUPPORT) {
                    logSupportSelfPerkUsage(
                        staffName = safeBuyerName,
                        perkType = AppPerkType.REEL_BOOST_24H,
                        detail = "Boosted Reel to top of Reels (24h · Free Staff Perk)"
                    )
                }
                return Result.success(Unit)
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            if (currentPoints < Reel.BOOST_PRICE_POINTS) {
                return Result.failure(
                    IllegalStateException("You need 5 points (\$5) to push your Reel to the top.")
                )
            }
            val txRef = db.collection("pointTransactions").document()

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-Reel.BOOST_PRICE_POINTS),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                reelRef,
                mapOf(
                    "boostedAtMillis" to nowMs,
                    "boostedUntilMillis" to nowMs + 86_400_000L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.BOOST_REEL.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to reel.id,
                    "pointsDelta" to -Reel.BOOST_PRICE_POINTS,
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

    suspend fun toggleLikeReel(reel: Reel): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reels/${reel.id}"
        return try {
            val updated = if (reel.likedByUserIds.contains(uid)) {
                reel.likedByUserIds.filterNot { it == uid }
            } else {
                (reel.likedByUserIds + uid).distinct().take(500)
            }
            db.collection("reels").document(reel.id).update(
                mapOf(
                    "likedByUserIds" to updated,
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

    suspend fun deleteReel(reelId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reels/$reelId"
        return try {
            db.collection("reels").document(reelId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeReelComments(reelId: String): Flow<List<ReelComment>> = callbackFlow {
        val listener = db.collection("reels")
            .document(reelId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "reels/$reelId/comments")
                    return@addSnapshotListener
                }
                val comments = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    runCatching { doc.toObject(ReelComment::class.java) }.getOrNull()
                }
                trySend(comments)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addReelComment(
        reelId: String,
        authorName: String,
        authorHandle: String,
        authorColorHex: String,
        authorTier: StaffTier,
        text: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        }
        val path = "reels/$reelId/comments"
        return try {
            val reelRef = db.collection("reels").document(reelId)
            val commentRef = reelRef.collection("comments").document()
            val safeName = authorName.trim().ifBlank { "Member" }.take(40)
            val safeHandle = authorHandle.trim().replace(" ", "_").ifBlank { "vox_user" }.take(30)
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(authorColorHex)) authorColorHex else "#1E6BFF"

            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "reelId" to reelId,
                    "authorId" to uid,
                    "authorName" to safeName,
                    "authorHandle" to safeHandle,
                    "authorColorHex" to safeColor,
                    "authorTier" to authorTier.name,
                    "text" to trimmed.take(400),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                reelRef,
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

    suspend fun deleteReelComment(reelId: String, commentId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reels/$reelId/comments/$commentId"
        return try {
            val reelRef = db.collection("reels").document(reelId)
            val commentRef = reelRef.collection("comments").document(commentId)
            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                reelRef,
                mapOf(
                    "commentCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementReelShareCount(reelId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reels/$reelId"
        return try {
            db.collection("reels").document(reelId).update(
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

    // --- PROMOTE PROFILE TO TOP OF DISCOVERY (5 PTS / 24H) ---

    suspend fun boostMyProfileToTop(
        buyerName: String,
        staffTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val freeStaff = staffTier.isStaff()
            if (!freeStaff) {
                val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(currentPoints >= 5L) {
                    "You need 5 points to promote your profile to the top of Discovery."
                }
            }
            val nowMs = System.currentTimeMillis()
            val boostUntilMs = nowMs + 24L * 60L * 60L * 1000L
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val dirRef = db.collection("userDirectory").document(uid)
            val existingDir = dirRef.get().await()

            val batch = db.batch()
            val userPatch = mutableMapOf<String, Any>(
                "profileBoostedUntilMillis" to boostUntilMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!freeStaff) {
                userPatch["pointsBalance"] = FieldValue.increment(-5L)
            }
            batch.update(userRef, userPatch)
            if (existingDir.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "profileBoostedUntilMillis" to boostUntilMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val safeHandle = (userSnap.getString("handle") ?: "vox_user").take(30)
                val safeColor = userSnap.getString("avatarColorHex") ?: "#1E6BFF"
                batch.set(
                    dirRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeBuyerName,
                        "handle" to safeHandle,
                        "avatarColorHex" to safeColor,
                        "staffTier" to staffTier.name,
                        "profileBoostedUntilMillis" to boostUntilMs,
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!freeStaff) {
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.BOOST_PROFILE.name,
                        "buyerId" to uid,
                        "buyerName" to safeBuyerName,
                        "creatorId" to "",
                        "creatorName" to "",
                        "postId" to "",
                        "pointsDelta" to -5L,
                        "grossCents" to 500L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 500L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            if (staffTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeBuyerName,
                    perkType = AppPerkType.PROFILE_BOOST_24H,
                    detail = "Promoted own profile to top of Discovery (24h · Free Staff Perk)"
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

    // --- CUSTOM PTT THEMES & WALKIE CHIRPS SHOP ---

    suspend fun selectPttTheme(
        theme: PttThemeStyle,
        staffName: String = "",
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "selectedPttTheme" to theme.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (staffTier == StaffTier.SUPPORT && theme.pricePoints > 0L) {
                logSupportSelfPerkUsage(
                    staffName = staffName.ifBlank { "Support" },
                    perkType = AppPerkType.PTT_THEME,
                    detail = "Equipped ${theme.label} PTT theme (Free Staff Perk)"
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

    suspend fun purchasePttTheme(
        theme: PttThemeStyle,
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (theme.pricePoints <= 0L || staffTier.isStaff()) {
            return selectPttTheme(theme, staffName = buyerName, staffTier = staffTier)
        }
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            val existingOwned = (userSnap.get("ownedPttThemes") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            if (existingOwned.contains(theme.id)) {
                return selectPttTheme(theme)
            }
            require(currentPoints >= theme.pricePoints) {
                "You need ${theme.pricePoints} points to unlock ${theme.label}."
            }
            val updatedOwned = (existingOwned + theme.id).distinct().take(20)
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val grossCents = theme.pricePoints * 100L
            val txRef = db.collection("pointTransactions").document()

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-theme.pricePoints),
                    "ownedPttThemes" to updatedOwned,
                    "selectedPttTheme" to theme.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.BUY_PTT_ITEM.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to "THEME_${theme.id}",
                    "pointsDelta" to -theme.pricePoints,
                    "grossCents" to grossCents,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to grossCents,
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

    suspend fun selectPttChirp(
        chirp: PttChirpStyle,
        staffName: String = "",
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "selectedPttChirp" to chirp.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (staffTier == StaffTier.SUPPORT && chirp.pricePoints > 0L) {
                logSupportSelfPerkUsage(
                    staffName = staffName.ifBlank { "Support" },
                    perkType = AppPerkType.PTT_CHIRP,
                    detail = "Equipped ${chirp.label} walkie chirp (Free Staff Perk)"
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

    suspend fun purchasePttChirp(
        chirp: PttChirpStyle,
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (chirp.pricePoints <= 0L || staffTier.isStaff()) {
            return selectPttChirp(chirp, staffName = buyerName, staffTier = staffTier)
        }
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            val existingOwned = (userSnap.get("ownedPttChirps") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            if (existingOwned.contains(chirp.id)) {
                return selectPttChirp(chirp)
            }
            require(currentPoints >= chirp.pricePoints) {
                "You need ${chirp.pricePoints} points to unlock ${chirp.label}."
            }
            val updatedOwned = (existingOwned + chirp.id).distinct().take(20)
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val grossCents = chirp.pricePoints * 100L
            val txRef = db.collection("pointTransactions").document()

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-chirp.pricePoints),
                    "ownedPttChirps" to updatedOwned,
                    "selectedPttChirp" to chirp.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.BUY_PTT_ITEM.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyerName,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to "CHIRP_${chirp.id}",
                    "pointsDelta" to -chirp.pricePoints,
                    "grossCents" to grossCents,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to grossCents,
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

    // --- MUTUAL DM TALK STREAKS (3+ CONSECUTIVE DAYS OF MUTUAL MESSAGES OR PTT CLIPS) ---

    private fun computeNextTalkStreakUpdate(thread: DirectThread, senderUid: String): Map<String, Any> {
        val today = utcDayKey()
        val yesterday = utcYesterdayKey()
        val otherUid = thread.otherUserId(senderUid)
        val updatedDays = thread.lastMessageDayByUserId.toMutableMap()
        updatedDays[senderUid] = today
        val otherSentToday = updatedDays[otherUid] == today
        val (nextCount, nextLastMutualDay) = if (otherSentToday) {
            when (thread.talkStreakLastMutualDay) {
                today -> thread.talkStreakCount.coerceAtLeast(1) to today
                yesterday -> (thread.talkStreakCount + 1).coerceAtMost(10_000) to today
                else -> 1 to today
            }
        } else {
            val activeCount = thread.activeTalkStreakCount(today, yesterday)
            val keptDay = if (activeCount > 0) thread.talkStreakLastMutualDay else ""
            activeCount to keptDay
        }
        return mapOf(
            "talkStreakCount" to nextCount,
            "talkStreakLastMutualDay" to nextLastMutualDay,
            "lastMessageDayByUserId" to updatedDays
        )
    }

    suspend fun recordLivePttTalkStreak(threadId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (threadId.isBlank()) return Result.success(Unit)
        val ref = db.collection("directThreads").document(threadId)
        val path = ref.path
        return try {
            val snap = ref.get().await()
            val thread = snap.toObject(DirectThread::class.java) ?: return Result.success(Unit)
            if (thread.isGroup || uid !in thread.participantIds || thread.hasAnyBlock() || thread.isUserKicked(uid)) {
                return Result.success(Unit)
            }
            val update = computeNextTalkStreakUpdate(thread, uid).toMutableMap()
            update["updatedAt"] = FieldValue.serverTimestamp()
            ref.update(update).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 24-HOUR SPOTLIGHT FRAME (5 POINTS -> GLOWING NEON AVATAR BORDER IN DISCOVER & CHATS) ---

    suspend fun purchaseSpotlightFrame(
        color: SpotlightFrameColor,
        buyerName: String,
        staffTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val freeStaff = staffTier.isStaff()
            if (!freeStaff) {
                val currentPoints = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(currentPoints >= SpotlightFrameColor.PRICE_POINTS) {
                    "You need 5 points to activate a 24-Hour Glowing Neon Spotlight Frame."
                }
            }
            val untilMs = System.currentTimeMillis() + SpotlightFrameColor.DURATION_MS
            val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
            val dirRef = db.collection("userDirectory").document(uid)
            val existingDir = dirRef.get().await()

            val batch = db.batch()
            val userPatch = mutableMapOf<String, Any>(
                "spotlightFrameUntilMillis" to untilMs,
                "spotlightFrameColorHex" to color.primaryHex,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!freeStaff) {
                userPatch["pointsBalance"] = FieldValue.increment(-SpotlightFrameColor.PRICE_POINTS)
            }
            batch.update(userRef, userPatch)
            if (existingDir.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "spotlightFrameUntilMillis" to untilMs,
                        "spotlightFrameColorHex" to color.primaryHex,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val safeHandle = (userSnap.getString("handle") ?: "vox_user").take(30)
                val safeColor = userSnap.getString("avatarColorHex") ?: "#1E6BFF"
                batch.set(
                    dirRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeBuyerName,
                        "handle" to safeHandle,
                        "avatarColorHex" to safeColor,
                        "staffTier" to staffTier.name,
                        "spotlightFrameUntilMillis" to untilMs,
                        "spotlightFrameColorHex" to color.primaryHex,
                        "referralCode" to generateReferralCode(uid),
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (!freeStaff) {
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.BUY_SPOTLIGHT_FRAME.name,
                        "buyerId" to uid,
                        "buyerName" to safeBuyerName,
                        "creatorId" to "",
                        "creatorName" to "",
                        "postId" to "",
                        "pointsDelta" to -SpotlightFrameColor.PRICE_POINTS,
                        "grossCents" to 500L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 500L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            if (staffTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeBuyerName,
                    perkType = AppPerkType.SPOTLIGHT_FRAME_24H,
                    detail = "${color.label} Spotlight Frame (24h · Free Staff Perk)"
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

    // --- GIFTED 1-MONTH VOX PRO PASS (POINTS STORE: 15 POINTS -> FRIEND OR ROOM HOST) ---

    suspend fun giftProPassToUser(
        targetUserId: String,
        targetName: String,
        senderName: String,
        giftNote: String = ""
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTargetUid = targetUserId.trim()
        if (cleanTargetUid.isBlank() || cleanTargetUid == uid) {
            return Result.failure(IllegalArgumentException("Choose a friend or room host to gift a 1-Month VOX Pro Pass."))
        }
        val path = "users/$cleanTargetUid"
        return try {
            val senderRef = db.collection("users").document(uid)
            val senderSnap = senderRef.get().await()
            val senderPoints = (senderSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            require(senderPoints >= UserProfile.PRO_GIFT_POINTS) {
                "You need ${UserProfile.PRO_GIFT_POINTS} points to gift a 1-Month VOX Pro Pass."
            }

            val recipientDirRef = db.collection("userDirectory").document(cleanTargetUid)
            val recipientDirSnap = recipientDirRef.get().await()
            if (!recipientDirSnap.exists()) {
                return Result.failure(IllegalStateException("Recipient account could not be found."))
            }
            val recipientEntry = recipientDirSnap.toObject(DirectoryEntry::class.java)
            val safeTargetName = targetName.trim()
                .ifBlank { recipientEntry?.effectiveDisplayName().orEmpty() }
                .ifBlank { "Member" }
                .take(40)
            val safeSenderName = senderName.trim().ifBlank { "Member" }.take(40)
            val nowMs = System.currentTimeMillis()

            if (recipientEntry != null) {
                if (recipientEntry.proLifetime) {
                    return Result.failure(IllegalStateException("$safeTargetName already has Lifetime VOX Pro!"))
                }
                val currentTier = recipientEntry.effectiveProTier(nowMs)
                if (currentTier == ProTier.VIP_PRO || currentTier == ProTier.BUSINESS) {
                    return Result.failure(
                        IllegalStateException("$safeTargetName already has ${currentTier.label} membership!")
                    )
                }
            }

            val existingExp = recipientEntry?.proExpiresAtMillis ?: 0L
            val baseExp = if (existingExp > nowMs) existingExp else nowMs
            val newExpiresAtMillis = baseExp + UserProfile.PRO_DURATION_MILLIS
            val safeNote = giftNote.trim().take(110).ifBlank { "GIFT_PRO_30D" }

            val recipientUserRef = db.collection("users").document(cleanTargetUid)
            val txRef = db.collection("pointTransactions").document()

            val batch = db.batch()
            batch.update(
                senderRef,
                mapOf(
                    "pointsBalance" to FieldValue.increment(-UserProfile.PRO_GIFT_POINTS),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                recipientUserRef,
                mapOf(
                    "proMember" to true,
                    "proTier" to ProTier.PRO.name,
                    "proExpiresAtMillis" to newExpiresAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                recipientDirRef,
                mapOf(
                    "proMember" to true,
                    "proTier" to ProTier.PRO.name,
                    "proExpiresAtMillis" to newExpiresAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.GIFT_PRO_PASS.name,
                    "buyerId" to uid,
                    "buyerName" to safeSenderName,
                    "creatorId" to cleanTargetUid,
                    "creatorName" to safeTargetName,
                    "postId" to safeNote,
                    "pointsDelta" to -UserProfile.PRO_GIFT_POINTS,
                    "grossCents" to 1500L,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 1500L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(safeTargetName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- DAILY LOGIN STREAK (+0.5 PT FOR FREE USERS AFTER 7-DAY STREAK) ---

    data class DailyLoginStreakOutcome(
        val streakDays: Int,
        val awardedHalfPoint: Boolean,
        val convertedToFullPoint: Boolean
    )

    suspend fun recordDailyLoginStreak(
        buyerName: String,
        staffTier: StaffTier
    ): Result<DailyLoginStreakOutcome?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val userRef = db.collection("users").document(uid)
        val path = userRef.path
        return try {
            val snap = userRef.get().await()
            if (!snap.exists()) return Result.success(null)
            val today = utcDayKey()
            val yesterday = utcYesterdayKey()
            val lastLoginDay = snap.getString("lastLoginDayKey").orEmpty()
            if (lastLoginDay == today) {
                return Result.success(null)
            }
            val oldStreak = (snap.get("loginStreakDays") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val nextStreak = if (lastLoginDay == yesterday) oldStreak + 1 else 1
            val proMember = snap.getBoolean("proMember") == true
            val proExpiry = (snap.get("proExpiresAtMillis") as? Number)?.toLong() ?: 0L
            val isProUser = proMember && proExpiry > System.currentTimeMillis()
            val isFreeMember = !isProUser && staffTier == StaffTier.USER
            val lastRewardDay = snap.getString("lastStreakRewardDayKey").orEmpty()
            val shouldAwardHalfPoint = isFreeMember &&
                nextStreak >= 7 &&
                (nextStreak % 7 == 0) &&
                lastRewardDay != today

            val oldHalf = (snap.get("halfPointsBalance") as? Number)?.toInt()?.coerceIn(0, 1) ?: 0
            val update = mutableMapOf<String, Any>(
                "loginStreakDays" to nextStreak,
                "lastLoginDayKey" to today,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            var convertedToFull = false
            val batch = db.batch()
            if (shouldAwardHalfPoint) {
                update["lastStreakRewardDayKey"] = today
                if (oldHalf == 0) {
                    update["halfPointsBalance"] = 1
                } else {
                    update["halfPointsBalance"] = 0
                    update["pointsBalance"] = FieldValue.increment(1L)
                    convertedToFull = true
                }
                val safeBuyerName = buyerName.trim().ifBlank { "Member" }.take(40)
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.DAILY_STREAK_BONUS.name,
                        "buyerId" to uid,
                        "buyerName" to safeBuyerName,
                        "creatorId" to "",
                        "creatorName" to "",
                        "postId" to "",
                        "pointsDelta" to if (convertedToFull) 1L else 0L,
                        "grossCents" to 0L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 0L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(userRef, update)
            batch.commit().await()
            Result.success(
                DailyLoginStreakOutcome(
                    streakDays = nextStreak,
                    awardedHalfPoint = shouldAwardHalfPoint,
                    convertedToFullPoint = convertedToFull
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- INVITE-A-FRIEND POINT BONUS (+2 PTS EACH AFTER VERIFIED ACCOUNT + FIRST PTT CLIP) ---

    data class ReferralBonusAwarded(
        val inviterName: String,
        val bonusPointsEach: Int = 2
    )

    suspend fun ensureMyReferralCode(): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val code = generateReferralCode(uid)
        val userRef = db.collection("users").document(uid)
        return try {
            val snap = userRef.get().await()
            if (snap.exists() && snap.getString("referralCode").isNullOrBlank()) {
                userRef.update(
                    mapOf(
                        "referralCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val dirRef = db.collection("userDirectory").document(uid)
            val dirSnap = dirRef.get().await()
            if (dirSnap.exists() && dirSnap.getString("referralCode").isNullOrBlank()) {
                dirRef.update(
                    mapOf(
                        "referralCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(snap.getString("referralCode").orEmpty().ifBlank { code })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    fun observeMyIncomingReferral(userId: String): Flow<ReferralRecord?> {
        if (userId.isBlank()) return flowOf(null)
        return db.collection("referrals").document(userId)
            .snapshots()
            .map { snap -> snap.toObject(ReferralRecord::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(null)
            }
    }

    fun observeMyInvitedReferrals(userId: String): Flow<List<ReferralRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return db.collection("referrals")
            .whereEqualTo("inviterId", userId)
            .snapshots()
            .map { snap ->
                snap.documents
                    .mapNotNull { it.toObject(ReferralRecord::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyList())
            }
    }

    suspend fun redeemReferralCode(
        codeInput: String,
        myName: String
    ): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val normalized = codeInput.trim().uppercase(java.util.Locale.US)
        val path = "referrals/$uid"
        return try {
            require(Regex("^[A-Z0-9\\-]{4,24}$").matches(normalized)) {
                "Enter a valid invite code (e.g. VOX-A7K9P2)."
            }
            val existingRef = db.collection("referrals").document(uid).get().await()
            require(!existingRef.exists()) {
                "You have already linked an invite code to this account."
            }
            val matches = db.collection("userDirectory")
                .whereEqualTo("referralCode", normalized)
                .limit(2)
                .get()
                .await()
            val inviterDoc = matches.documents.firstOrNull()
                ?: throw IllegalArgumentException("Invite code not found. Double-check the code and try again.")
            val inviterId = inviterDoc.getString("userId").orEmpty().ifBlank { inviterDoc.id }
            require(inviterId != uid) {
                "You cannot use your own invite code."
            }
            val inviterName = inviterDoc.getString("displayName").orEmpty().ifBlank { "Friend" }.take(40)
            val safeMyName = myName.trim().ifBlank { "Member" }.take(40)
            val isVerified = currentUser.isEmailVerified

            val batch = db.batch()
            batch.set(
                db.collection("referrals").document(uid),
                mapOf(
                    "inviteeId" to uid,
                    "inviteeName" to safeMyName,
                    "inviterId" to inviterId,
                    "inviterName" to inviterName,
                    "referralCode" to normalized,
                    "accountVerified" to isVerified,
                    "firstPttSent" to false,
                    "bonusClaimed" to false,
                    "bonusPointsEach" to 2,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "referredByUserId" to inviterId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(inviterName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeReferralBonusOnFirstPttClip(myName: String): Result<ReferralBonusAwarded?> {
        val currentUser = auth.currentUser
            ?: return Result.success(null)
        val uid = currentUser.uid
        val refDocRef = db.collection("referrals").document(uid)
        val path = refDocRef.path
        return try {
            val snap = refDocRef.get().await()
            if (!snap.exists()) return Result.success(null)
            val record = snap.toObject(ReferralRecord::class.java) ?: return Result.success(null)
            if (record.bonusClaimed) return Result.success(null)

            val isVerified = currentUser.isEmailVerified
            if (!isVerified) {
                // Record that first PTT clip is done; bonus will unlock once Google email is verified
                if (!record.firstPttSent) {
                    refDocRef.update(
                        mapOf(
                            "firstPttSent" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return Result.success(null)
            }

            val safeMyName = myName.trim().ifBlank { record.inviteeName.ifBlank { "Member" } }.take(40)
            val txRef = db.collection("pointTransactions").document()
            val batch = db.batch()
            batch.update(
                refDocRef,
                mapOf(
                    "accountVerified" to true,
                    "firstPttSent" to true,
                    "bonusClaimed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "pointsBalance" to FieldValue.increment(2L),
                    "referralBonusClaimed" to true,
                    "referredByUserId" to record.inviterId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(record.inviterId),
                mapOf(
                    "pointsBalance" to FieldValue.increment(2L),
                    "referralEarnedPoints" to FieldValue.increment(2L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.REFERRAL_BONUS.name,
                    "buyerId" to uid,
                    "buyerName" to safeMyName,
                    "creatorId" to record.inviterId,
                    "creatorName" to record.inviterName.ifBlank { "Friend" }.take(40),
                    "postId" to "",
                    "pointsDelta" to 2L,
                    "grossCents" to 0L,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(ReferralBonusAwarded(inviterName = record.inviterName.ifBlank { "your friend" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- WHO VIEWED MY PROFILE (BLURRED FREE TEASER + PRO / 2-PT UNLOCK) ---

    suspend fun recordProfileView(
        targetUserId: String,
        viewerProfile: UserProfile,
        viewerTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (targetUserId.isBlank() || targetUserId == uid) return Result.success(Unit)
        val viewId = "${targetUserId}_${uid}"
        val path = "profileViews/$viewId"
        return try {
            val now = System.currentTimeMillis()
            val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(now))
            val safeName = viewerProfile.displayName.trim().ifBlank { "Member" }.take(40)
            val rawHandle = viewerProfile.handle.trim().replace(" ", "_").take(30)
            val safeHandle = if (rawHandle.length >= 2) rawHandle else "vox_user"
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(viewerProfile.avatarColorHex)) {
                viewerProfile.avatarColorHex
            } else "#0088FF"
            val safePhoto = if (viewerProfile.photoBase64.length <= 140_000) viewerProfile.photoBase64 else ""
            val payload = mapOf(
                "targetUserId" to targetUserId,
                "viewerId" to uid,
                "viewerName" to safeName,
                "viewerHandle" to safeHandle,
                "viewerColorHex" to safeColor,
                "viewerPhotoBase64" to safePhoto,
                "viewerTier" to viewerTier.name,
                "viewerProMember" to viewerProfile.isProActive(viewerTier, now),
                "viewedAtMillis" to now,
                "viewedDayKey" to dayKey,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("profileViews").document(viewId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-blocking telemetry — ignore transient write issues cleanly
            Result.failure(e)
        }
    }

    fun observeMyProfileViews(userId: String): Flow<List<ProfileViewRecord>> = flow {
        val uid = requireUserId()
        val path = "profileViews"
        emitAll(
            db.collection("profileViews")
                .whereEqualTo("targetUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        doc.toObject(
                            ProfileViewRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(id = doc.id)
                    }.sortedByDescending { it.viewedAtMillis }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    emit(emptyList())
                }
        )
    }

    suspend fun unlockProfileViewers(
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Long> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val now = System.currentTimeMillis()
            val unlockedUntil = now + 86_400_000L
            val safeBuyer = buyerName.trim().ifBlank { "Member" }.take(40)

            if (staffTier.isStaff()) {
                userRef.update(
                    mapOf(
                        "profileViewersUnlockedUntilMillis" to unlockedUntil,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (staffTier == StaffTier.SUPPORT) {
                    logSupportSelfPerkUsage(
                        staffName = safeBuyer,
                        perkType = AppPerkType.PROFILE_VIEWERS_24H,
                        detail = "Unlocked Who Viewed My Profile (24h · Free Staff Perk)"
                    )
                }
                return Result.success(unlockedUntil)
            }

            val snap = userRef.get().await()
            val balance = (snap.get("pointsBalance") as? Number)?.toLong() ?: 0L
            require(balance >= 2L) { "You need 2 points to unlock Who Viewed Your Profile." }

            val txRef = db.collection("pointTransactions").document()
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "pointsBalance" to (balance - 2L),
                    "profileViewersUnlockedUntilMillis" to unlockedUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                txRef,
                mapOf(
                    "type" to PointTransactionType.UNLOCK_PROFILE_VIEWERS.name,
                    "buyerId" to uid,
                    "buyerName" to safeBuyer,
                    "creatorId" to "",
                    "creatorName" to "",
                    "postId" to "",
                    "pointsDelta" to -2L,
                    "grossCents" to 200L,
                    "creatorShareCents" to 0L,
                    "platformShareCents" to 200L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(unlockedUntil)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- PRIORITY BREAK-IN CHIRPS (1-2 POINTS, OPT-IN CLOSE FRIENDS) ---

    suspend fun updatePriorityChirpOptIn(optIn: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val batch = db.batch()
            val userRef = db.collection("users").document(uid)
            val dirRef = db.collection("userDirectory").document(uid)
            batch.update(
                userRef,
                mapOf(
                    "priorityChirpOptIn" to optIn,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val dirSnap = dirRef.get().await()
            if (dirSnap.exists()) {
                batch.update(
                    dirRef,
                    mapOf(
                        "priorityChirpOptIn" to optIn,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setDirectThreadPriorityChirpOptIn(
        thread: DirectThread,
        optIn: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (thread.isGroup) return Result.failure(IllegalArgumentException("Priority Chirps are for 1-on-1 DMs."))
        val path = "directThreads/${thread.id}"
        return try {
            val current = thread.priorityChirpOptInUserIds
            val updated = if (optIn) {
                (current + uid).distinct()
            } else {
                current.filter { it != uid }
            }
            if (updated == current) return Result.success(Unit)
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "priorityChirpOptInUserIds" to updated,
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

    suspend fun sendPriorityBreakInChirp(
        thread: DirectThread,
        senderName: String,
        senderTier: StaffTier,
        messageText: String,
        pointsCost: Int,
        displayedSenderTier: StaffTier = senderTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (thread.isGroup) return Result.failure(IllegalArgumentException("Priority Chirps are only available in 1-on-1 DMs."))
        val safeCost = pointsCost.coerceIn(1, 2)
        val cleanMsg = messageText.trim().ifBlank {
            if (safeCost >= 2) "Urgent break-in — jump on PTT!" else "Priority chirp — check in when free!"
        }.take(160)
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock() && !thread.isUserKicked(uid)) {
                "Messaging is unavailable in this conversation."
            }
            val freeStaff = senderTier.isStaff()
            val userRef = db.collection("users").document(uid)
            var balance = 0L
            if (!freeStaff) {
                val userSnap = userRef.get().await()
                balance = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(balance >= safeCost.toLong()) {
                    "You need $safeCost point${if (safeCost == 1) "" else "s"} to send a Priority Chirp."
                }
            }

            val otherUid = thread.otherUserId(uid)
            val otherName = thread.displayTitle(uid).take(40)
            val safeSender = senderName.trim().ifBlank { "Member" }.take(40)
            val now = System.currentTimeMillis()
            val msgRef = db.collection("directThreads").document(thread.id)
                .collection("messages").document()
            val preview = (if (safeCost >= 2) "🚨 URGENT CHIRP: $cleanMsg" else "📡 PRIORITY CHIRP: $cleanMsg").take(300)
            val grossCents = safeCost * 100L

            val batch = db.batch()
            if (!freeStaff) {
                batch.update(
                    userRef,
                    mapOf(
                        "pointsBalance" to (balance - safeCost.toLong()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                db.collection("directThreads").document(thread.id),
                mapOf(
                    "lastPriorityChirpAtMillis" to now,
                    "lastPriorityChirpSenderId" to uid,
                    "lastPriorityChirpSenderName" to safeSender,
                    "lastPriorityChirpMessage" to cleanMsg,
                    "lastPriorityChirpPoints" to safeCost,
                    "lastPriorityChirpAcknowledgedBy" to listOf(uid),
                    "lastMessageText" to preview,
                    "lastMessageSenderId" to uid,
                    "unreadByUserIds" to thread.participantIds.filter { it != uid },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                msgRef,
                mapOf(
                    "threadId" to thread.id,
                    "participantIds" to thread.participantIds,
                    "senderId" to uid,
                    "senderName" to safeSender,
                    "senderTier" to displayedSenderTier.name,
                    "text" to cleanMsg,
                    "messageType" to DirectMessage.TYPE_PRIORITY_CHIRP,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (!freeStaff) {
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.PRIORITY_CHIRP.name,
                        "buyerId" to uid,
                        "buyerName" to safeSender,
                        "creatorId" to otherUid,
                        "creatorName" to otherName,
                        "postId" to thread.id,
                        "pointsDelta" to -safeCost.toLong(),
                        "grossCents" to grossCents,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to grossCents,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            if (senderTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeSender,
                    perkType = AppPerkType.PRIORITY_CHIRP,
                    detail = "Sent Priority Chirp to $otherName (Free Staff Perk)"
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

    // --- 24-HOUR DM SCROLLING BANNER (1 POINT OR FREE FOR STAFF) ---

    suspend fun purchaseDirectThreadBanner(
        thread: DirectThread,
        bannerText: String,
        colorHex: String,
        buyerName: String,
        staffTier: StaffTier = StaffTier.USER
    ): Result<Long> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanBanner = bannerText.trim().take(140)
        if (cleanBanner.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a message for the scrolling banner."))
        }
        val safeColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(colorHex)) colorHex else DmBannerColor.ELECTRIC_CYAN.hex
        val path = "directThreads/${thread.id}"
        return try {
            require(!thread.hasAnyBlock() && !thread.isUserKicked(uid)) {
                "Banner unavailable in this conversation."
            }
            require(!thread.isGroup || (!thread.groupDisabled && !thread.isBannedInGroup(uid) && !thread.isBouncedInGroup(uid))) {
                "Banner unavailable in this Group DM."
            }
            val freeStaff = staffTier.isStaff()
            val userRef = db.collection("users").document(uid)
            var balance = 0L
            if (!freeStaff) {
                val userSnap = userRef.get().await()
                balance = (userSnap.get("pointsBalance") as? Number)?.toLong() ?: 0L
                require(balance >= 1L) { "You need 1 point to set a 24-hour scrolling banner." }
            }

            val now = System.currentTimeMillis()
            val expiresAt = now + 86_400_000L
            val safeBuyer = buyerName.trim().ifBlank { "Member" }.take(40)
            val threadDisplayLabel = thread.displayTitle(uid).ifBlank { if (thread.isGroup) "Group DM" else "Private DM" }.take(40)

            val batch = db.batch()
            if (!freeStaff) {
                batch.update(
                    userRef,
                    mapOf(
                        "pointsBalance" to (balance - 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                db.collection("directThreads").document(thread.id),
                mapOf(
                    "bannerText" to cleanBanner,
                    "bannerAuthorId" to uid,
                    "bannerAuthorName" to safeBuyer,
                    "bannerColorHex" to safeColor,
                    "bannerExpiresAtMillis" to expiresAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (!freeStaff) {
                val txRef = db.collection("pointTransactions").document()
                batch.set(
                    txRef,
                    mapOf(
                        "type" to PointTransactionType.DM_SCROLLING_BANNER.name,
                        "buyerId" to uid,
                        "buyerName" to safeBuyer,
                        "creatorId" to "",
                        "creatorName" to threadDisplayLabel,
                        "postId" to thread.id,
                        "pointsDelta" to -1L,
                        "grossCents" to 100L,
                        "creatorShareCents" to 0L,
                        "platformShareCents" to 100L,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            if (staffTier == StaffTier.SUPPORT) {
                logSupportSelfPerkUsage(
                    staffName = safeBuyer,
                    perkType = AppPerkType.DM_BANNER_24H,
                    detail = "Set 24h DM Banner in $threadDisplayLabel (Free Staff Perk)"
                )
            }
            Result.success(expiresAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- AUTOMATED 5-STRIKE REPORT ESCALATION, USER MODERATION FILES & PRIVATE FILE NOTES ---

    private suspend fun commitReportWithAutomatedStrikes(
        reportRef: com.google.firebase.firestore.DocumentReference,
        reportPayload: Map<String, Any>,
        reporterId: String,
        reporterName: String,
        targetUserId: String,
        targetName: String,
        category: String,
        details: String
    ) {
        val nowMs = System.currentTimeMillis()
        val rateLimitRef = db.collection("reportRateLimits").document(reporterId)
        val rateSnap = runCatching { rateLimitRef.get().await() }.getOrNull()
        val existingRate = rateSnap?.takeIf { it.exists() }?.toObject(ReportRateLimit::class.java)
        val activeTargetTimestamps = existingRate?.recentTargetTimestamps
            .orEmpty()
            .filterValues { nowMs - it < ReportRateLimit.REPORT_WINDOW_MS }

        val prevReportForTarget = activeTargetTimestamps[targetUserId]
        if (prevReportForTarget != null) {
            val minsLeft = (((prevReportForTarget + ReportRateLimit.REPORT_WINDOW_MS - nowMs) + 59_999L) / 60_000L)
                .coerceAtLeast(1L)
            throw IllegalStateException(
                "You already reported ${targetName.ifBlank { "this user" }} within the last hour. Try again in ${minsLeft}m."
            )
        }
        if (activeTargetTimestamps.size >= ReportRateLimit.MAX_DISTINCT_TARGETS_PER_HOUR) {
            val oldestTs = activeTargetTimestamps.values.minOrNull() ?: nowMs
            val minsLeft = (((oldestTs + ReportRateLimit.REPORT_WINDOW_MS - nowMs) + 59_999L) / 60_000L)
                .coerceAtLeast(1L)
            throw IllegalStateException(
                "Report limit reached: you can only report 3 different users (1 time each) per hour. Try again in ${minsLeft}m."
            )
        }

        val updatedRateMap = activeTargetTimestamps.toMutableMap().apply {
            put(targetUserId, nowMs)
        }
        val rateLimitPayload = mapOf(
            "userId" to reporterId,
            "recentTargetTimestamps" to updatedRateMap,
            "lastTargetUserId" to targetUserId,
            "lastReportId" to reportRef.id,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val canAutoEscalateTarget = targetUserId.isNotBlank() &&
            targetUserId != reporterId &&
            runCatching {
                val dirSnap = db.collection("userDirectory").document(targetUserId).get().await()
                StaffTier.fromRaw(dirSnap.getString("staffTier")) != StaffTier.ADMIN
            }.getOrDefault(true)

        if (!canAutoEscalateTarget) {
            val simpleBatch = db.batch()
            simpleBatch.set(reportRef, reportPayload)
            if (existingRate == null) {
                simpleBatch.set(rateLimitRef, rateLimitPayload)
            } else {
                simpleBatch.update(rateLimitRef, rateLimitPayload)
            }
            simpleBatch.commit().await()
            return
        }

        val modRef = db.collection("userModerationFiles").document(targetUserId)
        val modSnap = runCatching { modRef.get().await() }.getOrNull()
        val existingFile = modSnap?.takeIf { it.exists() }?.toObject(UserModerationFile::class.java)
        val oldCount = existingFile?.reportCount ?: 0
        val newCount = oldCount + 1

        val actionTaken: FileNoteAction
        val newStatus: InvestigationStatus
        var newWarningCount = existingFile?.warningCount ?: 0
        var newWarningMessage = existingFile?.lastWarningMessage.orEmpty()
        var newWarningAtMillis = existingFile?.lastWarningAtMillis ?: 0L
        var newSuspendedUntilMillis = existingFile?.suspendedUntilMillis ?: 0L
        var newSuspendedPendingReview = existingFile?.suspendedUntilInvestigationResolved ?: false
        var newSuspensionReason = existingFile?.suspensionReason.orEmpty()

        when {
            newCount == 1 -> {
                actionTaken = FileNoteAction.INVESTIGATION_STARTED
                newStatus = InvestigationStatus.INVESTIGATING
            }
            newCount == 2 -> {
                actionTaken = FileNoteAction.WARNING_ISSUED
                newStatus = InvestigationStatus.WARNING_ISSUED
                newWarningCount += 1
                newWarningMessage = "Formal Warning: Your account has received 2 safety reports ($category). Further reports will trigger automatic account suspensions."
                newWarningAtMillis = nowMs
            }
            newCount == 3 -> {
                actionTaken = FileNoteAction.SUSPENDED_1H
                newStatus = InvestigationStatus.SUSPENDED_1H
                newSuspendedUntilMillis = nowMs + 60L * 60L * 1000L
                newSuspensionReason = "3rd safety report ($category): Automatic 1-hour suspension."
            }
            newCount == 4 -> {
                actionTaken = FileNoteAction.SUSPENDED_2H
                newStatus = InvestigationStatus.SUSPENDED_2H
                newSuspendedUntilMillis = nowMs + 2L * 60L * 60L * 1000L
                newSuspensionReason = "4th safety report ($category): Automatic 2-hour suspension."
            }
            else -> {
                actionTaken = FileNoteAction.SUSPENDED_PENDING_REVIEW
                newStatus = InvestigationStatus.SUSPENDED_PENDING_REVIEW
                newSuspendedPendingReview = true
                newSuspensionReason = "Report #$newCount ($category): Account suspended until a Support member or Admin resolves the investigation."
            }
        }

        val noteRef = modRef.collection("notes").document(reportRef.id)
        val notePayload = mapOf(
            "targetUserId" to targetUserId,
            "reportId" to reportRef.id,
            "strikeNumber" to newCount,
            "actionTaken" to actionTaken.name,
            "category" to category.take(40),
            "reason" to details.take(450),
            "reporterId" to reporterId,
            "reporterName" to reporterName.take(40),
            "actorRole" to "REPORTER",
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(reportRef, reportPayload)
        if (existingRate == null) {
            batch.set(rateLimitRef, rateLimitPayload)
        } else {
            batch.update(rateLimitRef, rateLimitPayload)
        }
        if (existingFile == null) {
            batch.set(
                modRef,
                mapOf(
                    "userId" to targetUserId,
                    "targetName" to targetName.ifBlank { "Member" }.take(40),
                    "reportCount" to newCount,
                    "warningCount" to newWarningCount,
                    "investigationActive" to true,
                    "investigationStatus" to newStatus.name,
                    "suspendedUntilMillis" to newSuspendedUntilMillis,
                    "suspendedUntilInvestigationResolved" to newSuspendedPendingReview,
                    "pendingAdminReview" to false,
                    "pendingSuspensionRequestId" to "",
                    "suspensionReason" to newSuspensionReason.take(240),
                    "lastWarningMessage" to newWarningMessage.take(300),
                    "lastWarningAtMillis" to newWarningAtMillis,
                    "lastWarningAcknowledgedAtMillis" to 0L,
                    "lastReportId" to reportRef.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.update(
                modRef,
                mapOf(
                    "targetName" to targetName.ifBlank { existingFile.targetName.ifBlank { "Member" } }.take(40),
                    "reportCount" to newCount,
                    "warningCount" to newWarningCount,
                    "investigationActive" to true,
                    "investigationStatus" to newStatus.name,
                    "suspendedUntilMillis" to newSuspendedUntilMillis,
                    "suspendedUntilInvestigationResolved" to newSuspendedPendingReview,
                    "suspensionReason" to newSuspensionReason.take(240),
                    "lastWarningMessage" to newWarningMessage.take(300),
                    "lastWarningAtMillis" to newWarningAtMillis,
                    "lastReportId" to reportRef.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.set(noteRef, notePayload)
        batch.commit().await()
    }

    fun observeMyModerationFile(userId: String): Flow<UserModerationFile?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = db.collection("userModerationFiles").document(userId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    trySend(snap.toObject(UserModerationFile::class.java))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeAllModerationFiles(): Flow<List<UserModerationFile>> = callbackFlow {
        val listener = db.collection("userModerationFiles")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(150)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "userModerationFiles")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty()
                    .mapNotNull { it.toObject(UserModerationFile::class.java) }
                    .sortedWith(
                        compareByDescending<UserModerationFile> { it.suspendedUntilInvestigationResolved }
                            .thenByDescending { it.investigationActive }
                            .thenByDescending { it.reportCount }
                    )
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeUserFileNotes(targetUserId: String): Flow<List<UserFileNote>> = callbackFlow {
        if (targetUserId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("userModerationFiles")
            .document(targetUserId)
            .collection("notes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "userModerationFiles/$targetUserId/notes")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val notes = snap?.documents.orEmpty()
                    .mapNotNull { it.toObject(UserFileNote::class.java) }
                trySend(notes)
            }
        awaitClose { listener.remove() }
    }

    suspend fun acknowledgeStrikeWarning(): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "userModerationFiles/$uid"
        return try {
            db.collection("userModerationFiles").document(uid).update(
                mapOf(
                    "lastWarningAcknowledgedAtMillis" to System.currentTimeMillis(),
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

    suspend fun resolveUserInvestigation(
        targetUserId: String,
        targetName: String,
        resolverName: String,
        resolverTier: StaffTier,
        resolutionNote: String,
        resetReportCount: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "userModerationFiles/$targetUserId"
        return try {
            require(resolverTier.isStaff()) { "Only Support or Admin can resolve investigations." }
            val cleanNote = resolutionNote.trim().take(280)
            require(cleanNote.isNotEmpty()) { "Please enter a resolution note." }

            val modRef = db.collection("userModerationFiles").document(targetUserId)
            val existing = modRef.get().await().toObject(UserModerationFile::class.java)
                ?: return Result.failure(IllegalStateException("User moderation file not found."))

            if (existing.pendingAdminReview && resolverTier != StaffTier.ADMIN) {
                return Result.failure(IllegalStateException("Only an App Administrator can review and lift a Support-requested suspension."))
            }

            val safeResolver = resolverName.trim().ifBlank { resolverTier.name }.take(40)
            val actorRole = if (resolverTier == StaffTier.ADMIN) "ADMIN" else "SUPPORT"
            val noteRef = modRef.collection("notes").document()

            val updates = mutableMapOf<String, Any>(
                "investigationActive" to false,
                "investigationStatus" to InvestigationStatus.RESOLVED.name,
                "suspendedUntilMillis" to 0L,
                "suspendedUntilInvestigationResolved" to false,
                "pendingAdminReview" to false,
                "pendingSuspensionRequestId" to "",
                "suspensionReason" to "",
                "lastResolvedById" to uid,
                "lastResolvedByName" to safeResolver,
                "lastResolutionNote" to cleanNote,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (resetReportCount) {
                updates["reportCount"] = 0
                updates["warningCount"] = 0
            }

            val batch = db.batch()
            batch.update(modRef, updates)
            batch.set(
                noteRef,
                mapOf(
                    "targetUserId" to targetUserId,
                    "reportId" to "",
                    "strikeNumber" to existing.reportCount,
                    "actionTaken" to FileNoteAction.INVESTIGATION_RESOLVED.name,
                    "category" to "RESOLUTION",
                    "reason" to cleanNote,
                    "reporterId" to uid,
                    "reporterName" to safeResolver,
                    "actorRole" to actorRole,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            if (resolverTier == StaffTier.ADMIN) {
                runCatching {
                    val userRef = db.collection("users").document(targetUserId)
                    if (userRef.get().await().exists()) {
                        userRef.update(
                            mapOf(
                                "suspendedUntilMillis" to 0L,
                                "suspensionReason" to "",
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
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- SUPPORT STAFF SUSPENSION REQUESTS (IMMEDIATE SUSPENSION PENDING ADMIN REVIEW) ---

    fun observeSuspensionRequests(): Flow<List<SuspensionRequest>> = callbackFlow {
        val listener = db.collection("suspensionRequests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "suspensionRequests")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty()
                    .mapNotNull { it.toObject(SuspensionRequest::class.java) }
                    .sortedWith(
                        compareByDescending<SuspensionRequest> { it.statusEnum() == SuspensionRequestStatus.PENDING }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun submitSupportSuspensionRequest(
        targetUserId: String,
        targetName: String,
        requestedByName: String,
        requesterTier: StaffTier,
        reason: String,
        roomId: String = "",
        requestType: String = "SUSPENSION",
        isBusinessAccount: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "suspensionRequests"
        return try {
            require(requesterTier.isStaff()) { "Only Support Staff or Admins can submit a review request." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Select a valid member." }
            val cleanReason = reason.trim().take(400)
            require(cleanReason.length >= 3) { "Please provide a clear reason (at least 3 characters)." }

            val targetIsBusiness = isBusinessAccount || isTargetUserBusinessAccount(targetUserId)
            val resolvedRequestType = if (targetIsBusiness) {
                val allowedBizTypes = setOf("BUSINESS_EDIT", "BUSINESS_REMOVE", "BUSINESS_SUSPEND", "BUSINESS_BAN")
                if (requestType.uppercase() in allowedBizTypes) requestType.uppercase() else "BUSINESS_SUSPEND"
            } else {
                "SUSPENSION"
            }

            val reqRef = db.collection("suspensionRequests").document()
            val modRef = db.collection("userModerationFiles").document(targetUserId)
            val existingFile = runCatching { modRef.get().await() }.getOrNull()
                ?.takeIf { it.exists() }
                ?.toObject(UserModerationFile::class.java)

            val safeTargetName = targetName.trim().ifBlank { existingFile?.targetName.orEmpty().ifBlank { "Member" } }.take(40)
            val safeRequester = requestedByName.trim().ifBlank { requesterTier.name }.take(40)
            val suspensionMsg = "Support requested suspension pending Admin review: $cleanReason".take(240)
            val actorRole = if (requesterTier == StaffTier.ADMIN) "ADMIN" else "SUPPORT"

            if (targetIsBusiness) {
                // Support Staff cannot remove, ban, edit, or immediately suspend a Business Account.
                // Write ONLY the approval request for an App Admin to review.
                reqRef.set(
                    mapOf(
                        "targetUserId" to targetUserId,
                        "targetName" to safeTargetName,
                        "requestedById" to uid,
                        "requestedByName" to safeRequester,
                        "reason" to cleanReason,
                        "roomId" to roomId.take(260),
                        "requestType" to resolvedRequestType,
                        "isBusinessAccount" to true,
                        "status" to SuspensionRequestStatus.PENDING.name,
                        "reviewedByAdminId" to "",
                        "reviewedByAdminName" to "",
                        "adminDecisionNote" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success(Unit)
            }

            val batch = db.batch()
            batch.set(
                reqRef,
                mapOf(
                    "targetUserId" to targetUserId,
                    "targetName" to safeTargetName,
                    "requestedById" to uid,
                    "requestedByName" to safeRequester,
                    "reason" to cleanReason,
                    "roomId" to roomId.take(260),
                    "requestType" to resolvedRequestType,
                    "isBusinessAccount" to false,
                    "status" to SuspensionRequestStatus.PENDING.name,
                    "reviewedByAdminId" to "",
                    "reviewedByAdminName" to "",
                    "adminDecisionNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (existingFile == null) {
                batch.set(
                    modRef,
                    mapOf(
                        "userId" to targetUserId,
                        "targetName" to safeTargetName,
                        "reportCount" to 0,
                        "warningCount" to 0,
                        "investigationActive" to true,
                        "investigationStatus" to InvestigationStatus.SUSPENDED_PENDING_ADMIN_REVIEW.name,
                        "suspendedUntilMillis" to 0L,
                        "suspendedUntilInvestigationResolved" to true,
                        "pendingAdminReview" to true,
                        "pendingSuspensionRequestId" to reqRef.id,
                        "suspensionReason" to suspensionMsg,
                        "lastWarningMessage" to "",
                        "lastWarningAtMillis" to 0L,
                        "lastWarningAcknowledgedAtMillis" to 0L,
                        "lastReportId" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    modRef,
                    mapOf(
                        "targetName" to safeTargetName,
                        "investigationActive" to true,
                        "investigationStatus" to InvestigationStatus.SUSPENDED_PENDING_ADMIN_REVIEW.name,
                        "suspendedUntilInvestigationResolved" to true,
                        "pendingAdminReview" to true,
                        "pendingSuspensionRequestId" to reqRef.id,
                        "suspensionReason" to suspensionMsg,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                modRef.collection("notes").document(reqRef.id),
                mapOf(
                    "targetUserId" to targetUserId,
                    "reportId" to reqRef.id,
                    "strikeNumber" to (existingFile?.reportCount ?: 0),
                    "actionTaken" to FileNoteAction.SUPPORT_SUSPENSION_REQUESTED.name,
                    "category" to "SUSPENSION_REQUEST",
                    "reason" to cleanReason,
                    "reporterId" to uid,
                    "reporterName" to safeRequester,
                    "actorRole" to actorRole,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            // Also remove from room immediately if submitted inside a live room
            if (roomId.isNotBlank() && !roomId.contains("_")) {
                runCatching {
                    val pRef = db.collection("rooms").document(roomId).collection("participants").document(targetUserId)
                    if (pRef.get().await().exists()) {
                        pRef.delete().await()
                    }
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

    suspend fun adminRevokeBusinessAccount(
        targetUserId: String,
        targetName: String,
        adminName: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank() || targetUserId == uid) {
            return Result.failure(IllegalArgumentException("Select a valid Business Account."))
        }
        val cleanReason = reason.trim().ifBlank { "Business Account removed by App Admin ($adminName)." }.take(240)
        val path = "users/$targetUserId"
        return try {
            val userRef = db.collection("users").document(targetUserId)
            if (userRef.get().await().exists()) {
                userRef.update(
                    mapOf(
                        "proTier" to ProTier.FREE.name,
                        "proMember" to false,
                        "proLifetime" to false,
                        "proExpiresAtMillis" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            runCatching {
                val dirRef = db.collection("userDirectory").document(targetUserId)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "proTier" to ProTier.FREE.name,
                            "proMember" to false,
                            "proLifetime" to false,
                            "proExpiresAtMillis" to 0L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            // Deactivate owned company codes so new employees cannot join under the removed Business Account
            runCatching {
                val codesSnap = db.collection("companyCodes")
                    .whereEqualTo("ownerId", targetUserId)
                    .get()
                    .await()
                codesSnap.documents.forEach { codeDoc ->
                    codeDoc.reference.update(
                        mapOf(
                            "active" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            // Notify the former Business Account owner
            runCatching {
                db.collection("staffAlerts").document().set(
                    mapOf(
                        "targetUserId" to targetUserId,
                        "targetName" to targetName.trim().ifBlank { "Member" }.take(40),
                        "senderId" to uid,
                        "senderName" to adminName.trim().ifBlank { "App Admin" }.take(40),
                        "senderTier" to StaffTier.ADMIN.name,
                        "title" to "Business Account Removed",
                        "message" to cleanReason,
                        "urgent" to true,
                        "acknowledged" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
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

    suspend fun adminReviewSuspensionRequest(
        request: SuspensionRequest,
        decision: SuspensionRequestStatus,
        timedHours: Int,
        adminName: String,
        adminNote: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "suspensionRequests/${request.id}"
        return try {
            require(decision != SuspensionRequestStatus.PENDING) { "Choose a final Admin decision." }
            val cleanNote = adminNote.trim().ifBlank { decision.label }.take(300)
            val safeAdmin = adminName.trim().ifBlank { "Admin" }.take(40)
            val reqRef = db.collection("suspensionRequests").document(request.id)
            val modRef = db.collection("userModerationFiles").document(request.targetUserId)
            val existingFile = runCatching { modRef.get().await() }.getOrNull()
                ?.takeIf { it.exists() }
                ?.toObject(UserModerationFile::class.java)

            val nowMs = System.currentTimeMillis()
            val timedUntilMs = if (decision == SuspensionRequestStatus.APPROVED_TIMED) {
                nowMs + timedHours.coerceIn(1, 720).toLong() * 60L * 60L * 1000L
            } else {
                0L
            }

            val batch = db.batch()
            batch.update(
                reqRef,
                mapOf(
                    "status" to decision.name,
                    "reviewedByAdminId" to uid,
                    "reviewedByAdminName" to safeAdmin,
                    "adminDecisionNote" to cleanNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (existingFile != null) {
                val modUpdates = mutableMapOf<String, Any>(
                    "pendingAdminReview" to false,
                    "pendingSuspensionRequestId" to "",
                    "lastResolvedById" to uid,
                    "lastResolvedByName" to safeAdmin,
                    "lastResolutionNote" to cleanNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                when (decision) {
                    SuspensionRequestStatus.DISMISSED_LIFTED -> {
                        modUpdates["investigationActive"] = false
                        modUpdates["investigationStatus"] = InvestigationStatus.RESOLVED.name
                        modUpdates["suspendedUntilMillis"] = 0L
                        modUpdates["suspendedUntilInvestigationResolved"] = false
                        modUpdates["suspensionReason"] = ""
                    }
                    SuspensionRequestStatus.APPROVED_TIMED -> {
                        modUpdates["investigationActive"] = false
                        modUpdates["investigationStatus"] = InvestigationStatus.RESOLVED.name
                        modUpdates["suspendedUntilMillis"] = timedUntilMs
                        modUpdates["suspendedUntilInvestigationResolved"] = false
                        modUpdates["suspensionReason"] = "Admin confirmed ${timedHours}h suspension: $cleanNote".take(240)
                    }
                    SuspensionRequestStatus.APPROVED_BAN -> {
                        modUpdates["investigationActive"] = false
                        modUpdates["investigationStatus"] = InvestigationStatus.RESOLVED.name
                        modUpdates["suspendedUntilMillis"] = 0L
                        modUpdates["suspendedUntilInvestigationResolved"] = false
                        modUpdates["suspensionReason"] = "Admin permanently banned account: $cleanNote".take(240)
                    }
                    SuspensionRequestStatus.APPROVED_ACTION -> {
                        modUpdates["investigationActive"] = false
                        modUpdates["investigationStatus"] = InvestigationStatus.RESOLVED.name
                    }
                    SuspensionRequestStatus.PENDING -> Unit
                }
                batch.update(modRef, modUpdates)
                batch.set(
                    modRef.collection("notes").document(),
                    mapOf(
                        "targetUserId" to request.targetUserId,
                        "reportId" to request.id,
                        "strikeNumber" to existingFile.reportCount,
                        "actionTaken" to FileNoteAction.ADMIN_REVIEW_COMPLETED.name,
                        "category" to decision.name,
                        "reason" to "${decision.label}: $cleanNote".take(450),
                        "reporterId" to uid,
                        "reporterName" to safeAdmin,
                        "actorRole" to "ADMIN",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            when (decision) {
                SuspensionRequestStatus.APPROVED_BAN -> {
                    val userRef = db.collection("users").document(request.targetUserId)
                    if (userRef.get().await().exists()) {
                        userRef.update(
                            mapOf(
                                "globallyBanned" to true,
                                "suspensionReason" to "Admin banned: $cleanNote".take(200),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    runCatching {
                        syncDeviceBanForTargetUser(
                            targetUserId = request.targetUserId,
                            targetName = request.targetName,
                            active = true,
                            reason = "Admin banned: $cleanNote",
                            actorUid = uid,
                            actorName = safeAdmin
                        )
                    }
                }
                SuspensionRequestStatus.APPROVED_TIMED -> {
                    val userRef = db.collection("users").document(request.targetUserId)
                    if (userRef.get().await().exists()) {
                        userRef.update(
                            mapOf(
                                "suspendedUntilMillis" to timedUntilMs,
                                "suspensionReason" to "Admin approved ${timedHours}h suspension: $cleanNote".take(200),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                SuspensionRequestStatus.APPROVED_ACTION -> {
                    if (request.requestType == BusinessApprovalAction.REMOVE.requestType) {
                        adminRevokeBusinessAccount(
                            targetUserId = request.targetUserId,
                            targetName = request.targetName,
                            adminName = safeAdmin,
                            reason = cleanNote
                        )
                    }
                }
                SuspensionRequestStatus.DISMISSED_LIFTED -> {
                    runCatching {
                        syncDeviceBanForTargetUser(
                            targetUserId = request.targetUserId,
                            targetName = request.targetName,
                            active = false,
                            reason = "Admin lifted restriction: $cleanNote",
                            actorUid = uid,
                            actorName = safeAdmin
                        )
                    }
                }
                SuspensionRequestStatus.PENDING -> Unit
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // GROUP DM ADMINISTRATOR CONTROLS (MUTE / BOUNCE 24H / BAN)
    // =========================================================================

    suspend fun sendDirectPresenceNotice(
        thread: DirectThread,
        senderName: String,
        senderTier: StaffTier,
        text: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val cleanText = text.trim().take(500)
        if (thread.id.isBlank() || cleanText.isBlank()) return Result.success(Unit)
        if (thread.voxOfficial ||
            !thread.participantIds.contains(uid) ||
            thread.hasAnyBlock() ||
            thread.isUserKicked(uid) ||
            thread.isBannedInGroup(uid) ||
            thread.isBouncedInGroup(uid) ||
            thread.isMutedInGroup(uid)
        ) {
            return Result.success(Unit)
        }
        return runCatching {
            val threadRef = db.collection("directThreads").document(thread.id)
            val msgRef = threadRef.collection("messages").document()
            val payload = mutableMapOf<String, Any>(
                "threadId" to thread.id,
                "participantIds" to thread.participantIds,
                "senderId" to uid,
                "senderName" to senderName.trim().ifBlank { "Member" }.take(40),
                "senderTier" to senderTier.name,
                "text" to cleanText,
                "messageType" to DirectMessage.TYPE_SUPPORT_HANDOFF,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (thread.companyOwnerId.isNotBlank()) {
                payload["companyOwnerId"] = thread.companyOwnerId
            }
            msgRef.set(payload).await()
            Unit
        }
    }

    private suspend fun postGroupDmModNotice(thread: DirectThread, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleanText = text.trim().take(500)
        if (thread.id.isBlank() || cleanText.isBlank() || !thread.isGroup) return
        runCatching {
            val actorName = thread.participantNamesById[uid]?.trim()?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: "Moderator"
            val threadRef = db.collection("directThreads").document(thread.id)
            val msgRef = threadRef.collection("messages").document()
            val payload = mutableMapOf<String, Any>(
                "threadId" to thread.id,
                "participantIds" to thread.participantIds,
                "senderId" to uid,
                "senderName" to actorName.take(40),
                "senderTier" to StaffTier.USER.name,
                "text" to cleanText,
                "messageType" to DirectMessage.TYPE_SUPPORT_HANDOFF,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (thread.companyOwnerId.isNotBlank()) {
                payload["companyOwnerId"] = thread.companyOwnerId
            }
            msgRef.set(payload).await()
        }
    }

    suspend fun setGroupDmUserMuted(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        mute: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Group DM moderation is only available in group chats." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "You cannot mute yourself." }
            require(targetUserId != thread.effectiveGroupAdminId()) { "You cannot mute the Group DM Administrator." }
            if (!thread.isGroupAdmin(uid) && thread.isGroupSupport(targetUserId)) {
                return Result.failure(IllegalStateException("Group Support Staff cannot mute other Group Support Staff."))
            }
            val nextMuted = thread.mutedUserIds.toMutableSet().apply {
                if (mute) add(targetUserId) else remove(targetUserId)
            }.toList()
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "mutedUserIds" to nextMuted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val safeName = targetName.trim().ifBlank { "Member" }.take(40)
            postGroupDmModNotice(
                thread = thread,
                text = if (mute) "$safeName was muted in this chat" else "$safeName was unmuted in this chat"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun bounceGroupDmUser(
        thread: DirectThread,
        targetUserId: String,
        targetName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Group DM bounce is only available in group chats." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "You cannot bounce yourself." }
            require(targetUserId != thread.effectiveGroupAdminId()) { "You cannot bounce the Group DM Administrator." }
            if (!thread.isGroupAdmin(uid) && thread.isGroupSupport(targetUserId)) {
                return Result.failure(IllegalStateException("Group Support Staff cannot bounce other Group Support Staff."))
            }
            val untilMs = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val nextBouncedUntil = thread.bouncedUntilByUserId.toMutableMap().apply {
                put(targetUserId, untilMs)
            }
            val safeName = targetName.trim().ifBlank { "Member" }.take(40)
            val nextBouncedNames = thread.bouncedNamesByUserId.toMutableMap().apply {
                put(targetUserId, safeName)
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "bouncedUntilByUserId" to nextBouncedUntil,
                    "bouncedNamesByUserId" to nextBouncedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            postGroupDmModNotice(
                thread = thread,
                text = "$safeName was bounced from this chat for 24 hours"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeGroupDmBounce(
        thread: DirectThread,
        targetUserId: String
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Not a group DM." }
            val safeName = thread.bouncedNamesByUserId[targetUserId]?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
            val nextBouncedUntil = thread.bouncedUntilByUserId.toMutableMap().apply {
                remove(targetUserId)
            }
            val nextBouncedNames = thread.bouncedNamesByUserId.toMutableMap().apply {
                remove(targetUserId)
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "bouncedUntilByUserId" to nextBouncedUntil,
                    "bouncedNamesByUserId" to nextBouncedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            postGroupDmModNotice(
                thread = thread,
                text = "$safeName's bounce was removed"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun banGroupDmUser(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        alsoRemoveFromParticipants: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Group DM ban is only available in group chats." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "You cannot ban yourself." }
            val nextBanned = (thread.bannedUserIds + targetUserId).distinct()
            val safeName = targetName.trim().ifBlank { "Member" }.take(40)
            val nextBannedNames = thread.bannedNamesByUserId.toMutableMap().apply {
                put(targetUserId, safeName)
            }
            val updates = mutableMapOf<String, Any>(
                "bannedUserIds" to nextBanned,
                "bannedNamesByUserId" to nextBannedNames,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val nextParticipants = if (alsoRemoveFromParticipants &&
                targetUserId != thread.companyOwnerId &&
                thread.participantIds.size > 1 &&
                thread.participantIds.contains(targetUserId)
            ) {
                val filtered = thread.participantIds.filter { it != targetUserId }
                updates["participantIds"] = filtered
                updates["participantNamesById"] = thread.participantNamesById.toMutableMap().apply {
                    remove(targetUserId)
                }
                filtered
            } else {
                thread.participantIds
            }
            db.collection("directThreads").document(thread.id).update(updates).await()
            postGroupDmModNotice(
                thread = thread.copy(participantIds = nextParticipants),
                text = "$safeName was banned from this chat"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun staffEditGroupDmName(
        thread: DirectThread,
        newGroupName: String,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        val cleanTitle = newGroupName.trim().take(60)
        if (cleanTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter a group name."))
        }
        val cleanActor = actorName.trim().ifBlank { "VOX Staff" }.take(40)
        return try {
            require(thread.isGroup) { "Only Group DMs can be renamed." }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "groupName" to cleanTitle,
                    "lastMessageText" to "Group renamed to \"$cleanTitle\" by $cleanActor",
                    "lastMessageSenderId" to uid,
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

    suspend fun staffRemoveGroupDmMember(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Only Group DM members can be removed." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Select another member to remove." }
            if (thread.companyOwnerId.isNotBlank() && targetUserId == thread.companyOwnerId) {
                return Result.failure(IllegalStateException("Cannot remove the Business Account owner directly."))
            }
            val nextParticipants = thread.participantIds.filter { it != targetUserId }
            require(nextParticipants.isNotEmpty()) { "Cannot remove the last remaining member." }
            val nextNames = thread.participantNamesById.toMutableMap().apply { remove(targetUserId) }
            val safeTarget = targetName.trim().ifBlank { "Member" }.take(40)
            val safeActor = actorName.trim().ifBlank { "VOX Staff" }.take(40)
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "participantIds" to nextParticipants,
                    "participantNamesById" to nextNames,
                    "lastMessageText" to "$safeTarget was removed from the group by $safeActor",
                    "lastMessageSenderId" to uid,
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

    /**
     * Removes a user from a Group DM (for Group DM Admins / App Admins) or kicks/removes the
     * other participant from a 1-on-1 DM thread.
     */
    suspend fun removeUserFromDirectThread(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        actorName: String,
        isAppAdmin: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Select another user to remove." }
            val safeTarget = targetName.trim().ifBlank { "Member" }.take(40)
            val safeActor = actorName.trim().ifBlank { "Member" }.take(40)
            if (thread.isGroup) {
                require(thread.isGroupAdmin(uid) || isAppAdmin) {
                    "Only the Group DM Admin can remove members from this group."
                }
                if (thread.companyOwnerId.isNotBlank() && targetUserId == thread.companyOwnerId) {
                    return Result.failure(IllegalStateException("Cannot remove the Business Account owner."))
                }
                val nextParticipants = thread.participantIds.filter { it != targetUserId }
                require(nextParticipants.isNotEmpty()) { "Cannot remove the last remaining member." }
                val nextNames = thread.participantNamesById.toMutableMap().apply { remove(targetUserId) }
                db.collection("directThreads").document(thread.id).update(
                    mapOf(
                        "participantIds" to nextParticipants,
                        "participantNamesById" to nextNames,
                        "lastMessageText" to "$safeTarget was removed from the group by $safeActor",
                        "lastMessageSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val updatedKicked = (thread.kickedUserIds + targetUserId).distinct()
                db.collection("directThreads").document(thread.id).update(
                    mapOf(
                        "kickedUserIds" to updatedKicked,
                        "lastMessageText" to "$safeTarget was removed from this DM by $safeActor",
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

    /**
     * Removes a DM thread from the user's inbox:
     * - For non-admin members of a multi-person Group DM, removes only the caller so the group stays intact.
     * - For 1-on-1 DMs or Group DM Admins, deletes the thread document.
     */
    suspend fun removeDirectThread(
        thread: DirectThread,
        actorName: String,
        isAppAdmin: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            val safeActor = actorName.trim().ifBlank { "Member" }.take(40)
            if (thread.isGroup && !thread.isGroupAdmin(uid) && thread.participantIds.size > 1 && !isAppAdmin) {
                val nextParticipants = thread.participantIds.filter { it != uid }
                val nextNames = thread.participantNamesById.toMutableMap().apply { remove(uid) }
                db.collection("directThreads").document(thread.id).update(
                    mapOf(
                        "participantIds" to nextParticipants,
                        "participantNamesById" to nextNames,
                        "lastMessageText" to "$safeActor left the group",
                        "lastMessageSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                db.collection("directThreads").document(thread.id).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun staffSetGroupDmDisabled(
        thread: DirectThread,
        disabled: Boolean,
        reason: String,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        val cleanReason = reason.trim().ifBlank {
            if (disabled) "Policy violations detected by VOX Staff." else ""
        }.take(240)
        val cleanActor = actorName.trim().ifBlank { "VOX Staff" }.take(40)
        return try {
            require(thread.isGroup) { "Only Group DMs can be disabled." }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "groupDisabled" to disabled,
                    "groupDisabledReason" to if (disabled) cleanReason else "",
                    "groupDisabledByName" to if (disabled) cleanActor else "",
                    "lastMessageText" to if (disabled) {
                        "Group DM disabled by $cleanActor: ${cleanReason.take(120)}"
                    } else {
                        "Group DM re-enabled by $cleanActor"
                    },
                    "lastMessageSenderId" to uid,
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

    suspend fun unbanGroupDmUser(
        thread: DirectThread,
        targetUserId: String
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Not a group DM." }
            val safeName = thread.bannedNamesByUserId[targetUserId]?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
            val nextBanned = thread.bannedUserIds.filter { it != targetUserId }
            val nextBannedNames = thread.bannedNamesByUserId.toMutableMap().apply {
                remove(targetUserId)
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "bannedUserIds" to nextBanned,
                    "bannedNamesByUserId" to nextBannedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            postGroupDmModNotice(
                thread = thread,
                text = "$safeName was unbanned from this chat"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setGroupDmSupportStaff(
        thread: DirectThread,
        targetUserId: String,
        promote: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Group Support Staff is only available in group DMs." }
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Select another group member." }
            require(targetUserId != thread.effectiveGroupAdminId()) { "The Group DM Administrator already has full control." }
            val nextSupport = thread.groupSupportIds.toMutableSet().apply {
                if (promote) add(targetUserId) else remove(targetUserId)
            }.toList()
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "groupSupportIds" to nextSupport,
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

    suspend fun setGroupDmPttAllowed(
        thread: DirectThread,
        targetUserId: String,
        allowed: Boolean
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "PTT speaker selection is only available in group DMs." }
            require(targetUserId.isNotBlank()) { "Select a group member." }
            val nextAllowed = thread.pttAllowedUserIds.toMutableSet().apply {
                if (allowed) add(targetUserId) else remove(targetUserId)
            }.toList()
            val updates = mutableMapOf<String, Any>(
                "pttAllowedUserIds" to nextAllowed,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!allowed && thread.pttEnabledUserIds.contains(targetUserId)) {
                updates["pttEnabledUserIds"] = thread.pttEnabledUserIds.filter { it != targetUserId }
            }
            db.collection("directThreads").document(thread.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setGroupDmAdminTalking(
        threadId: String,
        talking: Boolean
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/$threadId"
        return try {
            db.collection("directThreads").document(threadId).update(
                mapOf(
                    "groupAdminTalking" to talking,
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

    /**
     * Group DM Admin (or VOX Staff) toggle to disable or re-enable Anonymous Mode ("Hide Name")
     * inside a Group DM. When disabled, members cannot hide their name on sent messages.
     */
    suspend fun setGroupDmAnonymousModeDisabled(
        thread: DirectThread,
        disabled: Boolean,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Anonymous Mode settings are only available in Group DMs." }
            val safeActor = actorName.trim().ifBlank { "Group Admin" }.take(40)
            val statusSummary = if (disabled) {
                "🔒 $safeActor disabled Anonymous Mode in this Group DM."
            } else {
                "🔓 $safeActor enabled Anonymous Mode in this Group DM."
            }.take(300)
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "anonymousModeDisabled" to disabled,
                    "lastMessageText" to statusSummary,
                    "lastMessageSenderId" to uid,
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

    /**
     * Group DM Admin (or VOX Staff) Urgent Alert: posts a high-visibility urgent alert card
     * inside the Group DM and updates the thread's alert fields so every member's phone receives
     * a high-priority ring/vibrate notification + foreground urgent alert dialog.
     */
    suspend fun sendGroupDmUrgentAlert(
        thread: DirectThread,
        senderName: String,
        senderTier: StaffTier,
        title: String,
        message: String,
        displayedSenderTier: StaffTier = senderTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            require(thread.isGroup) { "Urgent Group Alerts are only available in Group DMs." }
            require(!thread.groupDisabled || senderTier.isStaff()) {
                "This Group DM is currently disabled."
            }
            val cleanTitle = title.trim().ifBlank { "Urgent Group Alert" }.take(80)
            val cleanMessage = message.trim().take(300)
            require(cleanMessage.isNotEmpty()) { "Enter an urgent alert message to send to the group." }

            val safeSender = senderName.trim().ifBlank { "Group Admin" }.take(40)
            val now = System.currentTimeMillis()
            val unreadRecipients = thread.participantIds.filter { memberId ->
                memberId != uid && !thread.isBannedInGroup(memberId)
            }
            val previewText = "🚨 $cleanTitle: $cleanMessage".take(300)

            val msgRef = db.collection("directThreads").document(thread.id)
                .collection("messages").document()
            val msgPayload = mutableMapOf<String, Any>(
                "threadId" to thread.id,
                "participantIds" to thread.participantIds,
                "senderId" to uid,
                "senderName" to safeSender,
                "senderTier" to displayedSenderTier.name,
                "text" to "$cleanTitle\n$cleanMessage".take(500),
                "messageType" to DirectMessage.TYPE_GROUP_URGENT_ALERT,
                "hideSenderName" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (thread.companyOwnerId.isNotBlank()) {
                msgPayload["companyOwnerId"] = thread.companyOwnerId
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                db.collection("directThreads").document(thread.id),
                mapOf(
                    "lastGroupAlertTitle" to cleanTitle,
                    "lastGroupAlertMessage" to cleanMessage,
                    "lastGroupAlertSenderId" to uid,
                    "lastGroupAlertSenderName" to safeSender,
                    "lastGroupAlertAtMillis" to now,
                    "lastMessageText" to previewText,
                    "lastMessageSenderId" to uid,
                    "unreadByUserIds" to unreadRecipients,
                    "updatedAt" to FieldValue.serverTimestamp()
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
     * Admin-only Custom Identity Disguise: lets the App Admin appear under any display name,
     * any hex username color, and any membership tier/role across profiles, rooms, and chats.
     * Passing empty strings clears the disguise and restores normal Admin identity.
     */
    suspend fun setAdminCustomIdentity(
        customDisplayName: String,
        customNameColorHex: String,
        customMembershipTier: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = customDisplayName.trim().take(UsernamePolicy.MAX_LENGTH)
            val cleanColor = customNameColorHex.trim().uppercase().let { raw ->
                if (raw.isBlank()) ""
                else {
                    val withHash = if (raw.startsWith("#")) raw else "#$raw"
                    if (Regex("^#[0-9A-F]{6}$").matches(withHash)) withHash else ""
                }
            }
            val cleanTier = customMembershipTier.trim().uppercase().let { t ->
                if (t in listOf("", "FREE", "PRO", "VIP_PRO", "BUSINESS", "SUPPORT", "ADMIN")) t else ""
            }
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val realDisplayName = userSnap.getString("displayName").orEmpty().ifBlank { "Admin" }
            val realHandle = userSnap.getString("handle").orEmpty().ifBlank { "admin" }
            val realAvatarHex = userSnap.getString("avatarColorHex").orEmpty().ifBlank { "#0088FF" }

            userRef.update(
                mapOf(
                    "customDisplayName" to cleanName,
                    "customNameColorHex" to cleanColor,
                    "customMembershipTier" to cleanTier,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val dirRef = db.collection("userDirectory").document(uid)
            val dirSnap = dirRef.get().await()
            val shownName = cleanName.ifBlank { realDisplayName }
            if (dirSnap.exists()) {
                dirRef.update(
                    mapOf(
                        "displayName" to shownName,
                        "customDisplayName" to cleanName,
                        "customNameColorHex" to cleanColor,
                        "customMembershipTier" to cleanTier,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                dirRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to shownName,
                        "handle" to realHandle,
                        "avatarColorHex" to realAvatarHex,
                        "staffTier" to StaffTier.ADMIN.name,
                        "customDisplayName" to cleanName,
                        "customNameColorHex" to cleanColor,
                        "customMembershipTier" to cleanTier,
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

    /**
     * VIP Pro Custom Username Color: allows active VIP Pro (and Business / Staff) members
     * to choose a custom username color from the approved palette (strictly excluding Red,
     * Green, and Purple which are reserved for Admin, Support, and Room Leaders).
     * Passing an empty string resets to the user's default role/membership color.
     */
    suspend fun setVipProNameColor(
        colorHex: String,
        currentStaffTier: StaffTier = StaffTier.USER
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanColor = colorHex.trim().uppercase()
            require(isAllowedVipProNameColorHex(cleanColor)) {
                "VIP Pro username colors cannot be Red, Green, or Purple."
            }
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val profile = userSnap.toObject(UserProfile::class.java)
            val eligible = currentStaffTier.isStaff() ||
                (profile?.canChooseVipProNameColor(currentStaffTier) == true)
            require(eligible) {
                "Upgrade to VIP Pro to choose a custom username color."
            }

            userRef.update(
                mapOf(
                    "customNameColorHex" to cleanColor,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val dirRef = db.collection("userDirectory").document(uid)
            val dirSnap = dirRef.get().await()
            if (dirSnap.exists()) {
                dirRef.update(
                    mapOf(
                        "customNameColorHex" to cleanColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val realDisplayName = userSnap.getString("displayName").orEmpty().ifBlank { "Member" }
                val realHandle = userSnap.getString("handle").orEmpty().ifBlank { "member" }
                val realAvatarHex = userSnap.getString("avatarColorHex").orEmpty().ifBlank { "#0088FF" }
                val effectiveTier = profile?.effectiveProTier(currentStaffTier) ?: ProTier.FREE
                dirRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to realDisplayName,
                        "handle" to realHandle,
                        "avatarColorHex" to realAvatarHex,
                        "staffTier" to currentStaffTier.name,
                        "proMember" to (profile?.proMember == true),
                        "proExpiresAtMillis" to (profile?.proExpiresAtMillis ?: 0L),
                        "proLifetime" to (profile?.proLifetime == true),
                        "proTier" to effectiveTier.name,
                        "customNameColorHex" to cleanColor,
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

    // =========================================================================
    // ADMIN & SUPPORT REFUNDS (AUTO-UNSUBSCRIBE + SUPPORT REPORT TO APP ADMINS)
    // =========================================================================

    fun observeRefundReports(): Flow<List<RefundReport>> = flow {
        val path = "refundReports"
        emitAll(
            db.collection("refundReports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap -> snap.toObjects(RefundReport::class.java) }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAllCreatorSubscriptionsForStaff(): Flow<List<CreatorSubscription>> = flow {
        val path = "creatorSubscriptions"
        emitAll(
            db.collection("creatorSubscriptions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap -> snap.toObjects(CreatorSubscription::class.java) }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAdminNotifications(): Flow<List<AdminNotification>> = flow {
        val path = "adminNotifications"
        emitAll(
            db.collection("adminNotifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap -> snap.toObjects(AdminNotification::class.java) }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun issueRefund(
        targetUserId: String,
        targetName: String,
        refundType: RefundType,
        itemLabel: String,
        amountCents: Long,
        transactionId: String = "",
        subscriptionId: String = "",
        dmPassId: String = "",
        reason: String,
        issuerName: String,
        issuerTier: StaffTier
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val reportRef = db.collection("refundReports").document()
        val path = reportRef.path
        return try {
            require(issuerTier == StaffTier.ADMIN || issuerTier == StaffTier.SUPPORT) {
                "Only App Admins and Support Staff can issue refunds."
            }
            val cleanReason = reason.trim().take(300)
            require(cleanReason.isNotEmpty()) { "Please enter a reason for the refund." }
            require(targetUserId.isNotBlank()) { "Select a user to refund." }
            val safeTargetName = targetName.trim().ifBlank { "Member" }.take(40)
            val safeIssuerName = issuerName.trim().ifBlank { "Staff" }.take(40)
            val safeItemLabel = itemLabel.trim().ifBlank { refundType.label }.take(100)
            val safeAmountCents = amountCents.coerceIn(0L, 100_000_000L)
            val nowMs = System.currentTimeMillis()
            var membershipRevoked = false
            var resolvedSubId = subscriptionId.trim().take(260)

            // 1. Auto-unsubscribe / revoke refunded membership
            when (refundType) {
                RefundType.VOX_PRO -> {
                    val userRef = db.collection("users").document(targetUserId)
                    if (userRef.get().await().exists()) {
                        userRef.update(
                            mapOf(
                                "proMember" to false,
                                "proTier" to ProTier.FREE.name,
                                "proExpiresAtMillis" to 0L,
                                "proLifetime" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        membershipRevoked = true
                    }
                    runCatching {
                        val dirRef = db.collection("userDirectory").document(targetUserId)
                        if (dirRef.get().await().exists()) {
                            dirRef.update(
                                mapOf(
                                    "proMember" to false,
                                    "proTier" to ProTier.FREE.name,
                                    "proExpiresAtMillis" to 0L,
                                    "proLifetime" to false,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
                RefundType.CREATOR_SUBSCRIPTION -> {
                    if (resolvedSubId.isNotBlank()) {
                        val subRef = db.collection("creatorSubscriptions").document(resolvedSubId)
                        if (subRef.get().await().exists()) {
                            subRef.update(
                                mapOf(
                                    "active" to false,
                                    "expiresAtMillis" to 0L,
                                    "refunded" to true,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            membershipRevoked = true
                        }
                    } else {
                        // Find and revoke all active creator subscriptions for this user
                        val activeSubs = db.collection("creatorSubscriptions")
                            .whereEqualTo("subscriberId", targetUserId)
                            .get().await().documents
                        activeSubs.forEach { doc ->
                            val isActive = doc.getBoolean("active") == true
                            if (isActive) {
                                doc.reference.update(
                                    mapOf(
                                        "active" to false,
                                        "expiresAtMillis" to 0L,
                                        "refunded" to true,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                                if (resolvedSubId.isBlank()) resolvedSubId = doc.id
                                membershipRevoked = true
                            }
                        }
                    }
                }
                RefundType.PAID_DM -> {
                    val targetPassId = dmPassId.trim().take(260)
                    if (targetPassId.isNotBlank()) {
                        val passRef = db.collection("dmAccessPasses").document(targetPassId)
                        if (passRef.get().await().exists()) {
                            passRef.update(
                                mapOf(
                                    "active" to false,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            resolvedSubId = targetPassId
                            membershipRevoked = true
                        }
                    } else {
                        val passes = db.collection("dmAccessPasses")
                            .whereEqualTo("senderId", targetUserId)
                            .get().await().documents
                        passes.forEach { doc ->
                            if (doc.getBoolean("active") == true) {
                                doc.reference.update(
                                    mapOf(
                                        "active" to false,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                                if (resolvedSubId.isBlank()) resolvedSubId = doc.id
                                membershipRevoked = true
                            }
                        }
                    }
                }
                RefundType.POINTS_TRANSACTION -> {
                    membershipRevoked = false
                }
            }

            // 2. Mark linked transaction as refunded if provided
            val cleanTxId = transactionId.trim().take(128)
            if (cleanTxId.isNotBlank()) {
                runCatching {
                    val txRef = db.collection("pointTransactions").document(cleanTxId)
                    if (txRef.get().await().exists()) {
                        txRef.update(
                            mapOf(
                                "refunded" to true,
                                "refundedAtMillis" to nowMs,
                                "refundedById" to uid,
                                "refundedByName" to safeIssuerName,
                                "refundReason" to cleanReason
                            )
                        ).await()
                    }
                }
            }

            // 3. Write Refund Report record (and alert App Admins if issued by Support Staff)
            val isSupportIssuer = issuerTier == StaffTier.SUPPORT
            val batch = db.batch()
            batch.set(
                reportRef,
                mapOf(
                    "targetUserId" to targetUserId,
                    "targetName" to safeTargetName,
                    "refundType" to refundType.name,
                    "itemLabel" to safeItemLabel,
                    "amountCents" to safeAmountCents,
                    "transactionId" to cleanTxId,
                    "subscriptionId" to resolvedSubId,
                    "membershipRevoked" to membershipRevoked,
                    "reason" to cleanReason,
                    "issuedById" to uid,
                    "issuedByName" to safeIssuerName,
                    "issuedByTier" to issuerTier.name,
                    "reportedToAdmins" to isSupportIssuer,
                    "acknowledgedByAdmin" to !isSupportIssuer,
                    "acknowledgedByAdminName" to if (!isSupportIssuer) safeIssuerName else "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isSupportIssuer) {
                val notifRef = db.collection("adminNotifications").document()
                val amountLabel = formatUsdCents(safeAmountCents)
                val revokeText = if (membershipRevoked) " Membership was automatically unsubscribed." else ""
                batch.set(
                    notifRef,
                    mapOf(
                        "type" to AdminNotificationType.SUPPORT_REFUND_REPORT.name,
                        "title" to "Support Refund Report: $safeTargetName".take(120),
                        "message" to "$safeIssuerName (Support) refunded $safeItemLabel ($amountLabel) for $safeTargetName. Reason: $cleanReason.$revokeText".take(500),
                        "createdById" to uid,
                        "createdByName" to safeIssuerName,
                        "targetUserId" to targetUserId,
                        "targetName" to safeTargetName,
                        "referenceId" to reportRef.id,
                        "countValue" to 1,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeRefundReport(
        reportId: String,
        adminName: String
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "refundReports/$reportId"
        return try {
            val safeAdmin = adminName.trim().ifBlank { "Admin" }.take(40)
            db.collection("refundReports").document(reportId).update(
                mapOf(
                    "acknowledgedByAdmin" to true,
                    "acknowledgedByAdminName" to safeAdmin,
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

    suspend fun markAdminNotificationRead(notifId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "adminNotifications/$notifId"
        return try {
            db.collection("adminNotifications").document(notifId).update(
                mapOf(
                    "read" to true,
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

    // =========================================================================
    // APP PERK GRANTS (ADMINS & SUPPORT + 30-PERKS-IN-5-DAYS ALERT TO ADMINS)
    // =========================================================================

    fun observePerkGrants(): Flow<List<PerkGrant>> = flow {
        val path = "perkGrants"
        emitAll(
            db.collection("perkGrants")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(150)
                .snapshots()
                .map { snap -> snap.toObjects(PerkGrant::class.java) }
                .catch { error ->
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    data class PerkGrantOutcome(
        val supportTotal5d: Int = 0,
        val staffProPackages5d: Int = 0,
        val staffPoints5d: Long = 0L,
        val proAlertTriggered: Boolean = false,
        val pointsAlertTriggered: Boolean = false
    )

    private suspend fun logSupportSelfPerkUsage(
        staffName: String,
        perkType: AppPerkType,
        detail: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val safeName = staffName.trim().ifBlank { "Support" }.take(40)
        val nowMs = System.currentTimeMillis()
        runCatching {
            db.collection("perkGrants").document().set(
                mapOf(
                    "targetUserId" to uid,
                    "targetName" to safeName,
                    "perkType" to perkType.name,
                    "perkLabel" to perkType.label.take(80),
                    "perkDetail" to detail.take(120),
                    "reason" to "Support Staff free app perk usage",
                    "grantedById" to uid,
                    "grantedByName" to safeName,
                    "grantedByTier" to StaffTier.SUPPORT.name,
                    "pointsAmount" to 0L,
                    "selfUsage" to true,
                    "createdAtMillis" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun grantAppPerk(
        targetUserId: String,
        targetName: String,
        perkType: AppPerkType,
        detailValue: String = "",
        pointsAmount: Long = 5L,
        reason: String = "",
        granterName: String,
        granterTier: StaffTier
    ): Result<PerkGrantOutcome> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val grantRef = db.collection("perkGrants").document()
        val path = grantRef.path
        return try {
            require(granterTier == StaffTier.ADMIN || granterTier == StaffTier.SUPPORT) {
                "Only App Admins and Support Staff can grant app perks."
            }
            require(targetUserId.isNotBlank()) { "Select a user to receive this perk." }
            val safeTargetName = targetName.trim().ifBlank { "Member" }.take(40)
            val safeGranterName = granterName.trim().ifBlank { "Staff" }.take(40)
            val safeReason = reason.trim().take(300)
            val nowMs = System.currentTimeMillis()

            val userRef = db.collection("users").document(targetUserId)
            val userSnap = userRef.get().await()
            require(userSnap.exists()) { "Selected user profile was not found." }
            val userProfile = userSnap.toObject(UserProfile::class.java) ?: UserProfile(userId = targetUserId)

            val userUpdates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            val dirUpdates = mutableMapOf<String, Any>()
            val resolvedDetail: String
            var grantedPoints = 0L

            when (perkType) {
                AppPerkType.VOX_PRO_30D -> {
                    val baseMs = maxOf(nowMs, userProfile.proExpiresAtMillis)
                    val nextExpiry = baseMs + 30L * 24L * 60L * 60L * 1000L
                    val currentTier = userProfile.effectiveProTier()
                    val nextTier = if (currentTier.ordinal > ProTier.PRO.ordinal) currentTier else ProTier.PRO
                    userUpdates["proMember"] = true
                    userUpdates["proTier"] = nextTier.name
                    userUpdates["proExpiresAtMillis"] = nextExpiry
                    dirUpdates["proMember"] = true
                    dirUpdates["proTier"] = nextTier.name
                    dirUpdates["proExpiresAtMillis"] = nextExpiry
                    resolvedDetail = "30 days added (${nextTier.label})"
                }
                AppPerkType.VOX_PRO_LIFETIME -> {
                    val currentTier = userProfile.effectiveProTier()
                    val nextTier = if (currentTier.ordinal > ProTier.PRO.ordinal) currentTier else ProTier.PRO
                    userUpdates["proMember"] = true
                    userUpdates["proTier"] = nextTier.name
                    userUpdates["proLifetime"] = true
                    dirUpdates["proMember"] = true
                    dirUpdates["proTier"] = nextTier.name
                    dirUpdates["proLifetime"] = true
                    resolvedDetail = "Lifetime ${nextTier.label}"
                }
                AppPerkType.VIP_PRO_30D -> {
                    val baseMs = maxOf(nowMs, userProfile.proExpiresAtMillis)
                    val nextExpiry = baseMs + 30L * 24L * 60L * 60L * 1000L
                    userUpdates["proMember"] = true
                    userUpdates["proTier"] = ProTier.VIP_PRO.name
                    userUpdates["proExpiresAtMillis"] = nextExpiry
                    dirUpdates["proMember"] = true
                    dirUpdates["proTier"] = ProTier.VIP_PRO.name
                    dirUpdates["proExpiresAtMillis"] = nextExpiry
                    resolvedDetail = "30 days VIP Pro (up to 25-user Group DMs & 10 sub-accounts)"
                }
                AppPerkType.SPOTLIGHT_FRAME_24H -> {
                    val color = SpotlightFrameColor.fromHex(detailValue)
                    val untilMs = nowMs + SpotlightFrameColor.DURATION_MS
                    userUpdates["spotlightFrameUntilMillis"] = untilMs
                    userUpdates["spotlightFrameColorHex"] = color.primaryHex
                    dirUpdates["spotlightFrameUntilMillis"] = untilMs
                    dirUpdates["spotlightFrameColorHex"] = color.primaryHex
                    resolvedDetail = "${color.label} (24h)"
                }
                AppPerkType.PTT_THEME -> {
                    val theme = PttThemeStyle.fromRaw(detailValue)
                    val updatedOwned = (userProfile.ownedPttThemes + theme.id).distinct()
                    userUpdates["ownedPttThemes"] = updatedOwned
                    userUpdates["selectedPttTheme"] = theme.id
                    resolvedDetail = theme.label
                }
                AppPerkType.PTT_CHIRP -> {
                    val chirp = PttChirpStyle.fromRaw(detailValue)
                    val updatedOwned = (userProfile.ownedPttChirps + chirp.id).distinct()
                    userUpdates["ownedPttChirps"] = updatedOwned
                    userUpdates["selectedPttChirp"] = chirp.id
                    resolvedDetail = chirp.label
                }
                AppPerkType.BONUS_POINTS -> {
                    val safePts = pointsAmount.coerceIn(1L, 10_000L)
                    grantedPoints = safePts
                    val nextBalance = (userProfile.pointsBalance + safePts).coerceAtMost(1_000_000L)
                    userUpdates["pointsBalance"] = nextBalance
                    resolvedDetail = "+$safePts pts (new balance: $nextBalance pts)"
                }
                AppPerkType.PROFILE_VIEWERS_24H -> {
                    val untilMs = nowMs + ProfileViewRecord.UNLOCK_DURATION_MS
                    userUpdates["profileViewersUnlockedUntilMillis"] = untilMs
                    resolvedDetail = "Unlocked 24h"
                }
                AppPerkType.PROFILE_BOOST_24H -> {
                    val untilMs = nowMs + 24L * 60L * 60L * 1000L
                    userUpdates["profileBoostedUntilMillis"] = untilMs
                    dirUpdates["profileBoostedUntilMillis"] = untilMs
                    resolvedDetail = "Top of Discovery (24h)"
                }
                AppPerkType.POST_BOOST_24H,
                AppPerkType.REEL_BOOST_24H,
                AppPerkType.DM_BANNER_24H,
                AppPerkType.PRIORITY_CHIRP -> {
                    resolvedDetail = detailValue.ifBlank { perkType.label }
                }
            }

            userRef.update(userUpdates).await()
            if (dirUpdates.isNotEmpty()) {
                dirUpdates["updatedAt"] = FieldValue.serverTimestamp()
                runCatching {
                    val dirRef = db.collection("userDirectory").document(targetUserId)
                    if (dirRef.get().await().exists()) {
                        dirRef.update(dirUpdates).await()
                    }
                }
            }

            val isSelf = targetUserId == uid
            grantRef.set(
                mapOf(
                    "targetUserId" to targetUserId,
                    "targetName" to safeTargetName,
                    "perkType" to perkType.name,
                    "perkLabel" to perkType.label.take(80),
                    "perkDetail" to resolvedDetail.take(120),
                    "reason" to safeReason,
                    "grantedById" to uid,
                    "grantedByName" to safeGranterName,
                    "grantedByTier" to granterTier.name,
                    "pointsAmount" to grantedPoints,
                    "selfUsage" to isSelf,
                    "createdAtMillis" to nowMs,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            var support5dCount = 0
            var staffProPackages5d = 0
            var staffPoints5d = 0L
            var proAlertTriggered = false
            var pointsAlertTriggered = false

            if (granterTier == StaffTier.SUPPORT && !isSelf) {
                val cutoffMs = nowMs - PerkGrant.SUPPORT_ALERT_WINDOW_MS
                val supportGrants = db.collection("perkGrants")
                    .whereEqualTo("grantedByTier", StaffTier.SUPPORT.name)
                    .get().await().documents
                    .mapNotNull { runCatching { it.toObject(PerkGrant::class.java) }.getOrNull() }
                    .filter { !it.isSelfPerkUsage() && it.effectiveTimestampMillis() >= cutoffMs }

                support5dCount = supportGrants.size
                val myRecentGrants = supportGrants.filter { it.grantedById == uid }
                staffProPackages5d = myRecentGrants.count { it.perkTypeEnum().isProPackage() }
                staffPoints5d = myRecentGrants
                    .filter { it.perkTypeEnum() == AppPerkType.BONUS_POINTS }
                    .sumOf { it.effectivePointsAmount() }

                // Alert 1: 5+ Pro packages given in a 5-day period
                if (perkType.isProPackage() && staffProPackages5d >= PerkGrant.SUPPORT_PRO_PACKAGE_THRESHOLD) {
                    proAlertTriggered = true
                    db.collection("adminNotifications").document().set(
                        mapOf(
                            "type" to AdminNotificationType.SUPPORT_PRO_PACKAGES_5_IN_5D.name,
                            "title" to "Support Pro Package Alert: $safeGranterName ($staffProPackages5d in 5d)".take(120),
                            "message" to "$safeGranterName has given out $staffProPackages5d Pro packages in the last 5 days (alert threshold: ${PerkGrant.SUPPORT_PRO_PACKAGE_THRESHOLD}). Latest: ${perkType.label} to $safeTargetName.".take(500),
                            "createdById" to uid,
                            "createdByName" to safeGranterName,
                            "targetUserId" to targetUserId,
                            "targetName" to safeTargetName,
                            "referenceId" to grantRef.id,
                            "countValue" to staffProPackages5d.coerceAtMost(100_000),
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Alert 2: More than 50 points given in a 5-day period
                if (perkType == AppPerkType.BONUS_POINTS && staffPoints5d > PerkGrant.SUPPORT_POINTS_THRESHOLD) {
                    pointsAlertTriggered = true
                    db.collection("adminNotifications").document().set(
                        mapOf(
                            "type" to AdminNotificationType.SUPPORT_POINTS_50_IN_5D.name,
                            "title" to "Support Points Alert: $safeGranterName ($staffPoints5d pts in 5d)".take(120),
                            "message" to "$safeGranterName has given out $staffPoints5d points in the last 5 days (alert threshold: over ${PerkGrant.SUPPORT_POINTS_THRESHOLD} pts). Latest: +$grantedPoints pts to $safeTargetName.".take(500),
                            "createdById" to uid,
                            "createdByName" to safeGranterName,
                            "targetUserId" to targetUserId,
                            "targetName" to safeTargetName,
                            "referenceId" to grantRef.id,
                            "countValue" to staffPoints5d.coerceAtMost(100_000L).toInt(),
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                // Alert 3: Total 30+ perks in 5 days across Support
                if (support5dCount >= PerkGrant.SUPPORT_ALERT_THRESHOLD &&
                    (support5dCount == PerkGrant.SUPPORT_ALERT_THRESHOLD || support5dCount % 5 == 0)
                ) {
                    db.collection("adminNotifications").document().set(
                        mapOf(
                            "type" to AdminNotificationType.SUPPORT_PERK_THRESHOLD_30_IN_5D.name,
                            "title" to "Support Perk Alert: $support5dCount Perks in 5 Days".take(120),
                            "message" to "Support Staff have granted $support5dCount app perks in total over the last 5 days (threshold: ${PerkGrant.SUPPORT_ALERT_THRESHOLD}). Latest: $safeGranterName granted ${perkType.label} to $safeTargetName.".take(500),
                            "createdById" to uid,
                            "createdByName" to safeGranterName,
                            "targetUserId" to targetUserId,
                            "targetName" to safeTargetName,
                            "referenceId" to grantRef.id,
                            "countValue" to support5dCount,
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(
                PerkGrantOutcome(
                    supportTotal5d = support5dCount,
                    staffProPackages5d = staffProPackages5d,
                    staffPoints5d = staffPoints5d,
                    proAlertTriggered = proAlertTriggered,
                    pointsAlertTriggered = pointsAlertTriggered
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 26-28. VIP PRO COMPANY SUB-ACCOUNTS, SUPERVISORS, WORK ORDERS, EMPLOYEE REQUESTS & DND ---

    private fun generateSubAccountCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder("EMP_")
        repeat(6) {
            sb.append(alphabet[(Math.random() * alphabet.length).toInt().coerceIn(0, alphabet.lastIndex)])
        }
        return sb.toString()
    }

    suspend fun ensureOrCreateCompanyGroupThread(
        ownerProfile: UserProfile,
        ownerTier: StaffTier,
        companyName: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val threadId = "company_$uid"
        val path = "directThreads/$threadId"
        return try {
            val ref = db.collection("directThreads").document(threadId)
            val snap = ref.get().await()
            val cleanCompany = companyName.trim().ifBlank { "${ownerProfile.effectiveDisplayName()} HQ" }.take(60)
            if (snap.exists()) {
                val existingName = snap.getString("groupName").orEmpty()
                if (existingName.isBlank() && cleanCompany.isNotBlank()) {
                    ref.update(
                        mapOf(
                            "groupName" to cleanCompany,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return Result.success(threadId)
            }
            val ownerName = ownerProfile.effectiveDisplayName().ifBlank { "Owner" }.take(40)
            val ownerColor = ownerProfile.avatarColorHex.ifBlank { "#0088FF" }
            ref.set(
                mapOf(
                    "participantIds" to listOf(uid),
                    "isGroup" to true,
                    "groupName" to cleanCompany,
                    "groupAdminId" to uid,
                    "companyOwnerId" to uid,
                    "groupSupervisorIds" to emptyList<String>(),
                    "groupSupportIds" to emptyList<String>(),
                    "participantNamesById" to mapOf(uid to ownerName),
                    "userAId" to uid,
                    "userAName" to ownerName,
                    "userAColorHex" to ownerColor,
                    "userATier" to ownerTier.name,
                    "userBId" to uid,
                    "userBName" to ownerName,
                    "userBColorHex" to ownerColor,
                    "userBTier" to ownerTier.name,
                    "lastMessageText" to "Company team workspace created",
                    "lastMessageSenderId" to uid,
                    "unreadByUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createCompanySubAccount(
        ownerProfile: UserProfile,
        ownerTier: StaffTier,
        companyName: String,
        subName: String,
        roleTitle: String
    ): Result<CompanySubAccount> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!ownerProfile.canManageCompanySubAccounts(ownerTier)) {
            return Result.failure(IllegalStateException("VIP Pro or Business Account is required to create employee sub-names."))
        }
        val trimmedSub = subName.trim()
        val allowSymbols = ownerTier == StaffTier.ADMIN
        UsernamePolicy.validate(trimmedSub, allowSymbols = allowSymbols)?.let { err ->
            return Result.failure(IllegalArgumentException(err))
        }
        val cleanSub = trimmedSub.take(UsernamePolicy.MAX_LENGTH)
        if (cleanSub.length < 2) {
            return Result.failure(IllegalArgumentException("Employee sub-name must be at least 2 characters."))
        }
        val cleanCompany = companyName.trim().ifBlank { "${ownerProfile.effectiveDisplayName()} Team" }.take(60)
        val cleanRole = roleTitle.trim().take(60)
        val maxAllowedSubs = ownerProfile.maxCompanySubAccounts(ownerTier)
        val existingActiveCount = runCatching {
            db.collection("companySubAccounts")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .toObjects(CompanySubAccount::class.java)
                .count { it.active && !it.banned }
        }.getOrDefault(0)
        if (existingActiveCount >= maxAllowedSubs) {
            val msg = if (maxAllowedSubs < 50) {
                "BUSINESS_UPGRADE_REQUIRED: VIP Pro includes up to 10 sub-accounts. Upgrade to a Business Account (\$99.99/mo) for up to 50 sub-accounts."
            } else {
                "Maximum of 50 active sub-accounts reached for Business Account."
            }
            return Result.failure(IllegalStateException(msg))
        }
        val threadRes = ensureOrCreateCompanyGroupThread(ownerProfile, ownerTier, cleanCompany)
        val threadId = threadRes.getOrElse { return Result.failure(it) }
        val ownerName = ownerProfile.effectiveDisplayName().ifBlank { "Owner" }.take(40)

        // Ensure owner has a CompanyCode so every sub-account is bound to CompanyCode + Unique 6-digit PIN
        val resolvedCompanyCode = runCatching {
            val existingCodes = db.collection("companyCodes")
                .whereEqualTo("ownerId", uid)
                .get()
                .await()
                .toObjects(CompanyCode::class.java)
            existingCodes.firstOrNull { it.active }?.companyCode?.ifBlank { null }
                ?: existingCodes.firstOrNull()?.companyCode?.ifBlank { null }
        }.getOrNull() ?: run {
            val prefix = cleanCompany.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(6).ifBlank { "TEAM" }
            val autoCode = "${prefix}${(100..999).random()}".take(12)
            val autoPin = (100000..999999).random().toString()
            saveCompanyCodeAndPin(
                ownerProfile = ownerProfile,
                ownerTier = ownerTier,
                ownerEmail = "",
                companyName = cleanCompany,
                companyCodeInput = autoCode,
                pinInput = autoPin
            )
            autoCode
        }

        // Generate a unique 6-digit employee PIN and deterministic doc ID ("PIN_<CODE>_<PIN>")
        var uniquePin = (100000..999999).random().toString()
        var subDocId = "PIN_${resolvedCompanyCode}_${uniquePin}"
        for (attempt in 0 until 5) {
            val exists = runCatching {
                db.collection("companySubAccounts").document(subDocId).get().await().exists()
            }.getOrDefault(false)
            if (!exists) break
            uniquePin = (100000..999999).random().toString()
            subDocId = "PIN_${resolvedCompanyCode}_${uniquePin}"
        }
        val path = "companySubAccounts/$subDocId"
        return try {
            val threadSnap = db.collection("directThreads").document(threadId).get().await()
            @Suppress("UNCHECKED_CAST")
            val supervisorUids = (threadSnap.get("groupSupervisorIds") as? List<String>) ?: emptyList()
            db.collection("companySubAccounts").document(subDocId).set(
                mapOf(
                    "ownerId" to uid,
                    "ownerName" to ownerName,
                    "companyName" to cleanCompany,
                    "companyThreadId" to threadId,
                    "subName" to cleanSub,
                    "roleTitle" to cleanRole,
                    "accessCode" to subDocId,
                    "companyCode" to resolvedCompanyCode,
                    "joinPin" to uniquePin,
                    "claimedUserId" to "",
                    "claimedEmail" to "",
                    "supervisor" to false,
                    "supervisorUids" to supervisorUids,
                    "doNotDisturb" to false,
                    "active" to true,
                    "banned" to false,
                    "suspendedUntilMillis" to 0L,
                    "moderationReason" to "",
                    "lastBuzzAtMillis" to 0L,
                    "lastBuzzMessage" to "",
                    "lastBuzzByName" to "",
                    "pttEnabled" to true,
                    "messagingEnabled" to true,
                    "micEnabled" to true,
                    "videoEnabled" to true,
                    "claimedAtMillis" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                CompanySubAccount(
                    id = subDocId,
                    ownerId = uid,
                    ownerName = ownerName,
                    companyName = cleanCompany,
                    companyThreadId = threadId,
                    subName = cleanSub,
                    roleTitle = cleanRole,
                    accessCode = subDocId,
                    companyCode = resolvedCompanyCode,
                    joinPin = uniquePin,
                    supervisorUids = supervisorUids,
                    active = true,
                    pttEnabled = true,
                    messagingEnabled = true,
                    micEnabled = true,
                    videoEnabled = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun claimCompanySubAccount(
        subNameInput: String,
        accessCodeInput: String,
        myEmail: String
    ): Result<CompanySubAccount> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val code = com.example.auth.SubAccountLoginPrefs.normalizeCode(accessCodeInput)
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a valid company access code."))
        }
        val path = "companySubAccounts/$code"
        return try {
            val subRef = db.collection("companySubAccounts").document(code)
            val snap = subRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Company access code $code was not found."))
            }
            val sub = snap.toObject(CompanySubAccount::class.java)
                ?: return Result.failure(IllegalStateException("Invalid company sub-account record."))
            if (sub.banned) {
                return Result.failure(IllegalStateException("This employee sub-account has been banned by the company owner."))
            }
            if (!sub.active) {
                return Result.failure(IllegalStateException("This employee sub-account has been deactivated by the company owner."))
            }
            if (sub.ownerId == uid) {
                return Result.failure(IllegalStateException("You are the company owner — sign in on another Google account to test an employee sub-name."))
            }
            if (sub.claimedUserId.isNotBlank() && sub.claimedUserId != uid) {
                return Result.failure(IllegalStateException("This access code is already linked to another employee."))
            }
            val cleanInputName = subNameInput.trim()
            if (cleanInputName.isNotBlank() &&
                !sub.subName.equals(cleanInputName, ignoreCase = true) &&
                !sub.subName.contains(cleanInputName, ignoreCase = true)
            ) {
                return Result.failure(IllegalStateException("Sub-name does not match code $code."))
            }
            val nowMs = System.currentTimeMillis()
            if (sub.claimedUserId.isBlank()) {
                subRef.update(
                    mapOf(
                        "claimedUserId" to uid,
                        "claimedEmail" to myEmail.trim().take(120),
                        "claimedAtMillis" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val resolvedName = sub.subName.trim().ifBlank { "Employee" }.take(40)
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toLong()
            val existingBirthYear = runCatching {
                db.collection("users").document(uid).get().await().getLong("birthYear") ?: 0L
            }.getOrDefault(0L)
            val resolvedAdultBirthYear =
                if (existingBirthYear in 1900L..(currentYear - 18L)) existingBirthYear else 1995L
            db.collection("users").document(uid).update(
                mapOf(
                    "companySubAccount" to true,
                    "parentCompanyOwnerId" to sub.ownerId,
                    "companyName" to sub.companyName.take(60),
                    "companyThreadId" to sub.companyThreadId,
                    "subAccountId" to code,
                    "displayName" to resolvedName,
                    "doNotDisturb" to sub.doNotDisturb,
                    "age13Verified" to true,
                    "adultVerified" to true,
                    "birthYear" to resolvedAdultBirthYear,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "displayName" to resolvedName,
                            "companySubAccount" to true,
                            "parentCompanyOwnerId" to sub.ownerId,
                            "doNotDisturb" to sub.doNotDisturb,
                            "proMember" to false,
                            "proLifetime" to false,
                            "proTier" to ProTier.FREE.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val threadRef = db.collection("directThreads").document(sub.companyThreadId)
            val threadSnap = threadRef.get().await()
            if (threadSnap.exists()) {
                val thread = threadSnap.toObject(DirectThread::class.java)
                if (thread != null) {
                    val updatedParticipants = (thread.participantIds + uid).distinct()
                    val updatedNames = thread.participantNamesById.toMutableMap().apply {
                        put(uid, resolvedName)
                    }
                    threadRef.update(
                        mapOf(
                            "participantIds" to updatedParticipants,
                            "participantNamesById" to updatedNames,
                            "lastMessageText" to "$resolvedName joined ${sub.companyName}",
                            "lastMessageSenderId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(sub.copy(id = code, claimedUserId = uid, claimedEmail = myEmail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setGroupDmSupervisor(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        makeSupervisor: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/${thread.id}"
        return try {
            val nextSupervisors = if (makeSupervisor) {
                (thread.groupSupervisorIds + targetUserId).distinct().take(50)
            } else {
                thread.groupSupervisorIds.filter { it != targetUserId }
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "groupSupervisorIds" to nextSupervisors,
                    "lastMessageText" to if (makeSupervisor) "$targetName was appointed Group Supervisor" else "$targetName was removed as Group Supervisor",
                    "lastMessageSenderId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (thread.companyOwnerId == uid) {
                runCatching {
                    val ownedSubs = db.collection("companySubAccounts")
                        .whereEqualTo("ownerId", uid)
                        .get().await().documents
                    for (doc in ownedSubs) {
                        val claimedUid = doc.getString("claimedUserId").orEmpty()
                        val updates = mutableMapOf<String, Any>(
                            "supervisorUids" to nextSupervisors,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (claimedUid == targetUserId) {
                            updates["supervisor"] = makeSupervisor
                        }
                        doc.reference.update(updates).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeCompanySubAccount(subAccount: CompanySubAccount): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/${subAccount.id}"
        return try {
            db.collection("companySubAccounts").document(subAccount.id).update(
                mapOf(
                    "active" to false,
                    "supervisor" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (subAccount.claimedUserId.isNotBlank() && subAccount.companyThreadId.isNotBlank()) {
                runCatching {
                    val threadRef = db.collection("directThreads").document(subAccount.companyThreadId)
                    val thread = threadRef.get().await().toObject(DirectThread::class.java)
                    if (thread != null && thread.companyOwnerId == uid) {
                        val nextMembers = thread.participantIds.filter { it != subAccount.claimedUserId }
                        val nextNames = thread.participantNamesById.filterKeys { it != subAccount.claimedUserId }
                        if (nextMembers.contains(uid)) {
                            threadRef.update(
                                mapOf(
                                    "participantIds" to nextMembers,
                                    "participantNamesById" to nextNames,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        if (thread.groupSupervisorIds.contains(subAccount.claimedUserId)) {
                            threadRef.update(
                                mapOf(
                                    "groupSupervisorIds" to thread.groupSupervisorIds.filter { it != subAccount.claimedUserId },
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setDoNotDisturb(enabled: Boolean, subAccountId: String = ""): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "doNotDisturb" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "doNotDisturb" to enabled,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            if (subAccountId.isNotBlank()) {
                runCatching {
                    db.collection("companySubAccounts").document(subAccountId).update(
                        mapOf(
                            "doNotDisturb" to enabled,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSubAccountDisplayName(
        newDisplayName: String,
        subAccountId: String,
        companyThreadId: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val trimmedName = newDisplayName.trim()
        UsernamePolicy.validate(trimmedName, allowSymbols = false)?.let { err ->
            return Result.failure(IllegalArgumentException(err))
        }
        val cleanName = trimmedName.take(UsernamePolicy.MAX_LENGTH)
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Display name must be at least 2 characters."))
        }
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "displayName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            if (subAccountId.isNotBlank()) {
                runCatching {
                    db.collection("companySubAccounts").document(subAccountId).update(
                        mapOf(
                            "subName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            if (companyThreadId.isNotBlank()) {
                runCatching {
                    val threadRef = db.collection("directThreads").document(companyThreadId)
                    val thread = threadRef.get().await().toObject(DirectThread::class.java)
                    if (thread != null && thread.participantIds.contains(uid)) {
                        val updatedNames = thread.participantNamesById.toMutableMap().apply {
                            put(uid, cleanName)
                        }
                        threadRef.update(
                            mapOf(
                                "participantNamesById" to updatedNames,
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
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeOwnedCompanySubAccounts(ownerUid: String): Flow<List<CompanySubAccount>> = flow {
        val uid = requireUserId()
        require(uid == ownerUid)
        emitAll(
            db.collection("companySubAccounts")
                .whereEqualTo("ownerId", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CompanySubAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapFirestoreError(error), OperationType.LIST, "companySubAccounts")
                    }
                    emit(emptyList())
                }
        )
    }

    fun observeSupervisedCompanySubAccounts(supervisorUid: String): Flow<List<CompanySubAccount>> = flow {
        val uid = requireUserId()
        require(uid == supervisorUid)
        emitAll(
            db.collection("companySubAccounts")
                .whereArrayContains("supervisorUids", supervisorUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CompanySubAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeMyCompanySubAccount(subAccountId: String): Flow<CompanySubAccount?> = flow {
        requireUserId()
        if (subAccountId.isBlank()) {
            emit(null)
            return@flow
        }
        emitAll(
            db.collection("companySubAccounts").document(subAccountId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(CompanySubAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { emit(null) }
        )
    }

    suspend fun startOrGetCompanySubOneOnOneDm(
        myProfile: UserProfile,
        myTier: StaffTier,
        companyOwnerId: String,
        otherUserId: String,
        otherName: String,
        otherColorHex: String = "#0088FF",
        otherTier: StaffTier = StaffTier.USER
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (companyOwnerId.isBlank() || otherUserId.isBlank() || otherUserId == uid) {
            return Result.failure(IllegalArgumentException("Choose another teammate in your company group."))
        }
        val (firstId, secondId) = if (uid < otherUserId) uid to otherUserId else otherUserId to uid
        val threadId = "c1v1_${companyOwnerId}_${firstId}_${secondId}"
        val path = "directThreads/$threadId"
        return try {
            val threadRef = db.collection("directThreads").document(threadId)
            val snap = threadRef.get().await()
            if (snap.exists()) {
                return Result.success(threadId)
            }
            val myName = myProfile.effectiveDisplayName().ifBlank { "Member" }.take(40)
            val myColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(myProfile.avatarColorHex)) myProfile.avatarColorHex else "#0088FF"
            val safeOtherName = otherName.trim().ifBlank { "Teammate" }.take(40)
            val safeOtherColor = if (Regex("^#[0-9A-Fa-f]{6}$").matches(otherColorHex)) otherColorHex else "#0088FF"
            val aIsMe = firstId == uid
            threadRef.set(
                mapOf(
                    "participantIds" to listOf(firstId, secondId),
                    "isGroup" to false,
                    "companyOwnerId" to companyOwnerId,
                    "participantNamesById" to mapOf(uid to myName, otherUserId to safeOtherName),
                    "userAId" to firstId,
                    "userAName" to if (aIsMe) myName else safeOtherName,
                    "userAColorHex" to if (aIsMe) myColor else safeOtherColor,
                    "userATier" to if (aIsMe) myTier.name else otherTier.name,
                    "userBId" to secondId,
                    "userBName" to if (aIsMe) safeOtherName else myName,
                    "userBColorHex" to if (aIsMe) safeOtherColor else myColor,
                    "userBTier" to if (aIsMe) otherTier.name else myTier.name,
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "unreadByUserIds" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeCompanySubAccountDms(ownerUid: String): Flow<List<DirectThread>> = flow {
        val uid = requireUserId()
        require(uid == ownerUid)
        emitAll(
            db.collection("directThreads")
                .whereEqualTo("companyOwnerId", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DirectThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.isGroup }
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun banCompanySubAccount(
        subAccount: CompanySubAccount,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/${subAccount.id}"
        val cleanReason = reason.trim().ifBlank { "Banned by company owner" }.take(240)
        return try {
            db.collection("companySubAccounts").document(subAccount.id).update(
                mapOf(
                    "banned" to true,
                    "active" to false,
                    "supervisor" to false,
                    "moderationReason" to cleanReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (subAccount.claimedUserId.isNotBlank() && subAccount.companyThreadId.isNotBlank()) {
                runCatching {
                    val threadRef = db.collection("directThreads").document(subAccount.companyThreadId)
                    val thread = threadRef.get().await().toObject(DirectThread::class.java)
                    if (thread != null && thread.companyOwnerId == uid) {
                        val nextBanned = (thread.bannedUserIds + subAccount.claimedUserId).distinct().take(50)
                        val nextBannedNames = thread.bannedNamesByUserId.toMutableMap().apply {
                            put(subAccount.claimedUserId, subAccount.subName.ifBlank { "Employee" })
                        }
                        val nextSupervisors = thread.groupSupervisorIds.filter { it != subAccount.claimedUserId }
                        threadRef.update(
                            mapOf(
                                "bannedUserIds" to nextBanned,
                                "bannedNamesByUserId" to nextBannedNames,
                                "groupSupervisorIds" to nextSupervisors,
                                "lastMessageText" to "${subAccount.subName} was banned by owner",
                                "lastMessageSenderId" to uid,
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
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun suspendCompanySubAccount(
        subAccount: CompanySubAccount,
        durationMillis: Long,
        reason: String
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/${subAccount.id}"
        val nowMs = System.currentTimeMillis()
        val untilMs = when {
            durationMillis <= 0L -> 0L
            durationMillis > 365L * 10L * 86_400_000L -> nowMs + 365L * 10L * 86_400_000L
            else -> nowMs + durationMillis
        }
        val cleanReason = if (untilMs == 0L) "" else reason.trim().ifBlank { "Suspended by company owner" }.take(240)
        return try {
            db.collection("companySubAccounts").document(subAccount.id).update(
                mapOf(
                    "suspendedUntilMillis" to untilMs,
                    "moderationReason" to cleanReason,
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

    suspend fun buzzCompanySubAccountPhone(
        subAccount: CompanySubAccount,
        ownerName: String,
        message: String
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/${subAccount.id}"
        val cleanMsg = message.trim().ifBlank { "Check in with management right away!" }.take(200)
        val cleanOwner = ownerName.trim().ifBlank { "Company Owner" }.take(40)
        return try {
            db.collection("companySubAccounts").document(subAccount.id).update(
                mapOf(
                    "lastBuzzAtMillis" to System.currentTimeMillis(),
                    "lastBuzzMessage" to cleanMsg,
                    "lastBuzzByName" to cleanOwner,
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

    fun observeSubAccountFileNotes(subAccountId: String): Flow<List<CompanySubAccountFileNote>> = flow {
        requireUserId()
        if (subAccountId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("companySubAccounts").document(subAccountId)
                .collection("fileNotes")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CompanySubAccountFileNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun addSubAccountFileNote(
        subAccount: CompanySubAccount,
        authorName: String,
        category: String,
        note: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanNote = note.trim().take(500)
        if (cleanNote.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a note before saving."))
        }
        val cleanCategory = category.trim().ifBlank { "NOTE" }.take(30)
        val cleanAuthor = authorName.trim().ifBlank { "Owner" }.take(40)
        val ref = db.collection("companySubAccounts").document(subAccount.id)
            .collection("fileNotes").document()
        return try {
            ref.set(
                mapOf(
                    "ownerId" to subAccount.ownerId.ifBlank { uid },
                    "subAccountId" to subAccount.id,
                    "authorId" to uid,
                    "authorName" to cleanAuthor,
                    "category" to cleanCategory,
                    "note" to cleanNote,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubAccountFileNote(
        subAccountId: String,
        noteId: String
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/$subAccountId/fileNotes/$noteId"
        return try {
            db.collection("companySubAccounts").document(subAccountId)
                .collection("fileNotes").document(noteId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun sendWorkOrder(
        companyOwnerId: String,
        threadId: String,
        supervisorUids: List<String>,
        senderName: String,
        senderRole: String,
        targetUserId: String,
        targetName: String,
        orderType: WorkOrderType,
        priority: WorkOrderPriority,
        title: String,
        body: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTitle = title.trim().take(80)
        val cleanBody = body.trim().take(500)
        if (cleanTitle.isBlank() || cleanBody.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter both a title and details for the ${orderType.label.lowercase()}."))
        }
        val ref = db.collection("workOrders").document()
        val path = "workOrders/${ref.id}"
        return try {
            ref.set(
                mapOf(
                    "companyOwnerId" to companyOwnerId,
                    "threadId" to threadId,
                    "supervisorUids" to supervisorUids.distinct().take(50),
                    "senderId" to uid,
                    "senderName" to senderName.trim().ifBlank { "Supervisor" }.take(40),
                    "senderRole" to senderRole.trim().ifBlank { "Supervisor" }.take(30),
                    "targetUserId" to targetUserId,
                    "targetName" to targetName.trim().ifBlank { "Employee" }.take(40),
                    "orderType" to orderType.name,
                    "priority" to priority.name,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "status" to WorkOrderStatus.OPEN.name,
                    "acknowledged" to false,
                    "acknowledgedAtMillis" to 0L,
                    "completedAtMillis" to 0L,
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

    fun observeIncomingWorkOrders(userId: String): Flow<List<WorkOrder>> = flow {
        val uid = requireUserId()
        require(uid == userId)
        emitAll(
            db.collection("workOrders")
                .whereEqualTo("targetUserId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WorkOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeCompanyWorkOrders(ownerUid: String): Flow<List<WorkOrder>> = flow {
        val uid = requireUserId()
        require(uid == ownerUid)
        emitAll(
            db.collection("workOrders")
                .whereEqualTo("companyOwnerId", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WorkOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeSupervisedWorkOrders(supervisorUid: String): Flow<List<WorkOrder>> = flow {
        val uid = requireUserId()
        require(uid == supervisorUid)
        emitAll(
            db.collection("workOrders")
                .whereArrayContains("supervisorUids", supervisorUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WorkOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun updateWorkOrderStatus(orderId: String, status: WorkOrderStatus): Result<Unit> {
        val path = "workOrders/$orderId"
        return try {
            val nowMs = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "acknowledged" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (status == WorkOrderStatus.ACKNOWLEDGED) {
                updates["acknowledgedAtMillis"] = nowMs
            } else if (status == WorkOrderStatus.COMPLETED) {
                updates["acknowledgedAtMillis"] = nowMs
                updates["completedAtMillis"] = nowMs
            }
            db.collection("workOrders").document(orderId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitEmployeeRequest(
        companyOwnerId: String,
        threadId: String,
        supervisorUids: List<String>,
        requesterName: String,
        category: EmployeeRequestCategory,
        subject: String,
        details: String,
        urgent: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanSubject = subject.trim().take(80)
        val cleanDetails = details.trim().take(500)
        if (cleanSubject.isBlank() || cleanDetails.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter both a subject and request details."))
        }
        val ref = db.collection("employeeRequests").document()
        val path = "employeeRequests/${ref.id}"
        return try {
            ref.set(
                mapOf(
                    "companyOwnerId" to companyOwnerId,
                    "threadId" to threadId,
                    "supervisorUids" to supervisorUids.distinct().take(50),
                    "requesterId" to uid,
                    "requesterName" to requesterName.trim().ifBlank { "Employee" }.take(40),
                    "category" to category.name,
                    "subject" to cleanSubject,
                    "details" to cleanDetails,
                    "urgent" to urgent,
                    "status" to EmployeeRequestStatus.PENDING.name,
                    "reviewedById" to "",
                    "reviewedByName" to "",
                    "reviewerNote" to "",
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

    fun observeMyEmployeeRequests(userId: String): Flow<List<EmployeeRequest>> = flow {
        val uid = requireUserId()
        require(uid == userId)
        emitAll(
            db.collection("employeeRequests")
                .whereEqualTo("requesterId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmployeeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeCompanyEmployeeRequests(ownerUid: String): Flow<List<EmployeeRequest>> = flow {
        val uid = requireUserId()
        require(uid == ownerUid)
        emitAll(
            db.collection("employeeRequests")
                .whereEqualTo("companyOwnerId", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmployeeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    fun observeSupervisedEmployeeRequests(supervisorUid: String): Flow<List<EmployeeRequest>> = flow {
        val uid = requireUserId()
        require(uid == supervisorUid)
        emitAll(
            db.collection("employeeRequests")
                .whereArrayContains("supervisorUids", supervisorUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmployeeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun reviewEmployeeRequest(
        requestId: String,
        status: EmployeeRequestStatus,
        reviewerName: String,
        note: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "employeeRequests/$requestId"
        return try {
            val updates = if (status == EmployeeRequestStatus.CANCELLED) {
                mapOf(
                    "status" to EmployeeRequestStatus.CANCELLED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "status" to status.name,
                    "reviewedById" to uid,
                    "reviewedByName" to reviewerName.trim().ifBlank { "Supervisor" }.take(40),
                    "reviewerNote" to note.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("employeeRequests").document(requestId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- COMPANY CODE + PIN (Business Account & Employee Sub-Account Login) ---

    fun observeMyCompanyCodes(ownerUid: String): Flow<List<CompanyCode>> = flow {
        val uid = requireUserId()
        require(uid == ownerUid)
        emitAll(
            db.collection("companyCodes")
                .whereEqualTo("ownerId", ownerUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CompanyCode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.active }
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun saveCompanyCodeAndPin(
        ownerProfile: UserProfile,
        ownerTier: StaffTier,
        ownerEmail: String,
        companyName: String,
        companyCodeInput: String,
        pinInput: String
    ): Result<CompanyCode> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!ownerProfile.canManageCompanySubAccounts(ownerTier)) {
            return Result.failure(IllegalStateException("Business Account or VIP Pro membership is required to create a Company Code & PIN."))
        }
        val cleanCode = com.example.auth.SubAccountLoginPrefs.normalizeCompanyCode(companyCodeInput)
        val cleanPin = com.example.auth.SubAccountLoginPrefs.normalizePin(pinInput)
        if (cleanCode.length < 4) {
            return Result.failure(IllegalArgumentException("Company Code must be at least 4 letters/numbers (e.g. ACME2026)."))
        }
        if (cleanPin.length < 4) {
            return Result.failure(IllegalArgumentException("Company PIN must be at least 4 characters/digits (e.g. 4829)."))
        }
        val safeCompany = companyName.trim().ifBlank { "${ownerProfile.effectiveDisplayName()} Company" }.take(60)
        val maxSubs = ownerProfile.maxCompanySubAccounts(ownerTier).coerceIn(1, 50)
        val threadId = ensureOrCreateCompanyGroupThread(
            ownerProfile = ownerProfile,
            ownerTier = ownerTier,
            companyName = safeCompany
        ).getOrElse { return Result.failure(it) }

        val path = "companyCodes/$cleanCode"
        return try {
            val docRef = db.collection("companyCodes").document(cleanCode)
            val existing = docRef.get().await()
            if (existing.exists() && existing.getString("ownerId") != uid) {
                return Result.failure(IllegalStateException("Company Code \"$cleanCode\" is already in use by another company. Choose a different code."))
            }

            // Deactivate any previous active codes owned by this user if switching to a new code
            runCatching {
                val prevCodes = db.collection("companyCodes")
                    .whereEqualTo("ownerId", uid)
                    .get()
                    .await()
                prevCodes.documents.forEach { d ->
                    if (d.id != cleanCode && d.getBoolean("active") == true) {
                        d.reference.delete().await()
                    }
                }
            }

            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerProfile.effectiveDisplayName().take(40),
                "ownerEmail" to ownerEmail.trim().take(120),
                "companyName" to safeCompany,
                "companyThreadId" to threadId,
                "companyCode" to cleanCode,
                "pin" to cleanPin,
                "maxSubAccounts" to maxSubs,
                "active" to true,
                "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                CompanyCode(
                    id = cleanCode,
                    ownerId = uid,
                    ownerName = ownerProfile.effectiveDisplayName().take(40),
                    ownerEmail = ownerEmail.trim().take(120),
                    companyName = safeCompany,
                    companyThreadId = threadId,
                    companyCode = cleanCode,
                    pin = cleanPin,
                    maxSubAccounts = maxSubs,
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCompanySubAccountCapabilities(
        subAccount: CompanySubAccount,
        pttEnabled: Boolean,
        messagingEnabled: Boolean,
        micEnabled: Boolean,
        videoEnabled: Boolean
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "companySubAccounts/${subAccount.id}"
        return try {
            db.collection("companySubAccounts").document(subAccount.id).update(
                mapOf(
                    "pttEnabled" to pttEnabled,
                    "messagingEnabled" to messagingEnabled,
                    "micEnabled" to micEnabled,
                    "videoEnabled" to videoEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if ((!pttEnabled || !micEnabled) &&
                subAccount.claimedUserId.isNotBlank() &&
                subAccount.companyThreadId.isNotBlank()
            ) {
                runCatching {
                    val threadRef = db.collection("directThreads").document(subAccount.companyThreadId)
                    val thread = threadRef.get().await().toObject(DirectThread::class.java)
                    if (thread != null && thread.pttEnabledUserIds.contains(subAccount.claimedUserId)) {
                        threadRef.update(
                            mapOf(
                                "pttEnabledUserIds" to thread.pttEnabledUserIds.filter { it != subAccount.claimedUserId },
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
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun requestBusinessStaffHelp(
        threadId: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/$threadId"
        val cleanReason = reason.trim().ifBlank { "Group member requested live VOX Admin or Support assistance." }.take(240)
        return try {
            db.collection("directThreads").document(threadId).update(
                mapOf(
                    "staffHelpRequested" to true,
                    "staffHelpRequestedAtMillis" to System.currentTimeMillis(),
                    "staffHelpReason" to cleanReason,
                    "staffHelpJoinedIds" to emptyList<String>(),
                    "lastMessageText" to "Requested VOX Staff Help: ${cleanReason.take(120)}",
                    "lastMessageSenderId" to uid,
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

    suspend fun joinBusinessStaffHelp(
        thread: DirectThread,
        staffName: String,
        invisible: Boolean = false
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (invisible) {
            return Result.success(Unit)
        }
        val path = "directThreads/${thread.id}"
        val cleanName = staffName.trim().ifBlank { "VOX Staff" }.take(40)
        return try {
            val nextHelpers = (thread.staffHelpJoinedIds + uid).distinct().take(20)
            val enterMsg = if (thread.isGroup) {
                "$cleanName (VOX Staff) entered the group to help"
            } else {
                "$cleanName (VOX Staff) entered this private DM to monitor"
            }
            db.collection("directThreads").document(thread.id).update(
                mapOf(
                    "staffHelpJoinedIds" to nextHelpers,
                    "lastMessageText" to enterMsg,
                    "lastMessageSenderId" to uid,
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

    suspend fun endBusinessStaffHelp(
        threadId: String,
        actorName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "directThreads/$threadId"
        val cleanName = actorName.trim().ifBlank { "Staff" }.take(40)
        return try {
            db.collection("directThreads").document(threadId).update(
                mapOf(
                    "staffHelpRequested" to false,
                    "staffHelpReason" to "",
                    "staffHelpJoinedIds" to emptyList<String>(),
                    "reportStaffAccessOpen" to false,
                    "lastReportSummary" to "",
                    "lastMessageText" to "VOX Staff help session ended by $cleanName",
                    "lastMessageSenderId" to uid,
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

    fun observeBusinessHelpThreads(): Flow<List<DirectThread>> = flow {
        val uid = requireUserId()
        val helpFlow = db.collection("directThreads")
            .whereEqualTo("staffHelpRequested", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(DirectThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.isGroup && it.staffHelpRequested }
            }
            .catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapFirestoreError(error), OperationType.LIST, "directThreads")
                }
                emit(emptyList())
            }
        val reportFlow = db.collection("directThreads")
            .whereEqualTo("reportStaffAccessOpen", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(DirectThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.reportStaffAccessOpen }
            }
            .catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapFirestoreError(error), OperationType.LIST, "directThreads")
                }
                emit(emptyList())
            }
        emitAll(
            combine(helpFlow, reportFlow) { helpList, reportList ->
                (helpList + reportList)
                    .associateBy { it.id }
                    .values
                    .sortedByDescending { maxOf(it.staffHelpRequestedAtMillis, it.lastReportedAtMillis) }
            }
        )
    }

    /**
     * Checks if a 1:1 private DM is between an 18+ adult and an under-18 (13-17) minor.
     * When detected, opens Staff monitoring access on the thread (allowing Visible or Invisible entry)
     * and dispatches an urgent real-time Youth Safety alert to VOX Staff & Admins.
     */
    private suspend fun flagAdultMinorPrivateDmIfNeeded(
        threadId: String,
        callerUid: String,
        callerName: String,
        callerIsAdult: Boolean,
        callerIsMinor: Boolean,
        otherUserId: String,
        otherName: String
    ) {
        if (threadId.isBlank() || otherUserId.isBlank() || callerUid == otherUserId) return
        val otherDir = db.collection("userDirectory").document(otherUserId).get().await()
            .toObject(DirectoryEntry::class.java) ?: return
        if (otherDir.tierEnum().isStaff()) return
        val otherIsAdult = otherDir.isAdult18Plus()
        val otherIsMinor = otherDir.isMinor13To17()

        val adultToMinor = callerIsAdult && otherIsMinor
        val minorToAdult = callerIsMinor && otherIsAdult
        if (!adultToMinor && !minorToAdult) return

        val adultUid = if (adultToMinor) callerUid else otherUserId
        val adultName = (if (adultToMinor) callerName else otherDir.effectiveDisplayName().ifBlank { otherName }).take(40)
        val minorName = (if (adultToMinor) otherDir.effectiveDisplayName().ifBlank { otherName } else callerName).take(40)
        val nowMs = System.currentTimeMillis()
        val summary = "Youth Safety Alert: 18+ user ($adultName) private DM with under-18 user ($minorName)".take(240)

        db.collection("directThreads").document(threadId).update(
            mapOf(
                "reportStaffAccessOpen" to true,
                "lastReportedAtMillis" to nowMs,
                "lastReportSummary" to summary,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("adminNotifications").document().set(
            mapOf(
                "type" to AdminNotificationType.ADULT_TO_MINOR_DM.name,
                "title" to "Youth Safety Alert: 18+ DM to Under-18".take(120),
                "message" to "18+ user $adultName is in a private DM with under-18 user $minorName. Staff can enter this private DM Visible or Invisible from Safety Center to monitor.".take(500),
                "createdById" to callerUid,
                "createdByName" to callerName.take(40),
                "targetUserId" to adultUid,
                "targetName" to adultName,
                "referenceId" to threadId.take(260),
                "countValue" to 1,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Allows VOX Support Staff or App Admin to lift an active 5-day bounce-evasion device suspension.
     */
    suspend fun staffLiftDeviceSuspension(
        deviceHash: String,
        targetUserId: String,
        actorName: String,
        actorTier: StaffTier,
        note: String = "5-day bounce-evasion device suspension lifted by VOX Staff."
    ): Result<Unit> {
        val actorUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!actorTier.isStaff()) {
            return Result.failure(IllegalStateException("VOX Staff privileges required."))
        }
        val cleanHash = deviceHash.trim()
        if (cleanHash.length < 16) {
            return Result.failure(IllegalArgumentException("Invalid device hash."))
        }
        val safeActor = actorName.trim().ifBlank { "VOX Staff" }.take(40)
        val safeNote = note.trim().ifBlank { "5-day bounce-evasion device suspension lifted by $safeActor." }.take(240)
        return try {
            db.collection("bannedDevices").document(cleanHash).update(
                mapOf(
                    "active" to false,
                    "suspendedUntilMillis" to 0L,
                    "bounceEvasionFlag" to false,
                    "returningFlagActive" to false,
                    "staffReviewStatus" to "LIFTED",
                    "reviewedByUid" to actorUid,
                    "reviewedByName" to safeActor,
                    "reviewNote" to safeNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (targetUserId.isNotBlank()) {
                runCatching {
                    val modRef = db.collection("userModerationFiles").document(targetUserId)
                    if (modRef.get().await().exists()) {
                        modRef.update(
                            mapOf(
                                "suspendedUntilMillis" to 0L,
                                "suspensionReason" to "",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                if (actorTier == StaffTier.ADMIN) {
                    runCatching {
                        db.collection("users").document(targetUserId).update(
                            mapOf(
                                "suspendedUntilMillis" to 0L,
                                "suspensionReason" to "",
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
            handleFirestoreError(e, OperationType.UPDATE, "bannedDevices/$cleanHash")
            Result.failure(e)
        }
    }

    suspend fun staffRemoveUserFromBusinessAccount(
        businessOwnerId: String,
        targetUserId: String,
        targetName: String,
        reason: String,
        actorTier: StaffTier = StaffTier.SUPPORT
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (businessOwnerId.isBlank() || targetUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing business account or target user information."))
        }
        if (targetUserId == businessOwnerId && actorTier != StaffTier.ADMIN) {
            return Result.failure(
                IllegalStateException(
                    "Support Staff cannot remove a Business Account owner. Submit an App Admin approval request."
                )
            )
        }
        val cleanReason = reason.trim().ifBlank { "Removed by VOX Staff following a reported message." }.take(240)
        val groupThreadId = "company_$businessOwnerId"
        return try {
            // 1. Deactivate & ban any matching companySubAccount records so their unique PIN can never be reused
            val subsSnap = db.collection("companySubAccounts")
                .whereEqualTo("ownerId", businessOwnerId)
                .get()
                .await()
            val matchingSubs = subsSnap.toObjects(CompanySubAccount::class.java)
                .filter { it.claimedUserId == targetUserId }
            matchingSubs.forEach { sub ->
                db.collection("companySubAccounts").document(sub.id).update(
                    mapOf(
                        "active" to false,
                        "banned" to true,
                        "supervisor" to false,
                        "moderationReason" to cleanReason,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 2. Remove target from the Business Group DM and add to bannedUserIds
            val threadRef = db.collection("directThreads").document(groupThreadId)
            val threadSnap = runCatching { threadRef.get().await() }.getOrNull()
            val thread = threadSnap?.toObject(DirectThread::class.java)
            if (thread != null && thread.companyOwnerId == businessOwnerId) {
                val nextParticipants = thread.participantIds.filter { it != targetUserId }
                val nextNames = thread.participantNamesById.filterKeys { it != targetUserId }
                val nextSupervisors = thread.groupSupervisorIds.filter { it != targetUserId }
                val nextBanned = (thread.bannedUserIds + targetUserId).distinct().take(50)
                val nextBannedNames = thread.bannedNamesByUserId.toMutableMap().apply {
                    put(targetUserId, targetName.trim().ifBlank { "Employee" }.take(40))
                }
                if (nextParticipants.contains(businessOwnerId)) {
                    threadRef.update(
                        mapOf(
                            "participantIds" to nextParticipants,
                            "participantNamesById" to nextNames,
                            "groupSupervisorIds" to nextSupervisors,
                            "bannedUserIds" to nextBanned,
                            "bannedNamesByUserId" to nextBannedNames,
                            "lastMessageText" to "${targetName.ifBlank { "User" }} was removed by VOX Staff",
                            "lastMessageSenderId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "directThreads/$groupThreadId")
            Result.failure(e)
        }
    }

    suspend fun adminBanReportedUserDevice(
        targetUserId: String,
        targetName: String,
        reason: String,
        adminName: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        if (targetUserId.isBlank() || targetUserId == uid) {
            return Result.failure(IllegalArgumentException("Cannot ban your own admin device."))
        }
        val cleanReason = reason.trim().ifBlank { "Device banned by App Admin following reported message." }.take(240)
        val path = "users/$targetUserId"
        return try {
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "globallyBanned" to true,
                    "suspensionReason" to cleanReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            syncDeviceBanForTargetUser(
                targetUserId = targetUserId,
                targetName = targetName,
                active = true,
                reason = cleanReason,
                actorUid = uid,
                actorName = adminName
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinCompanyWithCodeAndPin(
        companyCodeInput: String,
        pinInput: String,
        firstNameInput: String,
        lastNameInput: String,
        myEmail: String
    ): Result<CompanySubAccount> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanCode = com.example.auth.SubAccountLoginPrefs.normalizeCompanyCode(companyCodeInput)
        val cleanPin = com.example.auth.SubAccountLoginPrefs.normalizePin(pinInput)
        val first = firstNameInput.trim().take(20)
        val last = lastNameInput.trim().take(20)
        if (first.isEmpty() || last.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter both your first and last name."))
        }
        if (cleanCode.length < 4 || cleanPin.length < 4) {
            return Result.failure(IllegalArgumentException("Enter your Company Code and Unique Employee PIN."))
        }
        val fullName = "$first $last".trim().take(40)
        val subId = "PIN_${cleanCode}_${cleanPin}"
        val subPath = "companySubAccounts/$subId"
        return try {
            val subRef = db.collection("companySubAccounts").document(subId)
            val subSnap = subRef.get().await()
            if (!subSnap.exists()) {
                return Result.failure(
                    IllegalStateException(
                        "Invalid Company Code or Unique Employee PIN. Ask your company owner to create your sub-account and give you your unique PIN."
                    )
                )
            }
            val sub = subSnap.toObject(CompanySubAccount::class.java)
                ?: return Result.failure(IllegalStateException("Invalid employee sub-account record."))
            if (sub.banned) {
                return Result.failure(
                    IllegalStateException("This employee PIN has been permanently banned by ${sub.companyName}.")
                )
            }
            if (!sub.active) {
                return Result.failure(
                    IllegalStateException("This employee PIN was removed by ${sub.companyName} and can no longer be used.")
                )
            }
            if (sub.isCurrentlySuspended()) {
                return Result.failure(
                    IllegalStateException("This employee sub-account is currently suspended by ${sub.companyName}.")
                )
            }
            if (sub.ownerId == uid) {
                return Result.failure(
                    IllegalStateException("You are the owner of ${sub.companyName}. Sign in with another Google account to use an employee PIN.")
                )
            }
            if (sub.claimedUserId.isNotBlank() && sub.claimedUserId != uid) {
                return Result.failure(
                    IllegalStateException("This unique PIN is already bound to another employee account.")
                )
            }

            val nowMs = System.currentTimeMillis()
            if (sub.claimedUserId.isBlank()) {
                subRef.update(
                    mapOf(
                        "subName" to fullName,
                        "claimedUserId" to uid,
                        "claimedEmail" to myEmail.trim().take(120),
                        "claimedAtMillis" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (sub.subName != fullName) {
                runCatching {
                    subRef.update(
                        mapOf(
                            "subName" to fullName,
                            "doNotDisturb" to sub.doNotDisturb,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val resolvedSub = sub.copy(
                id = subId,
                subName = fullName,
                claimedUserId = uid,
                claimedEmail = myEmail.trim().take(120)
            )

            // Update caller profile & directory to Employee Sub-Account scoped to this company
            // Automatically flag B2B sub-accounts as 18+ verified under the parent account
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toLong()
            val existingBirthYear = runCatching {
                db.collection("users").document(uid).get().await().getLong("birthYear") ?: 0L
            }.getOrDefault(0L)
            val resolvedAdultBirthYear =
                if (existingBirthYear in 1900L..(currentYear - 18L)) existingBirthYear else 1995L
            db.collection("users").document(uid).update(
                mapOf(
                    "companySubAccount" to true,
                    "parentCompanyOwnerId" to sub.ownerId,
                    "companyName" to sub.companyName.take(60),
                    "companyThreadId" to sub.companyThreadId,
                    "subAccountId" to subId,
                    "displayName" to fullName,
                    "doNotDisturb" to resolvedSub.doNotDisturb,
                    "age13Verified" to true,
                    "adultVerified" to true,
                    "birthYear" to resolvedAdultBirthYear,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            runCatching {
                val dirRef = db.collection("userDirectory").document(uid)
                if (dirRef.get().await().exists()) {
                    dirRef.update(
                        mapOf(
                            "displayName" to fullName,
                            "companySubAccount" to true,
                            "parentCompanyOwnerId" to sub.ownerId,
                            "doNotDisturb" to resolvedSub.doNotDisturb,
                            "proMember" to false,
                            "proLifetime" to false,
                            "proTier" to ProTier.FREE.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Join the company private group DM
            val threadRef = db.collection("directThreads").document(sub.companyThreadId)
            val threadSnap = threadRef.get().await()
            if (threadSnap.exists()) {
                val thread = threadSnap.toObject(DirectThread::class.java)
                if (thread != null) {
                    val updatedParticipants = (thread.participantIds + uid).distinct()
                    val updatedNames = thread.participantNamesById.toMutableMap().apply {
                        put(uid, fullName)
                    }
                    threadRef.update(
                        mapOf(
                            "participantIds" to updatedParticipants,
                            "participantNamesById" to updatedNames,
                            "lastMessageText" to "$fullName joined ${sub.companyName}",
                            "lastMessageSenderId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(resolvedSub)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, subPath)
            Result.failure(e)
        }
    }
}






