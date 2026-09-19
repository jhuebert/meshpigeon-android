package app.meshpigeon.domain

/**
 * Bundled region table (07 §12): a "region" is a named bundle of RF settings
 * — nothing more (03 §8). Custom presets stay behind Advanced and are
 * importable via QR/link (M5).
 */
object RadioPresets {
    val REGIONS = listOf(
        RegionPreset(0, "UNSET", 869_525_000, 125.0, 9, 5, 14),
        RegionPreset(1, "US-915", 906_875_000, 250.0, 9, 5, 22),
        RegionPreset(2, "EU-868", 869_525_000, 250.0, 9, 5, 14),
        RegionPreset(3, "EU-868-narrow", 869_525_000, 125.0, 9, 5, 14),
        RegionPreset(4, "Oceania-915", 917_000_000, 250.0, 9, 5, 22),
        RegionPreset(5, "CN-470", 470_100_000, 125.0, 9, 5, 19),
        RegionPreset(6, "IN-865", 865_062_500, 125.0, 9, 5, 20),
        RegionPreset(7, "JP-920", 920_600_000, 250.0, 9, 5, 16),
        RegionPreset(8, "KR-920", 921_200_000, 250.0, 9, 5, 14),
        RegionPreset(9, "TW-920", 923_200_000, 250.0, 9, 5, 20),
        RegionPreset(10, "RU-864", 864_100_000, 125.0, 9, 5, 20),
        RegionPreset(11, "UA-868", 869_100_000, 125.0, 9, 5, 14),
        RegionPreset(12, "TH-920", 921_000_000, 125.0, 9, 5, 14),
        RegionPreset(13, "SA-865", 868_000_000, 125.0, 9, 5, 14),
        RegionPreset(14, "IL-915", 915_000_000, 250.0, 9, 5, 20),
        RegionPreset(15, "AU-915", 916_500_000, 250.0, 9, 5, 22),
    )

    fun byId(id: Int): RegionPreset = REGIONS.firstOrNull { it.id == id } ?: REGIONS[0]

    fun toSettings(preset: RegionPreset, epoch: Long = 0): app.meshpigeon.transport.RadioSession.RadioSettings =
        app.meshpigeon.transport.RadioSession.RadioSettings(
            version = 1,
            region = preset.id,
            freqHz = preset.freqHz,
            bandwidthX100Khz = (preset.bandwidthKhz * 100).toInt(),
            spreadingFactor = preset.spreadingFactor,
            codingRate = preset.codingRate,
            powerDbm = preset.txPowerDbm,
            configEpoch = epoch,
        )
}
