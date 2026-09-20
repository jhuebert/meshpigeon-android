package app.meshpigeon.domain

import app.meshpigeon.protocol.DeliveryState

import app.meshpigeon.protocol.AdvertAppData

/**
 * Domain entities (06-android-app §2/§3). Identities are first-class and
 * every per-identity table is namespaced by `identityId` — switching radios
 * never forces identity churn and vice versa.
 */
data class Identity(
    val id: Long,
    val name: String,
    val publicKey: ByteArray,
    /** Encrypted with the Android keystore; never leaves the device unsealed. */
    val privateKeyEnc: ByteArray,
    val flags: Int,
    val createdAt: Long,
    val isActive: Boolean,
    val advertPolicy: AdvertPolicy,
    val lastAdvertAt: Long? = null,
)

enum class AdvertPolicy { MANUAL, NEARBY_DISCOVERABLE }

data class Contact(
    val id: Long,
    val identityId: Long,
    val publicKey: ByteArray,
    val name: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long? = null,
    val source: ContactSource = ContactSource.ADVERT,
    val blockedAt: Long? = null,
    val flags: Int = 0,
    val note: String? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val isRepeater: Boolean = false,
) {
    val isBlocked: Boolean get() = blockedAt != null
}

enum class ContactSource { ADVERT, QR, LINK, CLIPBOARD, MANUAL }

data class Channel(
    val id: Long,
    val identityId: Long,
    val name: String,
    /** 16-byte channel key, sealed at rest. */
    val keyEnc: ByteArray,
    val kind: ChannelKind,
    val createdAt: Long,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val notifyMode: NotifyMode = NotifyMode.DEFAULT,
)

enum class ChannelKind { PUBLIC, HASHTAG, PRIVATE }
enum class NotifyMode { DEFAULT, IMPORTANT_ONLY, MUTED }

data class Conversation(
    val id: Long,
    val identityId: Long,
    val kind: ConversationKind,
    /** For DMs: the contact's publicKey. For channels: the channel id. */
    val refId: Long?,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val notifyMode: NotifyMode = NotifyMode.DEFAULT,
    val lastMessageAt: Long? = null,
    val markUnreadFromId: Long? = null,
)

enum class ConversationKind { DM, GROUP, PUBLIC, TRACE_LOG }

data class Message(
    val id: Long,
    val conversationId: Long,
    val identityId: Long,
    val senderContactId: Long? = null,
    val senderName: String? = null,
    val body: String,
    val kind: MessageKind = MessageKind.TEXT,
    val sentAt: Long, // wall clock (computed via ClockMapper for received)
    val out: Boolean,
    val state: DeliveryState? = null, // outgoing only
    val snr: Float? = null,
    val rssi: Int? = null,
    val hops: Int? = null,
    val region: String? = null,
    val packetTag: ByteArray? = null,
    val replyToId: Long? = null,
    val ackKey: ByteArray? = null,
    val rttMs: Long? = null,
)

enum class MessageKind { TEXT, CONTACT_CARD, LOCATION, CHANNEL_SHARE, REACTION }

data class OutboxEntry(
    val id: Long,
    val conversationId: Long,
    val messageId: Long,
    val packet: ByteArray,
    val ackKey: ByteArray?,
    val attempts: Int,
    val nextRetryAt: Long,
    val state: DeliveryState,
    val airtimeMs: Double,
    val hops: Int,
    val direct: Boolean,
)

data class RadioTarget(
    val id: Long,
    val persistentId: String,
    val name: String,
    val linkKind: RadioLinkKind,
    val linkAddr: String,
    val prefOrder: Int,
    val preferred: Boolean,
    val desiredSettingsJson: String? = null,
    val lastConnectedAt: Long? = null,
)

enum class RadioLinkKind { BLE, USB, WIFI }

/** A known radio preset ("region" = radio settings bundle, 03 §8). */
data class RegionPreset(
    val id: Int,
    val name: String,
    val freqHz: Long,
    val bandwidthKhz: Double,
    val spreadingFactor: Int,
    val codingRate: Int,
    val txPowerDbm: Int,
)
