package app.meshpigeon.domain

/**
 * Per-conversation notification policy (07 §8): Default / Important only /
 * Muted. The conversation row wins when set; otherwise a muted channel
 * mutes its conversations and the channel's mode applies. A calm default
 * for channels: only mentions ("@name") notify; DMs always notify.
 */
object NotificationPolicy {

    fun effectiveMode(conversation: Conversation?, channel: Channel?): NotifyMode = when {
        conversation == null -> NotifyMode.DEFAULT
        conversation.notifyMode != NotifyMode.DEFAULT -> conversation.notifyMode
        channel?.muted == true -> NotifyMode.MUTED
        channel != null && channel.notifyMode != NotifyMode.DEFAULT -> channel.notifyMode
        else -> NotifyMode.DEFAULT
    }

    /** DEFAULT: DMs notify, channels only when the message mentions me. */
    fun shouldNotify(mode: NotifyMode, isDirect: Boolean, body: String, myName: String?): Boolean =
        when (mode) {
            NotifyMode.MUTED -> false
            NotifyMode.IMPORTANT_ONLY -> true
            NotifyMode.DEFAULT -> isDirect || (myName != null && body.contains("@$myName", ignoreCase = true))
        }
}
