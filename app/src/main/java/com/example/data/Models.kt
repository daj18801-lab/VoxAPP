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
    PAID("Paid Only (\$5 Lock)", "Users pay \$5 (you earn +1 usable point per user; 10 pts = Pro) — kick anytime"),
    DISABLED("Turn Off Private Messages", "No one can start a private chat with you");

    companion object {
        fun fromRaw(raw: String?): DmPolicy =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class PttThemeStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    val pricePoints: Long,
    val standbyColorHex: String,
    val liveColorHex: String,
    val ringGlowHex: String,
    val hasPulseRing: Boolean
) {
    CLASSIC(
        id = "CLASSIC",
        label = "Classic Emerald",
        subtitle = "Clean green standby & crimson live",
        pricePoints = 0L,
        standbyColorHex = "#10B981",
        liveColorHex = "#EF4444",
        ringGlowHex = "#34D399",
        hasPulseRing = false
    ),
    NEON_MAGENTA(
        id = "NEON_MAGENTA",
        label = "Neon Magenta Pulse",
        subtitle = "Electric magenta core with glowing pulse ring",
        pricePoints = 2L,
        standbyColorHex = "#EC4899",
        liveColorHex = "#F43F5E",
        ringGlowHex = "#F472B6",
        hasPulseRing = true
    ),
    TACTICAL_CYAN(
        id = "TACTICAL_CYAN",
        label = "Cyber Cyan Ring",
        subtitle = "Tactical cyan beacon with laser halo",
        pricePoints = 2L,
        standbyColorHex = "#06B6D4",
        liveColorHex = "#0EA5E9",
        ringGlowHex = "#22D3EE",
        hasPulseRing = true
    ),
    SOLAR_GOLD(
        id = "SOLAR_GOLD",
        label = "Solar Gold Halo",
        subtitle = "Luxury gold badge with radiant amber ring",
        pricePoints = 3L,
        standbyColorHex = "#F59E0B",
        liveColorHex = "#EA580C",
        ringGlowHex = "#FDE047",
        hasPulseRing = true
    ),
    ROYAL_PURPLE(
        id = "ROYAL_PURPLE",
        label = "Royal Purple Glow",
        subtitle = "Deep violet button with neon lavender aura",
        pricePoints = 3L,
        standbyColorHex = "#8B5CF6",
        liveColorHex = "#A855F7",
        ringGlowHex = "#C084FC",
        hasPulseRing = true
    ),
    INFERNO_CRIMSON(
        id = "INFERNO_CRIMSON",
        label = "Inferno Crimson Ring",
        subtitle = "Blazing ember core with high-energy pulse ring",
        pricePoints = 4L,
        standbyColorHex = "#DC2626",
        liveColorHex = "#F97316",
        ringGlowHex = "#FB7185",
        hasPulseRing = true
    );

    companion object {
        fun fromRaw(raw: String?): PttThemeStyle =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: CLASSIC
    }
}

enum class PttChirpStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    val pricePoints: Long
) {
    STANDARD(
        id = "STANDARD",
        label = "Standard Click",
        subtitle = "Subtle built-in key-down / key-up tone",
        pricePoints = 0L
    ),
    ROGER_BEEP(
        id = "ROGER_BEEP",
        label = "Classic Roger Beep",
        subtitle = "Iconic walkie-talkie dual-beep confirmation",
        pricePoints = 2L
    ),
    TACTICAL_SQUAWK(
        id = "TACTICAL_SQUAWK",
        label = "Tactical Radio Squawk",
        subtitle = "Crisp static squelch burst & high chirp",
        pricePoints = 3L
    ),
    RETRO_POLICE(
        id = "RETRO_POLICE",
        label = "Dispatch 3-Tone Chirp",
        subtitle = "Classic three-step dispatch radio chime",
        pricePoints = 3L
    ),
    SCIFI_PULSE(
        id = "SCIFI_PULSE",
        label = "Sci-Fi Comms Pulse",
        subtitle = "Futuristic frequency-sweep communicator chirp",
        pricePoints = 4L
    );

    companion object {
        fun fromRaw(raw: String?): PttChirpStyle =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: STANDARD
    }
}

