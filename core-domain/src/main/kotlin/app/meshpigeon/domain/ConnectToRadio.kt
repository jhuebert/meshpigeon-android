package app.meshpigeon.domain

import app.meshpigeon.transport.RadioKey
import app.meshpigeon.transport.RadioLink
import app.meshpigeon.transport.RadioSession
import app.meshpigeon.transport.RadioTarget as TransportRadioTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Radio selection (05 §2): the user's ordered radio list lives per app
 * profile (shared across identities). Auto mode connects to the first
 * available radio in preference order and falls back silently; a one-time
 * override connects for the session only.
 */
class ConnectToRadio(
    private val targets: RadioTargetRepository,
) {
    data class Selection(
        val target: TransportRadioTarget,
        val wasPreferred: Boolean,
        val fallbackUsed: Boolean,
    )

    /** Ordered preference list (domain entities, persisted in :core-data). */
    suspend fun preferences(): List<RadioTarget> = targets.observeAll().first()

    /**
     * Pick the radio to connect to: first available in preference order,
     * unless [override] names a specific radio for this session only.
     */
    suspend fun select(
        available: List<RadioKey>,
        override: RadioKey? = null,
    ): Selection? {
        if (override != null) {
            return Selection(toLinkTarget(override), wasPreferred = false, fallbackUsed = false)
        }
        for (p in preferences()) {
            val match = available.firstOrNull { it.persistentId == p.persistentId }
            if (match != null) {
                return Selection(toLinkTarget(match), p.preferred, fallbackUsed = false)
            }
        }
        // silent fallback: any available radio not in the list
        val fallback = available.firstOrNull() ?: return null
        return Selection(toLinkTarget(fallback), wasPreferred = false, fallbackUsed = true)
    }

    private fun toLinkTarget(key: RadioKey): TransportRadioTarget = when (key) {
        is TransportRadioTarget -> key
        is RadioTarget -> TransportRadioTarget(
            persistentId = key.persistentId,
            name = key.name,
            link = key.linkKind.toLink(key.linkAddr),
        )
        else -> TransportRadioTarget(
            persistentId = key.persistentId,
            name = key.name,
            link = key.link,
        )
    }

    private fun RadioLinkKind.toLink(addr: String): RadioLink = when (this) {
        RadioLinkKind.BLE -> RadioLink.Ble(addr)
        RadioLinkKind.USB -> RadioLink.Usb(0, 0, addr)
        RadioLinkKind.WIFI -> RadioLink.Wifi(addr, 8765)
    }
}

/**
 * Settings-epoch banner state (05 §3): when the radio's tuning differs from
 * the user's desired settings for that radio, surface a non-blocking
 * choice instead of silently retuning.
 */
class RadioSettingsGuard {
    data class Divergence(
        val radioSettings: RadioSession.RadioSettings,
        val desiredSettings: RadioSession.RadioSettings,
    ) {
        fun differs(): Boolean =
            radioSettings.copy(configEpoch = 0) != desiredSettings.copy(configEpoch = 0)
    }

    private val divergence = MutableStateFlow<Divergence?>(null)
    val current: StateFlow<Divergence?> = divergence

    fun onRadioSettings(radio: RadioSession.RadioSettings, desired: RadioSession.RadioSettings?) {
        divergence.value = if (desired != null && Divergence(radio, desired).differs()) {
            Divergence(radio, desired)
        } else {
            null
        }
    }

    fun resolve() {
        divergence.value = null
    }
}
