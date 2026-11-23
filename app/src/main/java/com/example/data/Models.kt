package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class StaffTier(val label: String) {
    USER("User"),
    SUPPORT("Support"),
    ADMIN("Admin");

    fun isStaff(): Boolean = this == SUPPORT || this == ADMIN
    fun isAdmin(): Boolean = this == ADMIN

    companion object {
        fun fromRaw(raw: String?): StaffTier =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

data class StaffCapabilities(
    val tier: StaffTier = StaffTier.USER,
    val canEnterInvisible: Boolean = false
) {
    val isAdmin: Boolean get() = tier == StaffTier.ADMIN
    val isSupport: Boolean get() = tier == StaffTier.SUPPORT
    val isStaff: Boolean get() = tier.isStaff()
    val effectiveCanEnterInvisible: Boolean get() = isAdmin || (isSupport && canEnterInvisible)
}

enum class AlertSeverity(val label: String) {
    INFO("Info"),
    WARNING("Warning"),
    URGENT("Urgent");

    companion object {
        fun fromRaw(raw: String?): AlertSeverity =
            entries.firstOrNull { it.name == raw } ?: WARNING
    }
}

enum class RoomCategory(val label: String) {
    VOICE_VIDEO("Voice & Video"),
    MUSIC("Music & Jam"),
    TECH("Tech & Dev"),
    LOUNGE("Lounge & Talk"),
    GAMING("Gaming"),
    LANGUAGE("Languages"),
    SAFE_SPACE("Safe Space");

    companion object {
        fun fromRaw(raw: String): RoomCategory =
            entries.firstOrNull { it.name == raw } ?: VOICE_VIDEO
    }
}

enum class RoomCoverKey(val label: String) {
    HERO("Neon Waves"),
    MUSIC("Acoustic Studio"),
    TECH("Broadcast Desk"),
    DEBATE("Roundtable");

    companion object {
        fun fromRaw(raw: String): RoomCoverKey =
            entries.firstOrNull { it.name == raw } ?: HERO
    }
}

enum class RoomVisibility(val label: String) {
    PUBLIC("Public"),
    PRIVATE("Private");

    companion object {
        fun fromRaw(raw: String): RoomVisibility =
            entries.firstOrNull { it.name == raw } ?: PUBLIC
    }
}

enum class RoomStatus {
    LIVE,
    CLOSED;

    companion object {
        fun fromRaw(raw: String): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: LIVE
    }
}

enum class ParticipantRole(val label: String) {
    HOST("Room Admin"),
    MODERATOR("Co-Admin"),
    SPEAKER("Speaker"),
    LISTENER("Listener");

    companion object {
        fun fromRaw(raw: String): ParticipantRole =
            entries.firstOrNull { it.name == raw } ?: LISTENER
    }
}

enum class MessageType {
    TEXT,
    REACTION,
    MOD_NOTICE,
    JOIN_LEAVE;

    companion object {
        fun fromRaw(raw: String): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class ModActionType(val label: String) {
    MUTE("Muted user"),
    UNMUTE("Unmuted user"),
    BOUNCE("Bounced from room"),
    KICK("Kicked from room"),
    SUSPEND("Suspended user"),
    BAN("Banned user"),
    UNBAN("Unbanned user"),
    DELETE_MSG("Redacted message"),
    AUTO_MOD("Auto-Mod triggered"),
    LOCK_ROOM("Toggled room lock"),
    STAGE_MUTE("Toggled stage mic lock"),
    PROMOTE_MOD("Added Room Co-Admin"),
    DEMOTE_MOD("Removed Room Co-Admin"),
    SEND_ALERT("Sent alert"),
    STAFF_ROLE("Updated staff role"),
    EDIT_USER("Edited user"),
    EDIT_ROOM("Edited room details"),
    CLOSE_ROOM("Closed room"),
    SETTINGS("Updated safety settings");

    companion object {
        fun fromRaw(raw: String): ModActionType =
            entries.firstOrNull { it.name == raw } ?: SETTINGS
    }
}

enum class ReportCategory(val label: String) {
    HARASSMENT("Harassment"),
    HATE_SPEECH("Hate Speech"),
    SPAM_SCAM("Spam or Scam"),
    INAPPROPRIATE_MEDIA("Inappropriate Video/Audio"),
    IMPERSONATION("Impersonation"),
    OTHER("Other Safety Issue");

    companion object {
        fun fromRaw(raw: String): ReportCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class ReportStatus(val label: String) {
    OPEN("Open"),
    RESOLVED("Resolved"),
    DISMISSED("Dismissed");

    companion object {
        fun fromRaw(raw: String): ReportStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class DmPolicy(val label: String, val subtitle: String) {
    OPEN("Allow Private Messages", "Anyone can send you private messages"),
    PAID("Paid Only (\$10 Lock)", "Users pay \$10 (\$5 to you · \$5 to app owner) — kick anytime"),
    DISABLED("Turn Off Private Messages", "No one can start a private chat with you");

    companion object {
        fun fromRaw(raw: String?): DmPolicy =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Guest",
    val handle: String = "guest",
    val bio: String = "",
    val avatarColorHex: String = "#0088FF",
    val photoBase64: String = "",
    val pushToTalkPreferred: Boolean = true,
    val darkThemePreferred: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val invisibleModePreferred: Boolean = false,
    val globallyBanned: Boolean = false,
    val disableCreateRoom: Boolean = false,
    val disableEnterRoom: Boolean = false,
    val disableMic: Boolean = false,
    val disableVideo: Boolean = false,
    val suspendedUntilMillis: Long = 0L,
    val suspensionReason: String = "",
    val pointsBalance: Long = 0L,
    val creatorEarningsCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val dmPolicy: String = DmPolicy.OPEN.name,
    val adultVerified: Boolean = false,
    val birthYear: Long = 0L,
    val blockedUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun dmPolicyEnum(): DmPolicy = DmPolicy.fromRaw(dmPolicy)
    fun isAdult18Plus(): Boolean {
        if (!adultVerified || birthYear < 1900L) return false
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return (currentYear - birthYear.toInt()) >= 18
    }
    fun currentlySuspended(nowMs: Long = System.currentTimeMillis()): Boolean =
        suspendedUntilMillis > nowMs

    fun remainingSuspensionLabel(nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = (suspendedUntilMillis - nowMs).coerceAtLeast(0L)
        if (diffMs <= 0L) return ""
        val totalMinutes = ((diffMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class StaffRole(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val staffTier: String = StaffTier.SUPPORT.name,
    val canEnterInvisible: Boolean = false,
    val addedByUid: String = "",
    val addedByName: String = "",
    val inviteId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun tierEnum(): StaffTier = StaffTier.fromRaw(staffTier)
}

@Keep
@IgnoreExtraProperties
data class StaffInvite(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val staffTier: String = StaffTier.SUPPORT.name,
    val canEnterInvisible: Boolean = false,
    val addedByUid: String = "",
    val addedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun tierEnum(): StaffTier = StaffTier.fromRaw(staffTier)
}

@Keep
@IgnoreExtraProperties
data class UserAlert(
    @DocumentId val id: String = "",
    val targetUserId: String = "",
    val targetName: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderTier: String = StaffTier.ADMIN.name,
    val roomId: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = AlertSeverity.WARNING.name,
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun senderTierEnum(): StaffTier = StaffTier.fromRaw(senderTier)
    fun severityEnum(): AlertSeverity = AlertSeverity.fromRaw(severity)
}

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostTier: String = StaffTier.USER.name,
    val title: String = "",
    val topic: String = "",
    val category: String = RoomCategory.VOICE_VIDEO.name,
    val coverKey: String = RoomCoverKey.HERO.name,
    val visibility: String = RoomVisibility.PUBLIC.name,
    val status: String = RoomStatus.LIVE.name,
    val videoEnabled: Boolean = true,
    val autoModEnabled: Boolean = true,
    val autoModBlockMode: Boolean = false,
    val linksBlocked: Boolean = true,
    val roomLocked: Boolean = false,
    val stageMicLocked: Boolean = false,
    val adminMicPriorityActive: Boolean = false,
    val slowModeSeconds: Long = 0L,
    val maxSpeakers: Long = 6L,
    val participantCount: Long = 0L,
    val moderatorIds: List<String> = emptyList(),
    val bannedUserIds: List<String> = emptyList(),
    val bouncedUntilByUserId: Map<String, Long> = emptyMap(),
    val mutedUserIds: List<String> = emptyList(),
    val blockedWords: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun displayTitle(): String = title.ifBlank { "Untitled Room" }
    fun hostTierEnum(): StaffTier = StaffTier.fromRaw(hostTier)
    fun categoryEnum(): RoomCategory = RoomCategory.fromRaw(category)
    fun coverEnum(): RoomCoverKey = RoomCoverKey.fromRaw(coverKey)
    fun visibilityEnum(): RoomVisibility = RoomVisibility.fromRaw(visibility)
    fun statusEnum(): RoomStatus = RoomStatus.fromRaw(status)
    fun isRoomAdmin(uid: String): Boolean = hostId == uid
    fun isCoAdmin(uid: String): Boolean = hostId != uid && moderatorIds.contains(uid)
    fun hasModPrivileges(uid: String, staffTier: StaffTier = StaffTier.USER): Boolean =
        hostId == uid || moderatorIds.contains(uid) || staffTier.isStaff()
    fun canBanInRoom(uid: String, staffTier: StaffTier = StaffTier.USER): Boolean =
        hostId == uid || staffTier == StaffTier.ADMIN
    fun canManageCoAdmins(uid: String, staffTier: StaffTier = StaffTier.USER): Boolean =
        hostId == uid || staffTier == StaffTier.ADMIN
    fun canEditOrCloseRoom(uid: String, staffTier: StaffTier = StaffTier.USER): Boolean =
        hostId == uid || staffTier == StaffTier.ADMIN

    fun isBouncedOut(uid: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        (bouncedUntilByUserId[uid] ?: 0L) > nowMs

    fun remainingBounceLabel(uid: String, nowMs: Long = System.currentTimeMillis()): String {
        val untilMs = bouncedUntilByUserId[uid] ?: 0L
        val diffMs = (untilMs - nowMs).coerceAtLeast(0L)
        if (diffMs <= 0L) return ""
        val totalMinutes = ((diffMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class RoomParticipant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val displayName: String = "",
    val avatarColorHex: String = "#6C63FF",
    val role: String = ParticipantRole.LISTENER.name,
    val staffTier: String = StaffTier.USER.name,
    val invisible: Boolean = false,
    val micActive: Boolean = false,
    val camActive: Boolean = false,
    val handRaised: Boolean = false,
    val mutedByMod: Boolean = false,
    val speakingLevel: Double = 0.0,
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): ParticipantRole = ParticipantRole.fromRaw(role)
    fun staffTierEnum(): StaffTier = StaffTier.fromRaw(staffTier)
    fun onStage(): Boolean = !invisible && roleEnum() != ParticipantRole.LISTENER
}

val ROOM_REACTION_EMOJIS = listOf("❤️", "😂", "🔥", "👏", "😮", "👍")

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = ParticipantRole.LISTENER.name,
    val senderTier: String = StaffTier.USER.name,
    val text: String = "",
    val messageType: String = MessageType.TEXT.name,
    val flaggedByAutoMod: Boolean = false,
    val deletedByMod: Boolean = false,
    val reactionsByEmoji: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun messageTypeEnum(): MessageType = MessageType.fromRaw(messageType)
    fun senderTierEnum(): StaffTier = StaffTier.fromRaw(senderTier)
    fun hasReacted(emoji: String, uid: String): Boolean =
        reactionsByEmoji[emoji]?.contains(uid) == true
    fun nonEmptyReactions(): List<Pair<String, List<String>>> =
        reactionsByEmoji.entries
            .filter { it.value.isNotEmpty() }
            .sortedByDescending { it.value.size }
            .map { it.key to it.value }
}

@Keep
@IgnoreExtraProperties
data class ModerationLog(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val targetUserId: String = "",
    val targetName: String = "",
    val actionType: String = ModActionType.SETTINGS.name,
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun actionTypeEnum(): ModActionType = ModActionType.fromRaw(actionType)
}

@Keep
@IgnoreExtraProperties
data class SafetyReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val roomId: String = "",
    val roomTitle: String = "",
    val roomHostId: String = "",
    val targetUserId: String = "",
    val targetName: String = "",
    val category: String = ReportCategory.HARASSMENT.name,
    val details: String = "",
    val status: String = ReportStatus.OPEN.name,
    val resolutionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): ReportCategory = ReportCategory.fromRaw(category)
    fun statusEnum(): ReportStatus = ReportStatus.fromRaw(status)
}

fun canonicalPairId(uid1: String, uid2: String): String =
    if (uid1 <= uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"

enum class FriendshipStatus {
    PENDING,
    ACCEPTED;

    companion object {
        fun fromRaw(raw: String?): FriendshipStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val bio: String = "",
    val avatarColorHex: String = "#0088FF",
    val photoBase64: String = "",
    val staffTier: String = StaffTier.USER.name,
    val postCount: Long = 0L,
    val dmPolicy: String = DmPolicy.OPEN.name,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun tierEnum(): StaffTier = StaffTier.fromRaw(staffTier)
    fun dmPolicyEnum(): DmPolicy = DmPolicy.fromRaw(dmPolicy)
}

@Keep
@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val userIds: List<String> = emptyList(),
    val requesterId: String = "",
    val requesterName: String = "",
    val requesterHandle: String = "",
    val requesterColorHex: String = "#0088FF",
    val requesterTier: String = StaffTier.USER.name,
    val recipientId: String = "",
    val recipientName: String = "",
    val recipientHandle: String = "",
    val recipientColorHex: String = "#0088FF",
    val recipientTier: String = StaffTier.USER.name,
    val status: String = FriendshipStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): FriendshipStatus = FriendshipStatus.fromRaw(status)
    fun isAccepted(): Boolean = statusEnum() == FriendshipStatus.ACCEPTED
    fun isIncomingFor(myUid: String): Boolean = statusEnum() == FriendshipStatus.PENDING && recipientId == myUid
    fun isOutgoingFor(myUid: String): Boolean = statusEnum() == FriendshipStatus.PENDING && requesterId == myUid

    fun otherUserId(myUid: String): String = if (requesterId == myUid) recipientId else requesterId
    fun otherName(myUid: String): String = if (requesterId == myUid) recipientName else requesterName
    fun otherHandle(myUid: String): String = if (requesterId == myUid) recipientHandle else requesterHandle
    fun otherColorHex(myUid: String): String = if (requesterId == myUid) recipientColorHex else requesterColorHex
    fun otherTier(myUid: String): StaffTier = StaffTier.fromRaw(
        if (requesterId == myUid) recipientTier else requesterTier
    )
}

@Keep
@IgnoreExtraProperties
data class DirectThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val isGroup: Boolean = false,
    val groupName: String = "",
    val participantNamesById: Map<String, String> = emptyMap(),
    val kickedUserIds: List<String> = emptyList(),
    val userAId: String = "",
    val userAName: String = "",
    val userAColorHex: String = "#0088FF",
    val userATier: String = StaffTier.USER.name,
    val userBId: String = "",
    val userBName: String = "",
    val userBColorHex: String = "#0088FF",
    val userBTier: String = StaffTier.USER.name,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val unreadByUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherUserId(myUid: String): String = if (userAId == myUid) userBId else userAId
    fun otherName(myUid: String): String = if (userAId == myUid) userBName else userAName
    fun otherColorHex(myUid: String): String = if (userAId == myUid) userBColorHex else userAColorHex
    fun otherTier(myUid: String): StaffTier = StaffTier.fromRaw(
        if (userAId == myUid) userBTier else userATier
    )
    fun isUnreadFor(myUid: String): Boolean = unreadByUserIds.contains(myUid)
    fun isUserKicked(uid: String): Boolean = kickedUserIds.contains(uid)
    fun displayTitle(myUid: String): String {
        if (!isGroup) return otherName(myUid)
        if (groupName.isNotBlank()) return groupName
        val otherNames = participantNamesById
            .filterKeys { it != myUid }
            .values
            .filter { it.isNotBlank() }
        return if (otherNames.isNotEmpty()) {
            otherNames.take(3).joinToString(", ")
        } else {
            "Group Chat (${participantIds.size})"
        }
    }
    fun memberNamesSummary(): String {
        val names = participantNamesById.values.filter { it.isNotBlank() }
        return if (names.isNotEmpty()) names.joinToString(" · ") else "${participantIds.size} members"
    }
}

@Keep
@IgnoreExtraProperties
data class DmAccessPass(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val recipientName: String = "",
    val priceCents: Long = 1000L,
    val creatorShareCents: Long = 500L,
    val platformShareCents: Long = 500L,
    val active: Boolean = true,
    val kicked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyAllowed(): Boolean = active && !kicked
}

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val threadId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val senderTier: String = StaffTier.USER.name,
    val text: String = "",
    val imageBase64: String = "",
    val audioBase64: String = "",
    val audioDurationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun senderTierEnum(): StaffTier = StaffTier.fromRaw(senderTier)
    fun hasVoiceNote(): Boolean = audioBase64.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class ProfilePost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorColorHex: String = "#0088FF",
    val authorPhotoBase64: String = "",
    val authorTier: String = StaffTier.USER.name,
    val text: String = "",
    val imageBase64: String = "",
    val locked: Boolean = false,
    val unlockPricePoints: Long = 2L,
    val unlockedByUserIds: List<String> = emptyList(),
    val boostedAtMillis: Long = 0L,
    val boostedUntilMillis: Long = 0L,
    val likedByUserIds: List<String> = emptyList(),
    val commentCount: Long = 0L,
    val shareCount: Long = 0L,
    val sharedFromPostId: String = "",
    val sharedFromAuthorId: String = "",
    val sharedFromAuthorName: String = "",
    val sharedFromAuthorHandle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun authorTierEnum(): StaffTier = StaffTier.fromRaw(authorTier)
    fun isLikedBy(uid: String): Boolean = likedByUserIds.contains(uid)
    fun isUnlockedFor(
        uid: String,
        subscribedCreatorIds: Set<String> = emptySet(),
        viewerIsAdultVerified: Boolean = true
    ): Boolean {
        if (!locked) return true
        if (!viewerIsAdultVerified) return false
        return authorId == uid || unlockedByUserIds.contains(uid) || subscribedCreatorIds.contains(authorId)
    }
    fun isShareable(): Boolean = !locked
    fun isCurrentlyBoosted(nowMs: Long = System.currentTimeMillis()): Boolean = boostedUntilMillis > nowMs
    fun boostRemainingLabel(nowMs: Long = System.currentTimeMillis()): String {
        val diff = (boostedUntilMillis - nowMs).coerceAtLeast(0L)
        val hours = diff / 3_600_000L
        val minutes = (diff % 3_600_000L) / 60_000L
        return when {
            hours >= 1L -> "${hours}h left"
            minutes >= 1L -> "${minutes}m left"
            else -> "<1m left"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorColorHex: String = "#0088FF",
    val authorPhotoBase64: String = "",
    val authorTier: String = StaffTier.USER.name,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun authorTierEnum(): StaffTier = StaffTier.fromRaw(authorTier)
}

enum class PointTransactionType(val label: String) {
    BUY_POINTS("Bought Points"),
    UNLOCK_PHOTO("Unlocked Photo"),
    SUBSCRIBE_CREATOR("Creator Subscription"),
    BOOST_POST("Boosted to Top of Discovery"),
    UNLOCK_DM("Paid Private Message (\$10)");

    companion object {
        fun fromRaw(raw: String?): PointTransactionType =
            entries.firstOrNull { it.name == raw } ?: BUY_POINTS
    }
}

@Keep
@IgnoreExtraProperties
data class CreatorSubscription(
    @DocumentId val id: String = "",
    val subscriberId: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val priceCents: Long = 1500L,
    val creatorShareCents: Long = 750L,
    val platformShareCents: Long = 750L,
    val active: Boolean = true,
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        active && expiresAtMillis > nowMs

    fun daysRemaining(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = (expiresAtMillis - nowMs).coerceAtLeast(0L)
        return ((diff + 86_399_999L) / 86_400_000L).toInt()
    }
}

@Keep
@IgnoreExtraProperties
data class PointTransaction(
    @DocumentId val id: String = "",
    val type: String = PointTransactionType.BUY_POINTS.name,
    val buyerId: String = "",
    val buyerName: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val postId: String = "",
    val pointsDelta: Long = 0L,
    val grossCents: Long = 0L,
    val creatorShareCents: Long = 0L,
    val platformShareCents: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun typeEnum(): PointTransactionType = PointTransactionType.fromRaw(type)
}

fun formatUsdCents(cents: Long): String {
    val safe = cents.coerceAtLeast(0L)
    val dollars = safe / 100L
    val rem = safe % 100L
    return if (rem == 0L) "$$dollars" else "$$dollars.${rem.toString().padStart(2, '0')}"
}