enum class UserSex(val label: String) {
    UNSPECIFIED("Not set"),
    MALE("Male"),
    FEMALE("Female");

    companion object {
        fun fromRaw(raw: String?): UserSex =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNSPECIFIED
    }
}

enum class AgeFilterRange(val label: String, val minAge: Int, val maxAge: Int) {
    ALL("Any Age", 0, 999),
    AGE_18_24("18–24", 18, 24),
    AGE_25_34("25–34", 25, 34),
    AGE_35_44("35–44", 35, 44),
    AGE_45_PLUS("45+", 45, 120);

    fun matches(age: Int): Boolean =
        if (this == ALL) true else age in minAge..maxAge
}

enum class PresenceStatus(val label: String) {
    ONLINE("Online"),
    IDLE("Idle"),
    OFFLINE("Offline");

    companion object {
        fun fromRaw(raw: String?): PresenceStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ONLINE
    }
}

fun formatAppTimeSpent(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    if (safe < 60L) return "${safe}s"
    val totalMinutes = safe / 60L
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes % (60L * 24L)) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L && hours > 0L -> "${days}d ${hours}h"
        days > 0L -> "${days}d"
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
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
    val halfPointsBalance: Int = 0,
    val creatorEarnedPoints: Long = 0L,
    val creatorEarningsCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val proMember: Boolean = false,
    val proExpiresAtMillis: Long = 0L,
    val profileBoostedUntilMillis: Long = 0L,
    val spotlightFrameUntilMillis: Long = 0L,
    val spotlightFrameColorHex: String = SpotlightFrameColor.ELECTRIC_CYAN.primaryHex,
    val loginStreakDays: Int = 0,
    val lastLoginDayKey: String = "",
    val lastStreakRewardDayKey: String = "",
    val referralCode: String = "",
    val referredByUserId: String = "",
    val referralBonusClaimed: Boolean = false,
    val referralEarnedPoints: Long = 0L,
    val ownedPttThemes: List<String> = emptyList(),
    val selectedPttTheme: String = PttThemeStyle.CLASSIC.id,
    val ownedPttChirps: List<String> = emptyList(),
    val selectedPttChirp: String = PttChirpStyle.STANDARD.id,
    val dmPolicy: String = DmPolicy.OPEN.name,
    val adultVerified: Boolean = false,
    val birthYear: Long = 0L,
    val sex: String = UserSex.UNSPECIFIED.name,
    val age: Long = 0L,
    val totalTimeSpentSec: Long = 0L,
    val appearanceStatus: String = PresenceStatus.ONLINE.name,
    val blockedUserIds: List<String> = emptyList(),
    val pictureUploadTimestamps: List<Long> = emptyList(),
    val discoveryPhotoTimestamps: List<Long> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun dmPolicyEnum(): DmPolicy = DmPolicy.fromRaw(dmPolicy)
    fun sexEnum(): UserSex = UserSex.fromRaw(sex)
    fun appearanceStatusEnum(): PresenceStatus = PresenceStatus.fromRaw(appearanceStatus)
    fun formattedTimeSpent(): String = formatAppTimeSpent(totalTimeSpentSec)
    fun formattedPointsBalance(): String = formatPointsBalance(pointsBalance, halfPointsBalance)
    fun isProfileBoosted(nowMs: Long = System.currentTimeMillis()): Boolean =
        profileBoostedUntilMillis > nowMs
    fun profileBoostRemainingLabel(nowMs: Long = System.currentTimeMillis()): String =
        formatRemainingHoursLabel(profileBoostedUntilMillis, nowMs)
    fun hasActiveSpotlightFrame(nowMs: Long = System.currentTimeMillis()): Boolean =
        spotlightFrameUntilMillis > nowMs
    fun spotlightFrameRemainingLabel(nowMs: Long = System.currentTimeMillis()): String =
        formatRemainingHoursLabel(spotlightFrameUntilMillis, nowMs)
    fun activeSpotlightColorHex(nowMs: Long = System.currentTimeMillis()): String? =
        if (hasActiveSpotlightFrame(nowMs)) spotlightFrameColorHex else null
    fun effectiveReferralCode(): String =
        referralCode.ifBlank { generateReferralCode(userId.ifBlank { id }) }
    fun streakCycleDay(): Int {
        if (loginStreakDays <= 0) return 0
        val mod = loginStreakDays % 7
        return if (mod == 0) 7 else mod
    }
    fun selectedPttThemeEnum(): PttThemeStyle = PttThemeStyle.fromRaw(selectedPttTheme)
    fun selectedPttChirpEnum(): PttChirpStyle = PttChirpStyle.fromRaw(selectedPttChirp)
    fun ownsPttTheme(theme: PttThemeStyle): Boolean =
        theme == PttThemeStyle.CLASSIC || ownedPttThemes.contains(theme.id)
    fun ownsPttChirp(chirp: PttChirpStyle): Boolean =
        chirp == PttChirpStyle.STANDARD || ownedPttChirps.contains(chirp.id)
    fun effectiveAge(): Int {
        if (age in 18L..120L) return age.toInt()
        if (birthYear >= 1900L) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val derived = currentYear - birthYear.toInt()
            if (derived in 18..120) return derived
        }
        return 0
    }
    fun isProActive(
        tier: StaffTier = StaffTier.USER,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = tier.isStaff() || (proMember && proExpiresAtMillis > nowMs)

    fun isProSubscriberOnly(nowMs: Long = System.currentTimeMillis()): Boolean =
        proMember && proExpiresAtMillis > nowMs

    fun proDaysRemaining(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = (proExpiresAtMillis - nowMs).coerceAtLeast(0L)
        return ((diff + 86_399_999L) / 86_400_000L).toInt()
    }

    /** Free members: 5-second PTT clips; Pro members & Staff: 1-minute (60s) PTT clips. */
    fun pttMaxDurationSeconds(
        tier: StaffTier = StaffTier.USER,
        nowMs: Long = System.currentTimeMillis()
    ): Int = if (isProActive(tier, nowMs)) PRO_PTT_MAX_SECONDS else FREE_PTT_MAX_SECONDS

    fun isPaidMember(tier: StaffTier = StaffTier.USER): Boolean =
        isProActive(tier) || totalSpentCents > 0L || pointsBalance > 0L
    fun isAdult18Plus(): Boolean {
        if (!adultVerified || birthYear < 1900L) return false
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return (currentYear - birthYear.toInt()) >= 18
    }
    fun recentPictureUploadsCount(nowMs: Long = System.currentTimeMillis()): Int =
        pictureUploadTimestamps.count { nowMs - it < PICTURE_UPLOAD_WINDOW_MS }

    fun remainingPictureUploads(nowMs: Long = System.currentTimeMillis()): Int =
        (MAX_PICTURES_PER_WINDOW - recentPictureUploadsCount(nowMs)).coerceAtLeast(0)

    fun recentDiscoveryPhotosCount(nowMs: Long = System.currentTimeMillis()): Int =
        discoveryPhotoTimestamps.count { nowMs - it < DISCOVERY_PHOTO_WEEK_MS }

    fun remainingFreeDiscoveryPhotos(nowMs: Long = System.currentTimeMillis()): Int =
        (FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK - recentDiscoveryPhotosCount(nowMs)).coerceAtLeast(0)

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

    companion object {
        const val MAX_PICTURES_PER_WINDOW = 10
        const val PICTURE_UPLOAD_WINDOW_MS = 10L * 60L * 1000L
        const val FREE_PTT_MAX_SECONDS = 5
        const val PRO_PTT_MAX_SECONDS = 60
        const val PRO_PRICE_CENTS = 999L
        const val PRO_DURATION_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val PRO_REDEEM_POINTS = 10L
        const val FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK = 2
        const val DISCOVERY_PHOTO_WEEK_MS = 7L * 24L * 60L * 60L * 1000L
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
data class AppBroadcast(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = AlertSeverity.INFO.name,
    val senderId: String = "",
    val senderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
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
    val proMember: Boolean = false,
    val proExpiresAtMillis: Long = 0L,
    val profileBoostedUntilMillis: Long = 0L,
    val spotlightFrameUntilMillis: Long = 0L,
    val spotlightFrameColorHex: String = SpotlightFrameColor.ELECTRIC_CYAN.primaryHex,
    val referralCode: String = "",
    val postCount: Long = 0L,
    val dmPolicy: String = DmPolicy.OPEN.name,
    val sex: String = UserSex.UNSPECIFIED.name,
    val age: Long = 0L,
    val online: Boolean = false,
    val appearanceStatus: String = PresenceStatus.ONLINE.name,
    val totalTimeSpentSec: Long = 0L,
    val acceptedFriendIds: List<String> = emptyList(),
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun tierEnum(): StaffTier = StaffTier.fromRaw(staffTier)
    fun isProActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        proMember && proExpiresAtMillis > nowMs
    fun isProfileBoosted(nowMs: Long = System.currentTimeMillis()): Boolean =
        profileBoostedUntilMillis > nowMs
    fun hasActiveSpotlightFrame(nowMs: Long = System.currentTimeMillis()): Boolean =
        spotlightFrameUntilMillis > nowMs
    fun activeSpotlightColorHex(nowMs: Long = System.currentTimeMillis()): String? =
        if (hasActiveSpotlightFrame(nowMs)) spotlightFrameColorHex else null
    fun dmPolicyEnum(): DmPolicy = DmPolicy.fromRaw(dmPolicy)
    fun sexEnum(): UserSex = UserSex.fromRaw(sex)
    fun appearanceStatusEnum(): PresenceStatus = PresenceStatus.fromRaw(appearanceStatus)
    fun formattedTimeSpent(): String = formatAppTimeSpent(totalTimeSpentSec)
    fun effectiveAge(): Int = if (age in 18L..120L) age.toInt() else 0

    /**
     * Returns true only if the user explicitly has online = true AND their last heartbeat
     * is within the last 3 minutes (protects against abrupt app kills or lost connectivity).
     */
    fun isCurrentlyOnline(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!online) return false
        val lastMs = lastActiveAt?.toDate()?.time ?: return true
        return (nowMillis - lastMs) <= ONLINE_STALE_WINDOW_MS
    }

    /** True connectivity status independent of user's chosen appearance preference. */
    fun trueStatus(nowMillis: Long = System.currentTimeMillis()): PresenceStatus =
        if (isCurrentlyOnline(nowMillis)) PresenceStatus.ONLINE else PresenceStatus.OFFLINE

    /**
     * Status shown to a viewer:
     * - Admins & Support always see the user's true online/offline status.
     * - Regular users see the user's selected appearanceStatus (Online / Idle / Offline) when active,
     *   or Offline when the user is actually offline.
     */
    fun displayedStatusFor(
        viewerIsStaff: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): PresenceStatus {
        val trulyOnline = isCurrentlyOnline(nowMillis)
        if (!trulyOnline) return PresenceStatus.OFFLINE
        if (viewerIsStaff) return PresenceStatus.ONLINE
        return appearanceStatusEnum()
    }

    /** True when the user is truly online but hiding their status as Idle or Offline. */
    fun isHidingTrueOnlineStatus(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isCurrentlyOnline(nowMillis) && appearanceStatusEnum() != PresenceStatus.ONLINE

    companion object {
        const val ONLINE_STALE_WINDOW_MS = 3L * 60L * 1000L
    }
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
    val blockedByUserIds: List<String> = emptyList(),
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
    val pttEnabledUserIds: List<String> = emptyList(),
    val disappearingDurationSec: Long = 0L,
    val readReceiptsDisabledByUserIds: List<String> = emptyList(),
    val readTimestampsByUserId: Map<String, Long> = emptyMap(),
    val lastAlertSenderId: String = "",
    val lastAlertSenderName: String = "",
    val lastAlertAtMillis: Long = 0L,
    val pinnedMessageId: String = "",
    val pinnedMessageText: String = "",
    val pinnedMessageSenderName: String = "",
    val pinnedByUserId: String = "",
    val pinnedByName: String = "",
    val pinnedAtMillis: Long = 0L,
    val talkStreakCount: Int = 0,
    val talkStreakLastMutualDay: String = "",
    val lastMessageDayByUserId: Map<String, String> = emptyMap(),
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
    fun isBlockedBy(uid: String): Boolean = blockedByUserIds.contains(uid)
    fun hasAnyBlock(): Boolean = blockedByUserIds.isNotEmpty()
    fun isPttEnabledFor(uid: String): Boolean = pttEnabledUserIds.contains(uid)
    fun isOtherPttEnabled(myUid: String): Boolean = pttEnabledUserIds.any { it != myUid }
    fun isMutualPttActive(myUid: String): Boolean =
        isPttEnabledFor(myUid) && pttEnabledUserIds.any { it != myUid }
    fun isDisappearingEnabled(): Boolean = disappearingDurationSec > 0L
    fun disappearingLabel(): String = formatDisappearingDuration(disappearingDurationSec)
    fun isReadReceiptsEnabledFor(uid: String): Boolean = !readReceiptsDisabledByUserIds.contains(uid)
    fun areReadReceiptsMutual(myUid: String, otherUid: String): Boolean =
        isReadReceiptsEnabledFor(myUid) && isReadReceiptsEnabledFor(otherUid)
    fun lastReadMillisFor(uid: String): Long = readTimestampsByUserId[uid] ?: 0L
    fun hasPinnedMessage(): Boolean = pinnedMessageId.isNotBlank()
    fun activeTalkStreakCount(
        todayKey: String = utcDayKey(),
        yesterdayKey: String = utcYesterdayKey()
    ): Int {
        if (isGroup || talkStreakCount <= 0) return 0
        return if (talkStreakLastMutualDay == todayKey || talkStreakLastMutualDay == yesterdayKey) {
            talkStreakCount
        } else 0
    }
    fun isTalkStreakExpiringToday(
        todayKey: String = utcDayKey(),
        yesterdayKey: String = utcYesterdayKey()
    ): Boolean = activeTalkStreakCount(todayKey, yesterdayKey) >= 3 && talkStreakLastMutualDay == yesterdayKey
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

    companion object {
        val DISAPPEARING_OPTIONS_SEC = listOf(0L, 30L, 300L, 3600L, 86400L)

        fun formatDisappearingDuration(seconds: Long): String = when (seconds) {
            30L -> "30s"
            300L -> "5m"
            3600L -> "1h"
            86400L -> "24h"
            else -> "Off"
        }
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
    val priceCents: Long = 500L,
    val creatorShareCents: Long = 0L,
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
    val expiresAtMillis: Long = 0L,
    val messageType: String = TYPE_TEXT,
    val reactionsByUserId: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun senderTierEnum(): StaffTier = StaffTier.fromRaw(senderTier)
    fun hasVoiceNote(): Boolean = audioBase64.isNotBlank()
    fun isScreenshotNotice(): Boolean = messageType == TYPE_SCREENSHOT
    fun isSupportHandoffNotice(): Boolean = messageType == TYPE_SUPPORT_HANDOFF
    fun isSystemNotice(): Boolean = isScreenshotNotice() || isSupportHandoffNotice()
    fun isStandaloneThumbsUp(): Boolean =
        !isSystemNotice() && imageBase64.isBlank() && audioBase64.isBlank() && text.trim() == "👍"
    fun myReaction(myUid: String): String? = reactionsByUserId[myUid]?.takeIf { it.isNotBlank() }
    fun groupedReactions(): List<Pair<String, Int>> =
        reactionsByUserId.values
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    fun isDisappearing(): Boolean = expiresAtMillis > 0L
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis > 0L && nowMs >= expiresAtMillis
    fun remainingExpiryLabel(nowMs: Long = System.currentTimeMillis()): String {
        if (expiresAtMillis <= 0L) return ""
        val remSec = ((expiresAtMillis - nowMs).coerceAtLeast(0L) + 999L) / 1000L
        return when {
            remSec <= 0L -> "0s"
            remSec < 60L -> "${remSec}s"
            remSec < 3600L -> "${(remSec + 59L) / 60L}m"
            else -> "${(remSec + 3599L) / 3600L}h"
        }
    }

    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_SCREENSHOT = "SCREENSHOT"
        const val TYPE_SUPPORT_HANDOFF = "SUPPORT_HANDOFF"
        val ALLOWED_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")
    }
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
    BOOST_POST("Promoted Photo to Top (5 pts)"),
    BOOST_REEL("Promoted Video Reel to Top (5 pts)"),
    BOOST_PROFILE("Promoted Profile to Top (5 pts)"),
    BUY_PTT_ITEM("Custom PTT Theme / Walkie Chirp"),
    BUY_SPOTLIGHT_FRAME("24h Neon Spotlight Frame (5 pts)"),
    DAILY_STREAK_BONUS("7-Day Login Streak (+0.5 pt)"),
    REFERRAL_BONUS("Invite-a-Friend Bonus (+2 pts)"),
    UNLOCK_DM("Paid Private Message (\$5)"),
    PRO_MEMBERSHIP("VoxStage Pro (\$9.99/mo)");

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
    val creatorShareCents: Long = 0L,
    val platformShareCents: Long = 1500L,
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
    val playOrderId: String = "",
    val playProductId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun typeEnum(): PointTransactionType = PointTransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "global",
    val bannerTitle: String = "",
    val bannerMessage: String = "",
    val bannerEnabled: Boolean = false,
    val trialLoginsEnabled: Boolean = true,
    val updatedByName: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasVisibleBanner(): Boolean =
        bannerEnabled && (bannerTitle.isNotBlank() || bannerMessage.isNotBlank())
}

@Keep
@IgnoreExtraProperties
data class RoomTransfer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetRoomId: String = "",
    val targetRoomTitle: String = "",
    val fromRoomId: String = "",
    val movedByAdminId: String = "",
    val movedByAdminName: String = "",
    val reason: String = "",
    val transferType: String = TYPE_ROOM,
    val targetThreadId: String = "",
    val targetSupportUserId: String = "",
    val targetSupportName: String = "",
    val consumed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isSupportDmTransfer(): Boolean = transferType == TYPE_SUPPORT_DM && targetThreadId.isNotBlank()

    companion object {
        const val TYPE_ROOM = "ROOM"
        const val TYPE_SUPPORT_DM = "SUPPORT_DM"
    }
}

enum class SupportTicketCategory(val label: String) {
    GENERAL("General Help"),
    ACCOUNT("Account & Profile"),
    BILLING("Points & Billing"),
    SAFETY("Safety & Moderation"),
    TECHNICAL("App Bug / Audio");

    companion object {
        fun fromRaw(raw: String?): SupportTicketCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class SupportTicketStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved");

    companion object {
        fun fromRaw(raw: String?): SupportTicketStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val category: String = SupportTicketCategory.GENERAL.name,
    val subject: String = "",
    val details: String = "",
    val status: String = SupportTicketStatus.OPEN.name,
    val assignedStaffId: String = "",
    val assignedStaffName: String = "",
    val staffResponse: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): SupportTicketCategory = SupportTicketCategory.fromRaw(category)
    fun statusEnum(): SupportTicketStatus = SupportTicketStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class UserFollow(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followingId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun formatUsdCents(cents: Long): String {
    val safe = cents.coerceAtLeast(0L)
    val dollars = safe / 100L
    val rem = safe % 100L
    return if (rem == 0L) "$$dollars" else "$$dollars.${rem.toString().padStart(2, '0')}"
}

@Keep
@IgnoreExtraProperties
data class Reel(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorColorHex: String = "#1E6BFF",
    val authorPhotoBase64: String = "",
    val authorTier: String = StaffTier.USER.name,
    val caption: String = "",
    val thumbnailBase64: String = "",
    val durationSec: Long = 1L,
    val chunkCount: Long = 1L,
    val mimeType: String = "video/mp4",
    val boostedAtMillis: Long = 0L,
    val boostedUntilMillis: Long = 0L,
    val likedByUserIds: List<String> = emptyList(),
    val commentCount: Long = 0L,
    val shareCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun authorTierEnum(): StaffTier = StaffTier.fromRaw(authorTier)
    fun isLikedBy(uid: String): Boolean = likedByUserIds.contains(uid)
    fun isCurrentlyBoosted(nowMs: Long = System.currentTimeMillis()): Boolean =
        boostedUntilMillis > nowMs
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
    fun formattedDuration(): String {
        val safe = durationSec.coerceIn(1L, MAX_DURATION_SECONDS.toLong())
        return "0:${safe.toString().padStart(2, '0')}"
    }

    companion object {
        const val MAX_DURATION_SECONDS = 30
        const val BOOST_PRICE_POINTS = 5L
    }
}

@Keep
@IgnoreExtraProperties
data class ReelChunk(
    @DocumentId val id: String = "",
    val reelId: String = "",
    val authorId: String = "",
    val chunkIndex: Long = 0L,
    val dataBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReelComment(
    @DocumentId val id: String = "",
    val reelId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val authorColorHex: String = "#1E6BFF",
    val authorTier: String = StaffTier.USER.name,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun authorTierEnum(): StaffTier = StaffTier.fromRaw(authorTier)
}

/**
 * Glowing neon Spotlight Frame colors selectable when activating the 24-Hour Spotlight Frame (5 pts).
 */
enum class SpotlightFrameColor(
    val label: String,
    val primaryHex: String,
    val secondaryHex: String
) {
    ELECTRIC_CYAN("Electric Cyan", "#00F5FF", "#0088FF"),
    NEON_MAGENTA("Neon Magenta", "#FF2E93", "#B400FF"),
    LASER_LIME("Laser Lime", "#39FF14", "#00E5A0"),
    HYPER_VIOLET("Hyper Violet", "#B44CFF", "#6D28D9"),
    SOLAR_GOLD("Solar Gold", "#FFD60A", "#FF8C00");

    companion object {
        const val PRICE_POINTS = 5L
        const val DURATION_MS = 24L * 60L * 60L * 1000L

        fun fromHex(hex: String?): SpotlightFrameColor =
            entries.firstOrNull { it.primaryHex.equals(hex, ignoreCase = true) } ?: ELECTRIC_CYAN
    }
}

@Keep
@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val inviteeId: String = "",
    val inviteeName: String = "",
    val inviterId: String = "",
    val inviterName: String = "",
    val referralCode: String = "",
    val accountVerified: Boolean = false,
    val firstPttSent: Boolean = false,
    val bonusClaimed: Boolean = false,
    val bonusPointsEach: Int = 2,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun utcDayKey(nowMs: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = nowMs
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(java.util.Locale.US, y, m, d)
}

fun utcYesterdayKey(nowMs: Long = System.currentTimeMillis()): String =
    utcDayKey(nowMs - 86_400_000L)

fun formatPointsBalance(points: Long, halfPoints: Int = 0): String =
    if (halfPoints == 1) "${points}.5 pts" else "$points pts"

fun formatRemainingHoursLabel(untilMillis: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = (untilMillis - nowMs).coerceAtLeast(0L)
    val hours = diff / 3_600_000L
    val minutes = (diff % 3_600_000L) / 60_000L
    return when {
        hours >= 1L -> "${hours}h left"
        minutes >= 1L -> "${minutes}m left"
        else -> "<1m left"
    }
}

fun generateReferralCode(uid: String): String {
    if (uid.isBlank()) return "VOX-GUEST1"
    return try {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val chars = CharArray(6) { idx ->
            alphabet[(digest[idx].toInt() and 0xFF) % alphabet.length]
        }
        "VOX-${String(chars)}"
    } catch (_: Exception) {
        val fallback = uid.filter { it.isLetterOrDigit() }.uppercase(java.util.Locale.US).take(6).padEnd(6, 'X')
        "VOX-$fallback"
    }
}


